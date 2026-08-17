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

    LaunchedEffect(events) {
        val now = System.currentTimeMillis()
        val activeEvents = events.filter {
            it.isActive && FormatHelper.isEventOngoing(it.startDate, it.endDate, now)
        }
        if (activeEvents.isNotEmpty() && selectedEventId == null && !isEventTransaction) {
            val nearestStart = activeEvents.maxByOrNull { it.startDate }
            selectedEventId = nearestStart?.id
            isEventTransaction = true
        }
    }

    // Date Picker Setup
    val calendar = remember { Calendar.getInstance() }
    var selectedTimestamp by remember { mutableStateOf(calendar.timeInMillis) }
    var dateLabel by remember { mutableStateOf("Hôm nay") }

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

    // Compute intelligent category suggestions in real-time
    val smartSuggestions = remember(
        currentAmount,
        note,
        selectedType,
        selectedWalletId,
        selectedTimestamp,
        allTransactions,
        filteredCategories
    ) {
        if (currentAmount <= 0.0) return@remember emptyList<SmartCategorySuggestion>()
        if (allTransactions.isEmpty() || filteredCategories.isEmpty()) return@remember emptyList<SmartCategorySuggestion>()

        val typeTxs = allTransactions.filter { it.type == selectedType }
        if (typeTxs.isEmpty()) return@remember emptyList<SmartCategorySuggestion>()

        val latestTxCategory = typeTxs.maxByOrNull { it.timestamp }?.categoryName
        val frequencyMap = typeTxs.groupBy { it.categoryName }.mapValues { it.value.size }

        // Group similar amounts of the same type
        val exactAmountMatches = if (currentAmount > 0.0) {
            typeTxs.filter { it.amount == currentAmount }
        } else {
            emptyList()
        }

        filteredCategories.mapNotNull { cat ->
            val cName = cat.name
            var categoryScore = 0.0
            val reasonsList = mutableListOf<String>()

            // 1. Base Frequency Usage bias (Max 15)
            val occurrences = frequencyMap.getOrDefault(cName, 0)
            if (occurrences > 0) {
                categoryScore += Math.min(15.0, occurrences * 1.5)
            }

            // 2. Most recent transaction bias (10 pts)
            if (cName == latestTxCategory) {
                categoryScore += 10.0
            }

            // 3. Amount consistency bonus (from previous similar amounts)
            if (exactAmountMatches.isNotEmpty()) {
                val amountMatchesForCat = exactAmountMatches.filter { it.categoryName == cName }.size
                val amountRatio = amountMatchesForCat.toDouble() / exactAmountMatches.size
                if (amountMatchesForCat >= 1) {
                    if (amountRatio >= 0.7 && exactAmountMatches.size >= 2) {
                        categoryScore += 45.0
                        reasonsList.add("Thường chi mức này")
                    } else {
                        categoryScore += 15.0
                        reasonsList.add("Có chi mức này")
                    }
                }
            }

            // 4. Historical comparison logic
            val txsToCheck = typeTxs.take(150)
            var historicalMaxForCat = 0.0
            var bestReasonForCat = ""

            for (tx in txsToCheck) {
                if (tx.categoryName != cName) continue

                var txRawScore = 0.0
                val currentTxReasons = mutableListOf<String>()

                // Match note
                if (note.isNotBlank() && tx.note.isNotBlank()) {
                    val curNoteNorm = note.trim().lowercase()
                    val txNoteNorm = tx.note.trim().lowercase()
                    
                    if (curNoteNorm == txNoteNorm) {
                        txRawScore += 70.0
                        currentTxReasons.add("Ghi chú y hệt")
                    } else if (curNoteNorm.contains(txNoteNorm) || txNoteNorm.contains(curNoteNorm)) {
                        txRawScore += 45.0
                        currentTxReasons.add("Ghi chú tương đồng")
                    }
                }

                // Match exact or close amounts
                if (currentAmount > 0.0) {
                    if (currentAmount == tx.amount) {
                         txRawScore += 20.0
                         currentTxReasons.add("Số tiền giống")
                    } else {
                        val diff = Math.abs(currentAmount - tx.amount)
                        val maxAmt = Math.max(currentAmount, tx.amount)
                        if (maxAmt > 0 && diff / maxAmt <= 0.1) {
                            txRawScore += 15.0
                            currentTxReasons.add("Số tiền tương tự")
                        } else if (maxAmt > 0 && diff / maxAmt <= 0.25) {
                            txRawScore += 10.0
                            currentTxReasons.add("Số tiền hơi giống")
                        }
                    }
                }

                // Match hours
                val calCurrent = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                val calTx = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                val currHour = calCurrent.get(Calendar.HOUR_OF_DAY)
                val txHour = calTx.get(Calendar.HOUR_OF_DAY)
                val hourDiff = Math.abs(currHour - txHour)
                if (hourDiff == 0 || hourDiff == 23) {
                    txRawScore += 10.0
                    currentTxReasons.add("Cùng khung giờ")
                } else if (hourDiff <= 2 || hourDiff >= 22) {
                    txRawScore += 7.0
                    currentTxReasons.add("Gần khung giờ")
                }

                // Match wallet
                if (selectedWalletId != null && tx.walletId == selectedWalletId) {
                    txRawScore += 5.0
                    currentTxReasons.add("Cùng tài khoản")
                }

                // Recency preference
                val daysAgo = (selectedTimestamp - tx.timestamp) / (24L * 60L * 60L * 1000L).toDouble()
                val recencyWeight = 1.0 / (1.0 + Math.max(0.0, daysAgo) / 30.0)

                val weightedScore = txRawScore * recencyWeight
                if (weightedScore > historicalMaxForCat) {
                    historicalMaxForCat = weightedScore
                    bestReasonForCat = currentTxReasons.joinToString(", ")
                }
            }

            categoryScore += historicalMaxForCat
            
            if (categoryScore > 10.0) {
                val displayReason = if (bestReasonForCat.isNotEmpty()) {
                    bestReasonForCat
                } else if (reasonsList.isNotEmpty()) {
                    reasonsList.joinToString(", ")
                } else {
                    "Danh mục quen thuộc"
                }
                SmartCategorySuggestion(
                    category = cat,
                    score = Math.min(100.0, categoryScore),
                    reason = displayReason
                )
            } else {
                null
            }
        }.sortedByDescending { it.score }.take(3)
    }

    // High confidence trigger for auto-selecting category
    LaunchedEffect(smartSuggestions) {
        if (!hasManuallySelected && smartSuggestions.isNotEmpty()) {
            val topSuggest = smartSuggestions.first()
            if (topSuggest.score >= 65.0) {
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

    val displayCategories = remember(filteredCategories, categoryUsageCounts) {
        filteredCategories.sortedByDescending { cat ->
            categoryUsageCounts.getOrDefault(cat.name, 0)
        }
    }

    LaunchedEffect(displayCategories) {
        if (displayCategories.isNotEmpty() && selectedCategoryName.isBlank()) {
            selectedCategoryName = displayCategories.first().name
        } else if (displayCategories.isNotEmpty() && filteredCategories.none { it.name == selectedCategoryName }) {
            selectedCategoryName = displayCategories.first().name
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
            onDismissKeyboard = {
                scope.launch {
                    scrollState.animateScrollTo(0)
                }
            },
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
            
            // Thêm tính năng chọn Event (Ưu tiên các sự kiện active)
            val activeEventsForSelection = events.filter { it.isActive }
                .sortedWith(
                    compareByDescending<com.app.data.Event> { ev ->
                        FormatHelper.isEventOngoing(ev.startDate, ev.endDate)
                    }.thenByDescending { ev -> ev.startDate }
                )

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
                            
                            Box(modifier = Modifier.weight(1f).padding(bottom = 6.dp)) {
                                val borderColor = if (isSelected) Color(0xFFF44336) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                val bgColor = MaterialTheme.colorScheme.surface
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bgColor)
                                        .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedCategoryName = cat.name
                                            hasManuallySelected = true
                                        }
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
                        if (rowCats.size < 4) {
                            for (i in 0 until (4 - rowCats.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }


                }
            }
        }

        } // Kết thúc khối if (!isTransfer)

        // 4 & 6. Ghi chú hóa đơn/mô tả
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Ghi chú / Mô tả giao dịch") },
            leadingIcon = { Icon(imageVector = Icons.Default.EditNote, contentDescription = "Note") },
            modifier = Modifier.fillMaxWidth().testTag("tx_note_input"),
            singleLine = true,
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
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(end = 48.dp) // Tránh che icon lịch
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDateTimePicker() }
                )
            }
        }

        // --- activeEventsForSelection is defined above ---
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Lưu
        Button(
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                
                val amount = FormatHelper.evaluateExpression(rawExpression)
                val walletId = selectedWalletId
                if (amount > 0 && walletId != null) {
                    if (isTransfer) {
                        val targetId = transferWalletId
                        if (targetId != null && targetId != walletId) {
                            val sourceWallet = wallets.firstOrNull { it.id == walletId }
                            val targetWallet = wallets.firstOrNull { it.id == targetId }
                            val sourceName = sourceWallet?.name ?: "Ví nguồn"
                            val targetName = targetWallet?.name ?: "Ví đích"
                            
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
                            scope.launch { scrollState.animateScrollTo(0) }
                        } else {
                            viewModel.showWarningNotification("Vui lòng chọn ví nhận khác nhau!")
                        }
                    } else {
                        // Normal manual transaction
                        if (selectedCategoryName.isEmpty()) {
                            viewModel.showWarningNotification("Vui lòng chọn hạng mục giao dịch!")
                            return@Button
                        }
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
            },
            enabled = if (isTransfer) {
                FormatHelper.evaluateExpression(rawExpression) > 0.0 && selectedWalletId != null && transferWalletId != null && transferWalletId != selectedWalletId
            } else {
                FormatHelper.evaluateExpression(rawExpression) > 0.0 && selectedWalletId != null && selectedCategoryName.isNotEmpty()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_transaction_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedType == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
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


