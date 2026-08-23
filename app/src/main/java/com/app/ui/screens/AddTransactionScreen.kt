package com.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.data.Categories
import com.app.data.FinanceCategory
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.IconMapper
import java.util.Calendar
import kotlinx.coroutines.launch

data class SmartCategorySuggestion(
    val category: FinanceCategory,
    val score: Double,
    val reason: String
)



@Composable
fun AddTransactionScreen(
    viewModel: FinanceViewModel,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var rawExpression by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME
    var selectedWalletId by remember { mutableStateOf<Int?>(null) }
    var selectedCategoryName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    


    val wallets by viewModel.dailyWallets.collectAsState()
    val categoriesList by viewModel.categoriesList.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()

    // Transfer Setup
    var isTransfer by remember { mutableStateOf(false) }
    var transferWalletId by remember { mutableStateOf<Int?>(null) }

    val events by viewModel.allEvents.collectAsState()
    var isEventTransaction by remember { mutableStateOf(false) }
    var selectedEventId by remember { mutableStateOf<Int?>(null) }

    // Date Picker Setup
    val calendar = remember { Calendar.getInstance() }
    var selectedTimestamp by remember { mutableStateOf(calendar.timeInMillis) }
    var dateLabel by remember { mutableStateOf("Hôm nay") }

    LaunchedEffect(events, selectedTimestamp) {
        val activeEvents = events.filter {
            it.isActive && FormatHelper.isEventOngoing(it.startDate, it.endDate, selectedTimestamp)
        }
        if (activeEvents.isNotEmpty() && selectedEventId == null && !isEventTransaction) {
            val nearestStart = activeEvents.maxByOrNull { it.startDate }
            selectedEventId = nearestStart?.id
            isEventTransaction = true
        } else if (selectedEventId != null) {
            val isStillValid = activeEvents.any { it.id == selectedEventId }
            if (!isStillValid) {
                val nearestStart = activeEvents.maxByOrNull { it.startDate }
                selectedEventId = nearestStart?.id
                if (selectedEventId == null) {
                    isEventTransaction = false
                }
            }
        }
    }

    // Smart Select State Management
    var hasManuallySelected by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }

    LaunchedEffect(selectedType) {
        hasManuallySelected = false
    }

    // Auto-select CASH wallet if available, otherwise first wallet
    LaunchedEffect(wallets) {
        if (selectedWalletId == null && wallets.isNotEmpty()) {
            val cashWallet = wallets.find { it.type == "CASH" }
            selectedWalletId = cashWallet?.id ?: wallets.first().id
        }
    }

    // Auto-select transfer target/source wallet if isTransfer is enabled
    LaunchedEffect(isTransfer, selectedWalletId) {
        if (isTransfer) {
            if (transferWalletId == null || transferWalletId == selectedWalletId) {
                transferWalletId = wallets.firstOrNull { it.id != selectedWalletId }?.id
            }
        }
    }

    // Filter categories depending on type. Only leaf categories (no children) are shown.
    val filteredCategories = remember(categoriesList, selectedType) {
        val typeFiltered = categoriesList.filter { it.type == selectedType || it.type == "BOTH" }
        val parentNames = categoriesList.mapNotNull { it.parentName }.toSet()
        typeFiltered.filter { it.name !in parentNames }
    }

    val currentAmount = remember(rawExpression) {
        FormatHelper.evaluateExpression(rawExpression)
    }

    // Compute intelligent category suggestions in real-time based on Amount, Time, Wallet, and Habits
    val smartSuggestions = remember(
        currentAmount,
        selectedType,
        selectedWalletId,
        selectedTimestamp,
        allTransactions,
        filteredCategories
    ) {
        if (filteredCategories.isEmpty()) return@remember emptyList<SmartCategorySuggestion>()

        val typeTxs = allTransactions.filter { it.type == selectedType }
        val calCurrent = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val currHour = calCurrent.get(Calendar.HOUR_OF_DAY)

        // 1. Group past transactions by exact and range amounts
        val exactAmountMatches = if (currentAmount > 0.0) {
            typeTxs.filter { it.amount == currentAmount }
        } else {
            emptyList()
        }

        val rangeAmountMatches = if (currentAmount > 0.0) {
            typeTxs.filter {
                val maxAmt = Math.max(it.amount, currentAmount)
                maxAmt > 0 && Math.abs(it.amount - currentAmount) / maxAmt <= 0.25
            }
        } else {
            emptyList()
        }

        // 2. Filter transactions in the nearby time window (±2 hours)
        val timeWindowTxs = typeTxs.filter { tx ->
            val calTx = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val txHour = calTx.get(Calendar.HOUR_OF_DAY)
            val diff = Math.abs(currHour - txHour)
            diff <= 2 || diff >= 22
        }

        val frequencyMap = typeTxs.groupBy { it.categoryName }.mapValues { it.value.size }
        val latestTxCategory = typeTxs.firstOrNull()?.categoryName

        // Calculate probability score for each category
        val scoredList = filteredCategories.map { cat ->
            val cName = cat.name
            var categoryScore = 0.0
            val reasonsList = mutableListOf<String>()

            // A. Amount Pattern Matching (Up to 45 pts)
            if (currentAmount > 0.0) {
                if (exactAmountMatches.isNotEmpty()) {
                    val exactCatCount = exactAmountMatches.count { it.categoryName == cName }
                    if (exactCatCount > 0) {
                        val exactRatio = exactCatCount.toDouble() / exactAmountMatches.size
                        val amountScore = exactRatio * 40.0 + (if (exactCatCount >= 2) 5.0 else 0.0)
                        categoryScore += amountScore
                        if (exactRatio >= 0.5) {
                            reasonsList.add("Thường chi mức này")
                        } else {
                            reasonsList.add("Có chi mức này")
                        }
                    }
                } else if (rangeAmountMatches.isNotEmpty()) {
                    val rangeCatCount = rangeAmountMatches.count { it.categoryName == cName }
                    if (rangeCatCount > 0) {
                        val rangeRatio = rangeCatCount.toDouble() / rangeAmountMatches.size
                        categoryScore += rangeRatio * 30.0
                        reasonsList.add("Số tiền tương tự")
                    }
                }
            }

            // B. Time-of-Day Pattern Matching (Up to 35 pts)
            if (timeWindowTxs.isNotEmpty()) {
                val timeCatCount = timeWindowTxs.count { it.categoryName == cName }
                if (timeCatCount > 0) {
                    val timeRatio = timeCatCount.toDouble() / timeWindowTxs.size
                    val timeScore = timeRatio * 35.0
                    categoryScore += timeScore
                    if (timeRatio >= 0.35) {
                        reasonsList.add("Thói quen lúc ${currHour}h")
                    } else if (reasonsList.isEmpty()) {
                        reasonsList.add("Khung giờ này")
                    }
                }
            } else {
                // Default natural time-of-day priors if history in this window is sparse
                val isMorning = currHour in 6..9
                val isNoon = currHour in 11..13
                val isEvening = currHour in 17..20
                val isNight = currHour in 21..23
                if ((isMorning || isNoon || isEvening) && (cName == "Ăn uống" || cName == "Giải khát" || cName == "Xăng xe" || cName == "Di chuyển")) {
                    categoryScore += 18.0
                    reasonsList.add("Khung giờ quen thuộc")
                } else if (isNight && (cName == "Giải trí" || cName == "Ăn uống")) {
                    categoryScore += 15.0
                    reasonsList.add("Khung giờ tối")
                }
            }

            // C. Wallet Context (Up to 10 pts)
            if (selectedWalletId != null && typeTxs.isNotEmpty()) {
                val walletTxs = typeTxs.filter { it.walletId == selectedWalletId }
                if (walletTxs.isNotEmpty()) {
                    val walletCatCount = walletTxs.count { it.categoryName == cName }
                    if (walletCatCount > 0) {
                        val walletRatio = walletCatCount.toDouble() / walletTxs.size
                        categoryScore += walletRatio * 10.0
                    }
                }
            }

            // D. Usage Frequency & Recency (Up to 10 pts)
            val occurrences = frequencyMap.getOrDefault(cName, 0)
            if (occurrences > 0 && typeTxs.isNotEmpty()) {
                val freqRatio = occurrences.toDouble() / typeTxs.size
                categoryScore += freqRatio * 8.0
            }
            if (cName == latestTxCategory) {
                categoryScore += 5.0
            }

            val finalScore = categoryScore.coerceIn(0.0, 100.0)
            val displayReason = when {
                reasonsList.isNotEmpty() -> reasonsList.first()
                occurrences > 0 -> "Danh mục quen thuộc"
                else -> "Gợi ý phù hợp"
            }

            SmartCategorySuggestion(
                category = cat,
                score = finalScore,
                reason = displayReason
            )
        }

        // Normalize top scores to realistic percentages
        val maxScore = scoredList.maxOfOrNull { it.score } ?: 1.0
        val topList = scoredList
            .filter { it.score > 5.0 }
            .sortedByDescending { it.score }
            .take(3)

        if (topList.isNotEmpty() && maxScore > 0.0) {
            topList.map { item ->
                val normalizedPct = if (maxScore > 50.0) {
                    item.score
                } else {
                    (item.score / maxScore) * 50.0 + 15.0
                }
                item.copy(score = Math.min(99.0, Math.max(10.0, normalizedPct)))
            }
        } else {
            emptyList()
        }
    }

    // High confidence trigger for auto-selecting category
    LaunchedEffect(smartSuggestions) {
        if (!hasManuallySelected && smartSuggestions.isNotEmpty()) {
            val topSuggest = smartSuggestions.first()
            if (topSuggest.score >= 50.0) {
                if (selectedCategoryName != topSuggest.category.name) {
                    selectedCategoryName = topSuggest.category.name
                }
            }
        }
    }

    val categoryUsageCounts = remember(allTransactions) {
        val counts = mutableMapOf<String, Int>()
        allTransactions.forEach { tx ->
            counts[tx.categoryName] = counts.getOrDefault(tx.categoryName, 0) + 1
        }
        counts
    }

    val displayCategories = remember(filteredCategories, smartSuggestions, categoryUsageCounts) {
        val suggestionScoreMap = smartSuggestions.associate { it.category.name to it.score }
        filteredCategories.sortedWith(
            compareByDescending<FinanceCategory> { suggestionScoreMap[it.name] ?: 0.0 }
                .thenByDescending { categoryUsageCounts[it.name] ?: 0 }
        )
    }

    LaunchedEffect(displayCategories, selectedType) {
        if (displayCategories.isNotEmpty()) {
            if (selectedCategoryName.isBlank() || filteredCategories.none { it.name == selectedCategoryName }) {
                selectedCategoryName = displayCategories.first().name
            }
        }
    }

    val dateTimeFormatter = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()) }

    val showDateTimePicker = {
        val currentCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val timePicker = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val finalCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedTimestamp = finalCal.timeInMillis
                dateLabel = dateTimeFormatter.format(finalCal.timeInMillis)
            },
            currentCal.get(Calendar.HOUR_OF_DAY),
            currentCal.get(Calendar.MINUTE),
            true // 24-hour style
        )

        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val partialCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedTimestamp = partialCal.timeInMillis
                timePicker.show()
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }


        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {


        // 1. Loại tiền (Switch EXPENSE vs INCOME)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { selectedType = "EXPENSE" },
                modifier = Modifier.weight(1f).testTag("select_expense_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == "EXPENSE") Color(0xFFF44336)
                                     else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedType == "EXPENSE") Color.White
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Chi", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Khoản Chi")
            }

            Button(
                onClick = { selectedType = "INCOME" },
                modifier = Modifier.weight(1f).testTag("select_income_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == "INCOME") Color(0xFF4CAF50)
                                     else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedType == "INCOME") Color.White
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Thu", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Khoản Thu")
            }
        }

        // 7. Số tiền
        com.app.ui.components.CustomMoneyInputField(
            value = rawExpression,
            onValueChange = { rawExpression = it },
            label = "Số tiền phát sinh",
            autoFocus = false,
            onDismissKeyboard = {},
            testTag = "tx_amount_text_field"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Layout 2 options side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Feature 1: Transfer
                Card(
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTransfer) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                                         else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isTransfer) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { 
                                isTransfer = !isTransfer 
                                if (isTransfer) isEventTransaction = false
                            }
                            .padding(start = 12.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Transfer",
                                tint = if (isTransfer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nội bộ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = isTransfer,
                            onCheckedChange = { 
                                isTransfer = it 
                                if (it) isEventTransaction = false
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.scale(0.7f).testTag("transfer_quick_switch")
                        )
                    }
                }
                
                // Feature 2: Event
                Card(
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEventTransaction) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                                         else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isEventTransaction) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { 
                                isEventTransaction = !isEventTransaction 
                                if (isEventTransaction) isTransfer = false
                            }
                            .padding(start = 12.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Event",
                                tint = if (isEventTransaction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sự kiện",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = isEventTransaction,
                            onCheckedChange = { 
                                isEventTransaction = it 
                                if (it) isTransfer = false
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
            }
            
            // Thêm tính năng chọn Event (Chỉ hiển thị các sự kiện đang active và đang diễn ra tại mốc thời gian giao dịch)
            val activeEventsForSelection = events.filter { 
                it.isActive && FormatHelper.isEventOngoing(it.startDate, it.endDate, selectedTimestamp)
            }.sortedByDescending { it.startDate }

            val isOptionExpanded = isEventTransaction || isTransfer
            if (isOptionExpanded) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (isEventTransaction) {
                                var showQuickCreate by remember { mutableStateOf(false) }
        
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    items(activeEventsForSelection) { event ->
                                            val isSelected = event.id == selectedEventId
                                            val eventColor = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(event.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) eventColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                                                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) eventColor else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                    .clickable { selectedEventId = event.id }
                                            ) {
                                                Text(
                                                    event.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) eventColor else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                )
                                                
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier.matchParentSize()
                                                    ) {
                                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                                            val sizePx = 24.dp.toPx()
                                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                                moveTo(size.width, 0f)
                                                                lineTo(size.width, sizePx)
                                                                lineTo(size.width - sizePx, 0f)
                                                                close()
                                                            }
                                                            drawPath(path, color = eventColor)
                                                        }
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = (-2).dp, y = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                    .clickable { showQuickCreate = true }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Tạo mới", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
        
                                    if (showQuickCreate) {
                                        var newEventName by remember { mutableStateOf("") }
                                        var newEventDesc by remember { mutableStateOf("") }
                                        AlertDialog(
                                            onDismissRequest = { showQuickCreate = false },
                                            title = { Text("Tạo sự kiện mới", fontWeight = FontWeight.Bold) },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(
                                                        value = newEventName,
                                                        onValueChange = { newEventName = it },
                                                        label = { Text("Tên sự kiện (*)") },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        if (newEventName.isNotBlank()) {
                                                            viewModel.addEvent(
                                                                name = newEventName,
                                                                description = newEventDesc,
                                                                startDate = System.currentTimeMillis(),
                                                                endDate = null,
                                                                limitAmount = null
                                                            )
                                                            viewModel.showSuccessNotification("Đã tạo $newEventName")
                                                            showQuickCreate = false
                                                        } else {
                                                            viewModel.showWarningNotification("Vui lòng nhập tên")
                                                        }
                                                    }
                                                ) { Text("Lưu") }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showQuickCreate = false }) { Text("Hủy") }
                                            }
                                        )
                                    }
                            } else if (isTransfer) {
                                val transferLabel = if (selectedType == "EXPENSE") "Ví chuyển đi (Nguồn)" else "Ví nhận về (Đích)"
                                Text(
                                    text = transferLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                
                                if (wallets.isEmpty()) {
                                    Text(
                                        text = "Không tìm thấy ví khả dụng.",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp
                                    )
                                } else {
                                    // Filter out the transfer target/destination wallet to avoid transfer to self!
                                    val availableTransferWallets = wallets.filter { it.id != transferWalletId }
                                    if (availableTransferWallets.isEmpty()) {
                                        Text(
                                            text = "Vui lòng tạo thêm ví khác để chuyển tiền.",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        val chunkedTransferWallets = availableTransferWallets.chunked(2)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            chunkedTransferWallets.forEach { rowWallets ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    rowWallets.forEach { wt ->
                                                        val isSelected = selectedWalletId == wt.id
                                                        val accentColor = FormatHelper.parseColor(wt.colorHex)
                                                        val cardColor = if (isSelected) accentColor.copy(alpha = 0.15f)
                                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                        val borderColor = if (isSelected) accentColor
                                                                          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                                        
                                                        Card(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .clickable { selectedWalletId = wt.id }
                                                                .testTag("tx_transfer_wallet_chip_${wt.id}"),
                                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                                            border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderColor)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(24.dp)
                                                                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = IconMapper.getIconByName(wt.iconName),
                                                                        contentDescription = wt.name,
                                                                        tint = accentColor,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = wt.name,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onSurface,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    Text(
                                                                        text = FormatHelper.formatVND(wt.balance),
                                                                        fontSize = 9.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                                if (isSelected) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.CheckCircle,
                                                                        contentDescription = "Selected",
                                                                        tint = accentColor,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (rowWallets.size < 2) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val surfaceColor = MaterialTheme.colorScheme.surface
                    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    val isLeft = isTransfer
                    
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(9.dp)) {
                        val triangleW = 16.dp.toPx()
                        val triangleH = 8.dp.toPx()
                        val cardY = 8.dp.toPx()
                        
                        val centerOffset = if (isLeft) (size.width / 2f - 4.dp.toPx()) / 2f 
                                            else size.width - (size.width / 2f - 4.dp.toPx()) / 2f
                                            
                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(centerOffset, 0f)
                            lineTo(centerOffset + triangleW / 2f, cardY + 2.5f) 
                            lineTo(centerOffset - triangleW / 2f, cardY + 2.5f)
                            close()
                        }
                        drawPath(fillPath, color = surfaceColor)
                        
                        drawLine(
                            color = borderColor,
                            start = androidx.compose.ui.geometry.Offset(centerOffset, 0f),
                            end = androidx.compose.ui.geometry.Offset(centerOffset + triangleW / 2f, cardY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            color = borderColor,
                            start = androidx.compose.ui.geometry.Offset(centerOffset, 0f),
                            end = androidx.compose.ui.geometry.Offset(centerOffset - triangleW / 2f, cardY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }

            // 2. Tài khoản thanh toán (bố cục 2x2 đẹp mắt)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isTransfer) {
                    if (selectedType == "EXPENSE") "Ví nhận tiền (Đích)" else "Ví rút tiền (Nguồn)"
                } else "Tài khoản thanh toán",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (wallets.isEmpty()) {
                Text(
                    text = "Không tìm thấy ví khả dụng. Chọn mục Tài khoản để tạo mới.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            } else {
                val bottomWallets = if (isTransfer) wallets.filter { it.id != selectedWalletId } else wallets
                val chunkedWallets = bottomWallets.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunkedWallets.forEach { rowWallets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowWallets.forEach { wt ->
                                val isSelected = if (isTransfer) transferWalletId == wt.id else selectedWalletId == wt.id
                                val accentColor = FormatHelper.parseColor(wt.colorHex)
                                val cardColor = if (isSelected) accentColor.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                val borderColor = if (isSelected) accentColor
                                                  else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { 
                                            if (isTransfer) {
                                                transferWalletId = wt.id
                                            } else {
                                                selectedWalletId = wt.id
                                            }
                                        }
                                        .testTag("tx_wallet_chip_${wt.id}"),
                                    colors = CardDefaults.cardColors(containerColor = cardColor),
                                    border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderColor)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(accentColor.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.getIconByName(wt.iconName),
                                                contentDescription = wt.name,
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = wt.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = FormatHelper.formatVND(wt.balance),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowWallets.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        } // Closed wrapping Column

        if (isTransfer) {
            // Thông báo chuyển tiền
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Transfer info",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Giao dịch Chuyển khoản liên ví không cần chọn hạng mục. Hệ thống sẽ tự động hạch toán đối ứng Thu & Chi để dòng tiền luôn chính xác.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        } else {
            // 3. Hạng mục dạng lưới (các mục bé hơn, 4 cột)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Hạng mục giao dịch",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Dynamic horizontal view containing top 3 smart suggestions
            if (allTransactions.isNotEmpty() && smartSuggestions.isNotEmpty()) {
                AnimatedVisibility(
                    visible = smartSuggestions.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "Gợi ý thông minh",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Gợi ý nhanh (tối đa 3):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            smartSuggestions.forEach { suggestion ->
                                val cat = suggestion.category
                                val isSelected = selectedCategoryName == cat.name
                                val isAutoSelected = !hasManuallySelected && suggestion.score >= 65.0 && isSelected
                                val accentColor = try { FormatHelper.parseColor(cat.colorHex) } catch (e: Exception) { Color.Gray }

                                val chipBg = if (isSelected) {
                                    accentColor.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }

                                val chipBorder = if (isAutoSelected) {
                                    BorderStroke(1.2.dp, if (selectedType == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50))
                                } else if (isSelected) {
                                    BorderStroke(1.2.dp, accentColor)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedCategoryName = cat.name
                                            hasManuallySelected = true
                                        }
                                        .testTag("smart_suggest_${cat.name}"),
                                    color = chipBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = chipBorder
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.getIconByName(cat.iconName),
                                            contentDescription = cat.name,
                                            tint = accentColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = cat.name,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isAutoSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (selectedType == "EXPENSE") Color(0xFFF44336).copy(alpha = 0.12f)
                                                        else Color(0xFF4CAF50).copy(alpha = 0.12f),
                                                        RoundedCornerShape(3.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Tự động chọn",
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selectedType == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "${suggestion.score.toInt()}%",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val chunkedCategories = displayCategories.chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunkedCategories.forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCats.forEach { cat ->
                            val isSelected = selectedCategoryName == cat.name
                            val categoryColor = try { FormatHelper.parseColor(cat.colorHex) } catch(e: Exception) { Color.Gray }
                            
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedCategoryName = cat.name
                                            hasManuallySelected = true
                                        }
                                ) {
                                    val borderColor = if (isSelected) Color(0xFFF44336) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    val bgColor = MaterialTheme.colorScheme.surface
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(bgColor)
                                            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                                            .padding(start = 6.dp, end = 2.dp)
                                            .testTag("category_select_${cat.name}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(categoryColor.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.getIconByName(cat.iconName),
                                                contentDescription = cat.name,
                                                tint = categoryColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = cat.name,
                                            fontSize = 10.sp, 
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 2.dp, y = 2.dp)
                                                .size(14.dp)
                                                .background(Color(0xFFF44336), CircleShape)
                                                .border(1.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(9.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (rowCats.size < 4) {
                            for (i in 0 until (4 - rowCats.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }


                }
            }
        }

        // --- activeEventsForSelection is defined above ---
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

        // 4 & 6. Ghi chú hóa đơn/mô tả
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Ghi chú / Mô tả giao dịch") },
            leadingIcon = { Icon(imageVector = Icons.Default.EditNote, contentDescription = "Note") },
            modifier = Modifier.fillMaxWidth().testTag("tx_note_input"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // 5. Thời gian (Có chức năng lấy thời gian nhanh, format HH:mm dd/MM/yyyy)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Thời gian phát sinh",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dateTimeFormatter.format(selectedTimestamp),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ngày & Giờ (HH:mm dd/MM/yyyy)") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Schedule, contentDescription = "Time") },
                    trailingIcon = {
                        IconButton(
                            onClick = { showDateTimePicker() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Chọn ngày giờ",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDateTimePicker() }
                )
            }
        }

        var isSubmitting by remember { mutableStateOf(false) }

        val isFormValid = if (isTransfer) {
            FormatHelper.evaluateExpression(rawExpression) > 0.0 && selectedWalletId != null && transferWalletId != null && transferWalletId != selectedWalletId
        } else {
            FormatHelper.evaluateExpression(rawExpression) > 0.0 && selectedWalletId != null && selectedCategoryName.isNotBlank()
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Lưu
        Button(
            onClick = {
                if (isSubmitting) return@Button
                focusManager.clearFocus()
                keyboardController?.hide()
                
                val amount = FormatHelper.evaluateExpression(rawExpression)
                val walletId = selectedWalletId
                if (amount > 0 && walletId != null) {
                    if (isTransfer) {
                        val targetId = transferWalletId
                        if (targetId != null && targetId != walletId) {
                            isSubmitting = true
                            val finalSourceId = if (selectedType == "EXPENSE") walletId else targetId
                            val finalDestId = if (selectedType == "EXPENSE") targetId else walletId
                            
                            viewModel.addTransaction(
                                walletId = finalSourceId,
                                type = "TRANSFER",
                                amount = amount,
                                categoryName = "Chuyển khoản",
                                note = note.ifBlank { "Chuyển khoản nội bộ" },
                                timestamp = selectedTimestamp,
                                isRecurring = false,
                                recurrencePeriod = "NONE",
                                destinationWalletId = finalDestId
                            )
                            viewModel.showSuccessNotification("Thực hiện chuyển khoản liên ví thành công!")
                            
                            // Reset state fields & stay on screen
                            rawExpression = ""
                            note = ""
                            selectedCategoryName = ""
                            isTransfer = false
                            transferWalletId = null
                            isSubmitting = false
                            scope.launch { scrollState.animateScrollTo(0) }
                        }
                    } else {
                        // Normal manual transaction
                        if (selectedCategoryName.isNotBlank()) {
                            isSubmitting = true
                            viewModel.addTransaction(
                                walletId = walletId,
                                type = selectedType,
                                amount = amount,
                                categoryName = selectedCategoryName,
                                note = note,
                                timestamp = selectedTimestamp,
                                isRecurring = false,
                                recurrencePeriod = "NONE",
                                eventId = if (isEventTransaction) selectedEventId else null
                            )
                            viewModel.showSuccessNotification("Thêm giao dịch mới thành công!")
                            
                            onSuccess()
                        }
                    }
                }
            },
            enabled = isFormValid && !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_transaction_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedType == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50),
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, disabledElevation = 0.dp)
        ) {
            Text(
                text = "LƯU GIAO DỊCH",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.ime))
    }
}


