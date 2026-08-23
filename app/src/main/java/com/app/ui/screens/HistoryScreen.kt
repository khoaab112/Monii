package com.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.data.Categories
import com.app.data.Transaction
import com.app.data.Wallet
import com.app.data.FinanceCategory
import com.app.data.Event
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.IconMapper
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import com.app.R
import com.app.ui.components.AppModalBottomSheet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: FinanceViewModel,
    onNavigateToTimeline: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val dailyTransactions by viewModel.dailyTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val categoriesList by viewModel.categoriesList.collectAsState()
    val walletsList by viewModel.allWallets.collectAsState()
    val allEvents by viewModel.allEvents.collectAsState()
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    if (transactionToDelete != null) {
        val typeTag = if (transactionToDelete!!.type == "EXPENSE") "khoản chi" else "khoản thu/chuyển"
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Xác nhận xóa giao dịch?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa $typeTag '${FormatHelper.formatVND(transactionToDelete!!.amount)}' (${transactionToDelete!!.note})? Hành động này không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionToDelete?.let { tx ->
                            viewModel.deleteTransaction(tx)
                            viewModel.showSuccessNotification("Xóa giao dịch thành công")
                        }
                        transactionToDelete = null
                    }
                ) {
                    Text("XÓA", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("HỦY")
                }
            }
        )
    }

    // --- DISPLAY MODE STATES ---
    var displayMode by rememberSaveable { mutableStateOf("CALENDAR") } // LIST, CALENDAR
    var selectedCalendarYear by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedCalendarMonth by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedCalendarDayOfMonth by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    var selectedCalendarDay = remember(selectedCalendarYear, selectedCalendarMonth, selectedCalendarDayOfMonth) {
        CalendarDay(
            year = selectedCalendarYear,
            month = selectedCalendarMonth,
            dayOfMonth = selectedCalendarDayOfMonth,
            isCurrentMonth = true // Not strictly used for tracking selection
        )
    }

    var showDayDetailDialog by remember { mutableStateOf<CalendarDay?>(null) }
    var showQuickActionMenuByDay by remember { mutableStateOf<CalendarDay?>(null) }
    var showQuickAddDialogByDay by remember { mutableStateOf<CalendarDay?>(null) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFilterSummarySheet by rememberSaveable { mutableStateOf(false) }

    // --- TIME FILTER STATES ---
    var activeTimeFilterMode by rememberSaveable { mutableStateOf("MONTH") } // ALL, WEEK, DAY, MONTH, YEAR, RANGE
    var selectedCustomDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedCustomMonth by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0 to 11
    var selectedCustomMonthYear by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedCustomYear by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedRangeStartMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedRangeEndMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    val selectedCustomDate: Calendar? = selectedCustomDateMillis?.let { Calendar.getInstance().apply { timeInMillis = it } }
    val selectedRangeStart: Calendar? = selectedRangeStartMillis?.let { Calendar.getInstance().apply { timeInMillis = it } }
    val selectedRangeEnd: Calendar? = selectedRangeEndMillis?.let { Calendar.getInstance().apply { timeInMillis = it } }

    LaunchedEffect(displayMode) {
        if (displayMode == "CALENDAR") {
            if (activeTimeFilterMode != "MONTH") {
                activeTimeFilterMode = "MONTH"
            }
        }
    }

    val showDatePicker = {
        val currentCal = selectedCustomDate ?: Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                selectedCustomDateMillis = newCal.timeInMillis
                activeTimeFilterMode = "DAY"
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    var showMonthDialog by remember { mutableStateOf(false) }
    var showYearDialog by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }

    // Synchronize Local Time Filter Selection with ViewModel filter ranges
    LaunchedEffect(
        activeTimeFilterMode,
        selectedCustomDate,
        selectedCustomMonth,
        selectedCustomMonthYear,
        selectedCustomYear,
        selectedRangeStart,
        selectedRangeEnd
    ) {
        when (activeTimeFilterMode) {
            "ALL" -> {
                viewModel.setDateFilterRange(null, null)
            }
            "WEEK" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                viewModel.setDateFilterRange(start, end)
            }
            "DAY" -> {
                val cal = selectedCustomDate ?: Calendar.getInstance()
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = startCal.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis

                viewModel.setDateFilterRange(start, end)
            }
            "MONTH" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedCustomMonthYear)
                    set(Calendar.MONTH, selectedCustomMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedCustomMonthYear)
                    set(Calendar.MONTH, selectedCustomMonth)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis

                viewModel.setDateFilterRange(start, end)
            }
            "YEAR" -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedCustomYear)
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedCustomYear)
                    set(Calendar.DAY_OF_YEAR, getActualMaximum(Calendar.DAY_OF_YEAR))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis

                viewModel.setDateFilterRange(start, end)
            }
            "RANGE" -> {
                val sCal = selectedRangeStart
                val eCal = selectedRangeEnd
                if (sCal != null && eCal != null) {
                    val startCal = Calendar.getInstance().apply {
                        timeInMillis = sCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = eCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    viewModel.setDateFilterRange(startCal.timeInMillis, endCal.timeInMillis)
                } else {
                    viewModel.setDateFilterRange(null, null)
                }
            }
        }
    }

    // --- MONTH SELECTION DIALOG ---
    if (showMonthDialog) {
        AlertDialog(
            onDismissRequest = { showMonthDialog = false },
            title = { Text("Chọn Tháng", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedCustomMonthYear-- }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Year")
                        }
                        Text("Năm $selectedCustomMonthYear", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { selectedCustomMonthYear++ }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Year")
                        }
                    }

                    val months = listOf(
                        "Thg 1", "Thg 2", "Thg 3", "Thg 4",
                        "Thg 5", "Thg 6", "Thg 7", "Thg 8",
                        "Thg 9", "Thg 10", "Thg 11", "Thg 12"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (j in 0 until 3) {
                                    val monthIdx = i * 3 + j
                                    val isSelected = selectedCustomMonth == monthIdx
                                    Button(
                                        onClick = {
                                            selectedCustomMonth = monthIdx
                                            activeTimeFilterMode = "MONTH"
                                            showMonthDialog = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(months[monthIdx], fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // --- YEAR SELECTION DIALOG ---
    if (showYearDialog) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 4..currentYear + 2).toList()

        AlertDialog(
            onDismissRequest = { showYearDialog = false },
            title = { Text("Chọn Năm", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.chunked(3).forEach { rowYears ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowYears.forEach { yr ->
                                val isSelected = selectedCustomYear == yr
                                Button(
                                    onClick = {
                                        selectedCustomYear = yr
                                        activeTimeFilterMode = "YEAR"
                                        showYearDialog = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(yr.toString(), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showYearDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // --- RANGE SELECTION DIALOG ---
    if (showRangeDialog) {
        val pickerContext = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            title = { Text("Khoảng thời gian", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val currentCal = selectedRangeStart ?: Calendar.getInstance()
                                android.app.DatePickerDialog(
                                    pickerContext,
                                    { _, year, month, dayOfMonth ->
                                        selectedRangeStartMillis = Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth)
                                        }.timeInMillis
                                    },
                                    currentCal.get(Calendar.YEAR),
                                    currentCal.get(Calendar.MONTH),
                                    currentCal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = selectedRangeStart?.let {
                                    SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(it.time)
                                } ?: "Từ ngày...",
                                fontSize = 13.sp
                            )
                        }

                        Text("—", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Button(
                            onClick = {
                                val currentCal = selectedRangeEnd ?: Calendar.getInstance()
                                android.app.DatePickerDialog(
                                    pickerContext,
                                    { _, year, month, dayOfMonth ->
                                        selectedRangeEndMillis = Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth)
                                        }.timeInMillis
                                    },
                                    currentCal.get(Calendar.YEAR),
                                    currentCal.get(Calendar.MONTH),
                                    currentCal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = selectedRangeEnd?.let {
                                    SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(it.time)
                                } ?: "Đến ngày...",
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (selectedRangeStart != null && selectedRangeEnd != null) {
                        if (selectedRangeStart!!.after(selectedRangeEnd)) {
                            Text(
                                text = "Ngày bắt đầu phải nhỏ hơn ngày kết thúc!",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedRangeStart != null && selectedRangeEnd != null && !selectedRangeStart!!.after(selectedRangeEnd)) {
                            activeTimeFilterMode = "RANGE"
                            showRangeDialog = false
                        }
                    },
                    enabled = selectedRangeStart != null && selectedRangeEnd != null && !selectedRangeStart!!.after(selectedRangeEnd)
                ) {
                    Text("Áp dụng")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Grouping transactions by date for a premium timeline look
    val groupedTransactions = remember(filteredTransactions) {
        filteredTransactions.groupBy { FormatHelper.formatDate(it.timestamp) }
    }

    val savingsWalletIds = remember(walletsList) {
        walletsList.filter { it.type == "SAVINGS" }.map { it.id }.toSet()
    }

    // Precompute daily summaries to optimize lazy list scrolling performance
    val dailySummaries = remember(groupedTransactions, savingsWalletIds) {
        groupedTransactions.mapValues { (_, txList) ->
            val summary = com.app.ui.calculateRealFinancialSummary(txList, savingsWalletIds)
            Pair(summary.realIncome, summary.realExpense)
        }
    }

    // Grouping transactions by calendar date key for instant calendar day lookup
    val transactionsByDayKey = remember(dailyTransactions) {
        dailyTransactions.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var isFiltersExpanded by remember { mutableStateOf(false) }

    val filterSummary = remember(
        searchQuery,
        selectedTypeFilter,
        selectedCategoryFilter,
        activeTimeFilterMode,
        selectedCustomDate,
        selectedCustomMonth,
        selectedCustomMonthYear,
        selectedCustomYear,
        selectedRangeStart,
        selectedRangeEnd
    ) {
        val parts = mutableListOf<String>()
        if (searchQuery.isNotEmpty()) {
            parts.add("Tìm: \"$searchQuery\"")
        }
        if (selectedTypeFilter != "ALL") {
            parts.add(if (selectedTypeFilter == "EXPENSE") "Chi" else "Thu")
        }
        if (selectedCategoryFilter != "ALL") {
            parts.add("Hạng mục: $selectedCategoryFilter")
        }
        val timeStr = when (activeTimeFilterMode) {
            "ALL" -> "Mọi lúc"
            "DAY" -> selectedCustomDate?.let { 
                SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(it.time) 
            } ?: "Một ngày"
            "WEEK" -> "1 Tuần qua"
            "RANGE" -> if (selectedRangeStart != null && selectedRangeEnd != null) {
                val s = SimpleDateFormat("dd/MM", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedRangeStart!!.time)
                val e = SimpleDateFormat("dd/MM", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedRangeEnd!!.time)
                "$s - $e"
            } else "Khoảng ngày"
            "MONTH" -> "Thg ${selectedCustomMonth + 1}/$selectedCustomMonthYear"
            "YEAR" -> "Năm $selectedCustomYear"
            else -> "Mọi lúc"
        }
        if (timeStr != "Mọi lúc") {
            parts.add(timeStr)
        }
        
        if (parts.isEmpty()) "Mọi giao dịch (Không có bộ lọc)" else parts.joinToString(" • ")
    }

    val selectedRangeStr = remember(
        activeTimeFilterMode,
        selectedCustomDate,
        selectedCustomMonth,
        selectedCustomMonthYear,
        selectedCustomYear,
        selectedRangeStart,
        selectedRangeEnd
    ) {
        when (activeTimeFilterMode) {
            "ALL" -> "Mọi lúc"
            "DAY" -> selectedCustomDate?.let { 
                SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(it.time) 
            } ?: "Một ngày"
            "WEEK" -> "1 Tuần qua"
            "RANGE" -> if (selectedRangeStart != null && selectedRangeEnd != null) {
                val s = SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedRangeStart.time)
                val e = SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedRangeEnd.time)
                "$s - $e"
            } else "Khoảng ngày"
            "MONTH" -> "Thg ${selectedCustomMonth + 1}/$selectedCustomMonthYear"
            "YEAR" -> "Năm $selectedCustomYear"
            else -> "Mọi lúc"
        }
    }

    if (showFilterSummarySheet) {
        FilterSummaryBottomSheet(
            filteredTransactions = filteredTransactions,
            walletsList = walletsList,
            filterSummary = filterSummary,
            searchQuery = searchQuery,
            selectedTypeFilter = selectedTypeFilter,
            selectedCategoryFilter = selectedCategoryFilter,
            activeTimeFilterMode = activeTimeFilterMode,
            selectedRangeStr = selectedRangeStr,
            onDismissRequest = { showFilterSummarySheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode Switcher (Danh sách / Lịch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("CALENDAR" to "Lịch", "LIST" to "Danh sách").forEach { (mode, label) ->
                val isSelected = displayMode == mode
                val bgColors = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val textColors = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColors)
                        .clickable { displayMode = mode }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (mode == "LIST") Icons.AutoMirrored.Filled.List else Icons.Default.CalendarToday,
                        contentDescription = label,
                        tint = textColors,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColors
                    )
                }
            }
        }

        if (displayMode == "LIST") {
            // COLLAPSIBLE FILTER + SEARCH GROUP CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Clickable Header to Expand/Collapse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFiltersExpanded = !isFiltersExpanded }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Bộ lọc và tìm kiếm",
                            tint = if (isFiltersExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Bộ lọc & Tìm kiếm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (filterSummary.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = filterSummary,
                                    fontSize = 10.sp,
                                    color = if (filterSummary.startsWith("Mọi giao dịch")) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                           else MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    IconButton(
                        onClick = { isFiltersExpanded = !isFiltersExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFiltersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isFiltersExpanded) "Thu gọn" else "Mở rộng",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                AnimatedVisibility(
                    visible = isFiltersExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        
                        // 1. Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Tìm kiếm ghi chú, danh mục, ví...", fontSize = 13.sp) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search Log", modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        
                        // 2. Transaction Type Group
                        Text(
                            text = "Loại giao dịch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "ALL" to "Tất cả",
                                "EXPENSE" to "Khoản Chi",
                                "INCOME" to "Khoản Thu"
                            ).forEach { (typeVal, name) ->
                                val isSelected = selectedTypeFilter == typeVal
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setTypeFilter(typeVal) },
                                    label = { Text(name, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("filter_type_$typeVal"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        
                        // 3. Category selector Group
                        Text(
                            text = "Hạng mục giao dịch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isAllCategory = selectedCategoryFilter == "ALL"
                            FilterChip(
                                selected = isAllCategory,
                                onClick = { viewModel.setCategoryFilter("ALL") },
                                label = { Text("Mọi danh mục", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("filter_category_ALL"),
                                shape = RoundedCornerShape(8.dp)
                            )
                
                            Box(
                                modifier = Modifier.weight(1.2f)
                            ) {
                                val selectedCatObj = categoriesList.find { it.name == selectedCategoryFilter }
                                FilterChip(
                                    selected = !isAllCategory,
                                    onClick = { categoryDropdownExpanded = true },
                                    label = {
                                        Text(
                                            text = if (isAllCategory) "Chọn danh mục..." else selectedCategoryFilter,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Category key",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    leadingIcon = selectedCatObj?.let { cat ->
                                        {
                                            Icon(
                                                imageVector = IconMapper.getIconByName(cat.iconName),
                                                contentDescription = cat.name,
                                                tint = if (!isAllCategory) Color.Unspecified else FormatHelper.parseColor(cat.colorHex),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("filter_category_select_selector"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                
                                DropdownMenu(
                                    expanded = categoryDropdownExpanded,
                                    onDismissRequest = { categoryDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.55f)
                                ) {
                                    categoriesList.forEach { cat ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = IconMapper.getIconByName(cat.iconName),
                                                        contentDescription = cat.name,
                                                        tint = FormatHelper.parseColor(cat.colorHex),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(cat.name, fontSize = 13.sp)
                                                }
                                            },
                                            onClick = {
                                                viewModel.setCategoryFilter(cat.name)
                                                categoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 4. Time Filter Group
                        Text(
                            text = "Thời gian",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // All time
                            val isAll = activeTimeFilterMode == "ALL"
                            FilterChip(
                                selected = isAll,
                                onClick = { activeTimeFilterMode = "ALL" },
                                label = { Text("Mọi lúc", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "All time",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("time_filter_ALL"),
                                shape = RoundedCornerShape(8.dp)
                            )
            
                            // Day
                            val isDay = activeTimeFilterMode == "DAY"
                            val dayLabel = if (selectedCustomDate != null) {
                                SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedCustomDate!!.time)
                            } else {
                                "Theo ngày"
                            }
                            FilterChip(
                                selected = isDay,
                                onClick = { showDatePicker() },
                                label = { Text(dayLabel, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Specific day",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("time_filter_DAY"),
                                shape = RoundedCornerShape(8.dp)
                            )
            
                            // Week
                            val isWeek = activeTimeFilterMode == "WEEK"
                            FilterChip(
                                selected = isWeek,
                                onClick = { activeTimeFilterMode = "WEEK" },
                                label = { Text("1 Tuần", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Last week",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("time_filter_WEEK"),
                                shape = RoundedCornerShape(8.dp)
                            )
            
                            // Range
                            val isRange = activeTimeFilterMode == "RANGE"
                            val rangeLabel = if (selectedRangeStart != null && selectedRangeEnd != null) {
                                val startStr = SimpleDateFormat("dd/MM", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedRangeStart!!.time)
                                val endStr = SimpleDateFormat("dd/MM", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(selectedRangeEnd!!.time)
                                "$startStr - $endStr"
                            } else {
                                "Khoảng ngày"
                            }
                            FilterChip(
                                selected = isRange,
                                onClick = { showRangeDialog = true },
                                label = { Text(rangeLabel, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Date range",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("time_filter_RANGE"),
                                shape = RoundedCornerShape(8.dp)
                            )
            
                            // Month
                            val isMonth = activeTimeFilterMode == "MONTH"
                            val monthLabel = "Thg ${selectedCustomMonth + 1}/$selectedCustomMonthYear"
                            FilterChip(
                                selected = isMonth,
                                onClick = { showMonthDialog = true },
                                label = { Text(if (isMonth) monthLabel else "Theo tháng", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Specific month",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("time_filter_MONTH"),
                                shape = RoundedCornerShape(8.dp)
                            )
            
                            // Year
                            val isYear = activeTimeFilterMode == "YEAR"
                            val yearLabel = "Năm $selectedCustomYear"
                            FilterChip(
                                selected = isYear,
                                onClick = { showYearDialog = true },
                                label = { Text(if (isYear) yearLabel else "Theo năm", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Specific year",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("time_filter_YEAR"),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        
                        // Action buttons in expanded filter view
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { showFilterSummarySheet = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("view_filter_summary_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = "Tổng hợp",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Xem kết quả tổng hợp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (searchQuery.isNotEmpty() || selectedTypeFilter != "ALL" || selectedCategoryFilter != "ALL" || activeTimeFilterMode != "ALL") {
                                TextButton(
                                    onClick = {
                                        viewModel.setSearchQuery("")
                                        viewModel.setTypeFilter("ALL")
                                        viewModel.setCategoryFilter("ALL")
                                        activeTimeFilterMode = "ALL"
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Xóa lọc", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bỏ toàn bộ lọc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        
        if (displayMode == "LIST") {
            if (!isFiltersExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showFilterSummarySheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("view_filter_summary_collapsed_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Tổng hợp",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xem kết quả tổng hợp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    if (searchQuery.isNotEmpty() || selectedTypeFilter != "ALL" || selectedCategoryFilter != "ALL" || activeTimeFilterMode != "ALL") {
                        TextButton(
                            onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setTypeFilter("ALL")
                                viewModel.setCategoryFilter("ALL")
                                activeTimeFilterMode = "ALL"
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Xóa lọc", modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bỏ lọc", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Transaction Timeline List with daily summaries
            if (groupedTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val context = LocalContext.current
                        val imageLoader = remember(context) {
                            coil.ImageLoader.Builder(context)
                                .components {
                                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                                        add(coil.decode.ImageDecoderDecoder.Factory())
                                    } else {
                                        add(coil.decode.GifDecoder.Factory())
                                    }
                                }
                                .build()
                        }
                        val painter = coil.compose.rememberAsyncImagePainter(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(R.drawable.empty_state)
                                .build(),
                            imageLoader = imageLoader
                        )
                        Image(
                            painter = painter,
                            contentDescription = "No transactions",
                            modifier = Modifier.size(150.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Không tìm thấy giao dịch phù hợp!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Thử thay đổi bộ lọc tìm kiếm",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("history_transactions_lazy_column"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedTransactions.forEach { (dateStr, txList) ->
                        stickyHeader {
                            val summary = dailySummaries[dateStr] ?: Pair(0.0, 0.0)
                            val totalIncome = summary.first
                            val totalExpense = summary.second

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dateStr,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onNavigateToTimeline(dateStr) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timeline,
                                            contentDescription = "Timeline",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (totalExpense > 0.0) {
                                        Text(
                                            text = "-${FormatHelper.formatVND(totalExpense)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFF44336)
                                        )
                                    }
                                    if (totalIncome > 0.0) {
                                        Text(
                                            text = "+${FormatHelper.formatVND(totalIncome)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        items(txList, key = { tx -> "${tx.id}_${tx.timestamp}_${tx.hashCode()}" }) { tx ->
                            RemovableTransactionItem(
                                tx = tx,
                                onDelete = { transactionToDelete = tx },
                                onEdit = { editingTransaction = tx },
                                walletsList = walletsList
                            )
                        }
                    }
                }
            }
        } else {
            // --- CALENDAR MODE CONTENT ---
            // 1. Sleek Forest Green Navigation Header
            CalendarHeaderRow(
                selectedMonth = selectedCustomMonth,
                selectedYear = selectedCustomMonthYear,
                onMonthChange = { selectedCustomMonth = it },
                onYearChange = { selectedCustomMonthYear = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 2. 7-Column Grid View and Day Transactions Scrollable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                CalendarGrid(
                    selectedMonth = selectedCustomMonth,
                    selectedYear = selectedCustomMonthYear,
                    transactionsByDayKey = transactionsByDayKey,
                    selectedCalendarDay = selectedCalendarDay,
                    onDayClick = { day ->
                        selectedCalendarYear = day.year
                        selectedCalendarMonth = day.month
                        selectedCalendarDayOfMonth = day.dayOfMonth
                    },
                    onDayLongPress = { day ->
                        selectedCalendarYear = day.year
                        selectedCalendarMonth = day.month
                        selectedCalendarDayOfMonth = day.dayOfMonth
                        showQuickActionMenuByDay = day
                    }
                )

                val dayVal = selectedCalendarDay
                val dayKey = "${dayVal.year}-${dayVal.month}-${dayVal.dayOfMonth}"
                val dayTxs = transactionsByDayKey[dayKey] ?: emptyList()
                DayTransactionsInline(
                    day = dayVal,
                    transactions = dayTxs,
                    onEditTransaction = { tx ->
                        editingTransaction = tx
                    },
                    onDeleteTransaction = { tx ->
                        transactionToDelete = tx
                    },
                    onNavigateToTimeline = onNavigateToTimeline,
                    walletsList = walletsList
                )
            }
        }
    }

    if (editingTransaction != null) {
        EditTransactionDialog(
            tx = editingTransaction!!,
            categoriesList = categoriesList,
            walletsList = walletsList,
            eventsList = allEvents,
            onDismiss = { editingTransaction = null },
            onSave = { updatedTx ->
                viewModel.updateTransaction(updatedTx)
                editingTransaction = null
            }
        )
    }

    if (showQuickActionMenuByDay != null) {
        val dayVal = showQuickActionMenuByDay!!
        val dayKey = "${dayVal.year}-${dayVal.month}-${dayVal.dayOfMonth}"
        val dayTxs = transactionsByDayKey[dayKey] ?: emptyList()
        DayQuickActionsDialog(
            day = dayVal,
            hasTransactions = dayTxs.isNotEmpty(),
            onDismiss = { showQuickActionMenuByDay = null },
            onQuickAdd = {
                showQuickAddDialogByDay = dayVal
                showQuickActionMenuByDay = null
            },
            onManageDay = {
                showQuickActionMenuByDay = null
            }
        )
    }

    if (showQuickAddDialogByDay != null) {
        val dayVal = showQuickAddDialogByDay!!
        val cal = Calendar.getInstance().apply {
            set(dayVal.year, dayVal.month, dayVal.dayOfMonth)
        }
        QuickAddTransactionDialog(
            initialTimestamp = cal.timeInMillis,
            categoriesList = categoriesList,
            walletsList = walletsList,
            onDismiss = { showQuickAddDialogByDay = null },
            onSave = { walletId, type, amount, categoryName, categoryIcon, categoryColor, note, timestamp ->
                viewModel.addTransaction(
                    walletId = walletId,
                    type = type,
                    amount = amount,
                    categoryName = categoryName,
                    note = note,
                    timestamp = timestamp
                )
                showQuickAddDialogByDay = null
            }
        )
    }
}

@Composable
fun RemovableTransactionItem(
    tx: Transaction,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    walletsList: List<Wallet> = emptyList()
) {
    val isTransfer = tx.type == "TRANSFER"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isTransfer) Color(0xFF2196F3).copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
            .clickable { onEdit() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isTransfer) Color(0xFF2196F3) else FormatHelper.parseColor(tx.categoryColor)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isTransfer) Icons.AutoMirrored.Filled.CompareArrows else IconMapper.getIconByName(tx.categoryIcon),
                contentDescription = if (isTransfer) "Nội bộ" else tx.categoryName,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Details Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isTransfer) "Nội bộ" else tx.categoryName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTransfer) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val subText = remember(tx, walletsList) {
                    if (isTransfer) {
                        val destWallet = walletsList.find { it.id == tx.destinationWalletId }
                        val destName = destWallet?.name ?: "Ví nhận"
                        if (tx.note.isNotBlank()) {
                            "${tx.walletName} ➔ $destName - ${tx.note}"
                        } else {
                            "${tx.walletName} ➔ $destName"
                        }
                    } else {
                        if (tx.note.isNotBlank()) "${tx.walletName} - ${tx.note}" else tx.walletName
                    }
                }
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    color = if (isTransfer) Color(0xFF2196F3) else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tx.isRecurring) {
                    Text(text = "•", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Định kỳ",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        // Money + Delete Panel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isAdjustmentDecrease = tx.type == "ADJUSTMENT" && !tx.note.contains("tăng")
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (tx.type == "EXPENSE" || isAdjustmentDecrease) "-${FormatHelper.formatVND(tx.amount)}"
                           else if (tx.type == "TRANSFER") "±${FormatHelper.formatVND(tx.amount)}"
                           else "+${FormatHelper.formatVND(tx.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tx.type == "EXPENSE" || isAdjustmentDecrease) Color(0xFFF44336) else if (tx.type == "TRANSFER") Color(0xFF2196F3) else Color(0xFF4CAF50),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = FormatHelper.formatTime(tx.timestamp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("delete_tx_btn_${tx.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EditTransactionDialog(
    tx: Transaction,
    categoriesList: List<FinanceCategory>,
    walletsList: List<Wallet>,
    eventsList: List<Event> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val scope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    val dismissWithAnimation = {
        scope.launch {
            isVisible = false
            delay(220)
            onDismiss()
        }
    }

    var amountText by remember { mutableStateOf(tx.amount.toLong().toString()) }
    var selectedType by remember { mutableStateOf(tx.type) } // EXPENSE, INCOME
    var selectedCategoryName by remember { mutableStateOf(if (tx.type == "TRANSFER") "Nội bộ" else tx.categoryName) }
    var selectedWalletId by remember { mutableStateOf<Int?>(tx.walletId) }
    var selectedEventId by remember { mutableStateOf<Int?>(tx.eventId) }
    var noteText by remember { mutableStateOf(tx.note) }
    var isRecurring by remember { mutableStateOf(tx.isRecurring) }
    var recurrencePeriod by remember { mutableStateOf(tx.recurrencePeriod.ifEmpty { "DAILY" }) }
    var selectedTimestamp by remember { mutableStateOf(tx.timestamp) }
    
    // Dropdowns visibility
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var walletDropdownExpanded by remember { mutableStateOf(false) }
    var eventDropdownExpanded by remember { mutableStateOf(false) }
    
    // Filter categories depending on selectedType. Only leaf categories (no children) are shown.
    val filteredCategories = remember(categoriesList, selectedType) {
        val typeFiltered = categoriesList.filter { it.type == selectedType || it.type == "BOTH" }
        val parentNames = categoriesList.mapNotNull { it.parentName }.toSet()
        typeFiltered.filter { it.name !in parentNames }
    }
    
    // Ensure the category chosen is kept valid
    LaunchedEffect(selectedType) {
        if (selectedType == "TRANSFER") {
            selectedCategoryName = "Nội bộ"
            return@LaunchedEffect
        }
        val hasCategory = filteredCategories.any { it.name == selectedCategoryName }
        if (!hasCategory && filteredCategories.isNotEmpty()) {
            selectedCategoryName = filteredCategories.first().name
        }
    }

    val dateTimeFormatter = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()) }
    var dateLabel by remember(selectedTimestamp) {
        mutableStateOf(dateTimeFormatter.format(selectedTimestamp))
    }

    val showDateTimePicker = {
        val currentCal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val timePicker = android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val finalCal = Calendar.getInstance().apply {
                    timeInMillis = selectedTimestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedTimestamp = finalCal.timeInMillis
            },
            currentCal.get(Calendar.HOUR_OF_DAY),
            currentCal.get(Calendar.MINUTE),
            true
        )

        val datePicker = android.app.DatePickerDialog(
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

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { dismissWithAnimation() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .clickable(enabled = false) {}, // Prevent clicks outside from propagating
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 54.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sửa Giao Dịch",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { dismissWithAnimation() }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Edit")
                            }
                        }

                        // Scrollable Content Column (takes up screen but keeps actions pinned)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Transaction Type (Income / Expense Selector)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "EXPENSE" to "Khoản Chi",
                                    "INCOME" to "Khoản Thu"
                                ).forEach { (typeVal, label) ->
                                    val isSelected = selectedType == typeVal
                                    val targetBgColor = if (isSelected) {
                                        if (typeVal == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                    val targetContentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                                    Button(
                                        onClick = { selectedType = typeVal },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = targetBgColor,
                                            contentColor = targetContentColor
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("edit_tx_type_$typeVal")
                                    ) {
                                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // 2. Amount Input field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Số tiền *",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("edit_tx_amount_input")
                                )
                                // Real-time currency preview helper
                                val expressionValue = FormatHelper.evaluateExpression(amountText)
                                if (expressionValue > 0.0) {
                                    Text(
                                        text = "= " + FormatHelper.formatVND(expressionValue),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedType == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }

                            // 3. Note Field
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Ghi chú",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    placeholder = { Text("Mô tả chi tiết giao dịch...") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("edit_tx_note_input")
                                )
                            }

                            // 4. Category Selector Dropdown Button
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Danh mục *",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val currentCategoryObject = categoriesList.find { it.name == selectedCategoryName }
                                    
                                    OutlinedTextField(
                                        value = selectedCategoryName,
                                        onValueChange = {},
                                        readOnly = true,
                                        leadingIcon = if (selectedType == "TRANSFER") {
                                            {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                                    contentDescription = "TRANSFER",
                                                    tint = Color(0xFF2196F3),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        } else {
                                            currentCategoryObject?.let { cat ->
                                                {
                                                    Icon(
                                                        imageVector = IconMapper.getIconByName(cat.iconName),
                                                        contentDescription = cat.name,
                                                        tint = FormatHelper.parseColor(cat.colorHex),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_tx_category_selector")
                                    )
                                    // Bắt sự kiện click mượt mà phủ lên toàn bộ ô input
                                    if (selectedType != "TRANSFER") {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(Color.Transparent)
                                                .clickable { categoryDropdownExpanded = !categoryDropdownExpanded }
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = categoryDropdownExpanded,
                                        onDismissRequest = { categoryDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        filteredCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = IconMapper.getIconByName(cat.iconName),
                                                            contentDescription = cat.name,
                                                            tint = FormatHelper.parseColor(cat.colorHex),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(cat.name, fontSize = 14.sp)
                                                    }
                                                },
                                                onClick = {
                                                    selectedCategoryName = cat.name
                                                    categoryDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 5. Wallet Dropdown Selector
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Chọn tài khoản / Ví *",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val currentWalletObject = walletsList.find { it.id == selectedWalletId }
                                    
                                    OutlinedTextField(
                                        value = currentWalletObject?.name ?: "Chưa chọn tài khoản",
                                        onValueChange = {},
                                        readOnly = true,
                                        leadingIcon = currentWalletObject?.let { w ->
                                            {
                                                Icon(
                                                    imageVector = IconMapper.getIconByName(w.iconName),
                                                    contentDescription = w.name,
                                                    tint = FormatHelper.parseColor(w.colorHex),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_tx_wallet_selector")
                                    )
                                    // Bắt sự kiện click mượt mà phủ lên toàn bộ ô input
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Transparent)
                                            .clickable { walletDropdownExpanded = !walletDropdownExpanded }
                                    )

                                    DropdownMenu(
                                        expanded = walletDropdownExpanded,
                                        onDismissRequest = { walletDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        walletsList.forEach { w ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = IconMapper.getIconByName(w.iconName),
                                                            contentDescription = w.name,
                                                            tint = FormatHelper.parseColor(w.colorHex),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(w.name, fontSize = 14.sp)
                                                    }
                                                },
                                                onClick = {
                                                    selectedWalletId = w.id
                                                    walletDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 6. Time and Date Selector
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Thời gian giao dịch",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(
                                    onClick = showDateTimePicker,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("edit_tx_datetime_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "Thời gian: $dateLabel", fontSize = 14.sp)
                                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pick DateTime", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // 7. Event Selector (Chỉ hiển thị input khi bản ghi có eventId, ngược lại ẩn input và làm mờ xám label)
                            if (tx.eventId != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Sự kiện liên kết",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        val currentEvent = eventsList.find { it.id == selectedEventId }
                                        val eventColor = currentEvent?.let {
                                            try { Color(android.graphics.Color.parseColor(it.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                                        } ?: MaterialTheme.colorScheme.outline

                                        OutlinedTextField(
                                            value = currentEvent?.name ?: "Không thuộc sự kiện nào",
                                            onValueChange = {},
                                            readOnly = true,
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (selectedEventId != null) Icons.Default.Event else Icons.Default.EventBusy,
                                                    contentDescription = "Event",
                                                    tint = eventColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("edit_tx_event_selector")
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(Color.Transparent)
                                                .clickable { eventDropdownExpanded = !eventDropdownExpanded }
                                        )

                                        DropdownMenu(
                                            expanded = eventDropdownExpanded,
                                            onDismissRequest = { eventDropdownExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            // Option to unlink/remove from event
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Không thuộc sự kiện nào",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text("Không thuộc sự kiện nào (Gỡ sự kiện)", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                                                    }
                                                },
                                                onClick = {
                                                    selectedEventId = null
                                                    eventDropdownExpanded = false
                                                }
                                            )
                                            val selectableEvents = remember(eventsList, selectedTimestamp, selectedEventId) {
                                                eventsList.filter { ev ->
                                                    (ev.isActive && FormatHelper.isEventOngoing(ev.startDate, ev.endDate, selectedTimestamp)) || ev.id == selectedEventId
                                                }.sortedByDescending { it.startDate }
                                            }
                                            selectableEvents.forEach { ev ->
                                                val evColor = try { Color(android.graphics.Color.parseColor(ev.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(12.dp)
                                                                    .clip(CircleShape)
                                                                    .background(evColor)
                                                            )
                                                            Text(ev.name, fontSize = 14.sp, fontWeight = if (ev.id == selectedEventId) FontWeight.Bold else FontWeight.Normal)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedEventId = ev.id
                                                        eventDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sự kiện liên kết",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        text = "Không có",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = { dismissWithAnimation() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("HỦY BỎ")
                            }

                            val finalAmount = FormatHelper.evaluateExpression(amountText)
                            Button(
                                onClick = {
                                    val targetWallet = walletsList.find { it.id == selectedWalletId } ?: walletsList.first()
                                    val updatedTx = if (selectedType == "TRANSFER") {
                                        tx.copy(
                                            walletId = targetWallet.id,
                                            walletName = targetWallet.name,
                                            type = selectedType,
                                            amount = finalAmount,
                                            categoryName = "Nội bộ",
                                            categoryIcon = "swap_horiz",
                                            categoryColor = "#2196F3",
                                            note = noteText.ifEmpty { "Chuyển tiền nội bộ" },
                                            timestamp = selectedTimestamp,
                                            isRecurring = isRecurring,
                                            recurrencePeriod = if (isRecurring) recurrencePeriod else "NONE",
                                            eventId = selectedEventId
                                        )
                                    } else {
                                        val targetCat = categoriesList.find { it.name == selectedCategoryName } ?: categoriesList.first()
                                        tx.copy(
                                            walletId = targetWallet.id,
                                            walletName = targetWallet.name,
                                            type = selectedType,
                                            amount = finalAmount,
                                            categoryName = targetCat.name,
                                            categoryIcon = targetCat.iconName,
                                            categoryColor = targetCat.colorHex,
                                            note = noteText.ifEmpty { targetCat.name },
                                            timestamp = selectedTimestamp,
                                            isRecurring = isRecurring,
                                            recurrencePeriod = if (isRecurring) recurrencePeriod else "NONE",
                                            eventId = selectedEventId
                                        )
                                    }
                                    onSave(updatedTx)
                                },
                                enabled = finalAmount > 0.0 && selectedWalletId != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp)
                                    .testTag("edit_tx_save_btn")
                            ) {
                                Text("Thay đổi", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CalendarDay(
    val year: Int,
    val month: Int, // 0-based
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean
)

private fun getCalendarGridDays(year: Int, monthIdx: Int): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    
    // Calendar for first day of current month
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, monthIdx)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=SUNDAY, 2=MONDAY, ...
    val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
    
    // Backfill previous month days
    val prevMonthCal = cal.clone() as Calendar
    prevMonthCal.add(Calendar.MONTH, -1)
    val maxDaysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val prevMonthYear = prevMonthCal.get(Calendar.YEAR)
    val prevMonthIdx = prevMonthCal.get(Calendar.MONTH)
    
    for (i in offset - 1 downTo 0) {
        val dayNo = maxDaysInPrevMonth - i
        days.add(CalendarDay(prevMonthYear, prevMonthIdx, dayNo, isCurrentMonth = false))
    }
    
    // Current month days
    val maxDaysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (dayNo in 1..maxDaysInCurrentMonth) {
        days.add(CalendarDay(year, monthIdx, dayNo, isCurrentMonth = true))
    }
    
    // Next month days to fill up to a multiple of 7
    val totalCells = if (days.size <= 35) 35 else 42
    val nextMonthCal = cal.clone() as Calendar
    nextMonthCal.add(Calendar.MONTH, 1)
    val nextMonthYear = nextMonthCal.get(Calendar.YEAR)
    val nextMonthIdx = nextMonthCal.get(Calendar.MONTH)
    
    var nextMonthDayNo = 1
    while (days.size < totalCells) {
        days.add(CalendarDay(nextMonthYear, nextMonthIdx, nextMonthDayNo, isCurrentMonth = false))
        nextMonthDayNo++
    }
    
    return days
}

@Composable
fun CalendarHeaderRow(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChange: (Int) -> Unit,
    onYearChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2E7D32)) // Beautiful Fintech Green
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Prev / Month Title / Next
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (selectedMonth == 0) {
                        onMonthChange(11)
                        onYearChange(selectedYear - 1)
                    } else {
                        onMonthChange(selectedMonth - 1)
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.25f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Tháng trước",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            val monthStr = "THÁNG %02d - %d".format(selectedMonth + 1, selectedYear)
            Text(
                text = monthStr,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    if (selectedMonth == 11) {
                        onMonthChange(0)
                        onYearChange(selectedYear + 1)
                    } else {
                        onMonthChange(selectedMonth + 1)
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.25f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Tháng sau",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Quick Month/Year Dropdown Selectors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var monthDropdownShow by remember { mutableStateOf(false) }
            var yearDropdownShow by remember { mutableStateOf(false) }

            // Month Selector
            Box {
                Row(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable { monthDropdownShow = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedMonth + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
                DropdownMenu(
                    expanded = monthDropdownShow,
                    onDismissRequest = { monthDropdownShow = false }
                ) {
                    (0..11).forEach { mIdx ->
                        DropdownMenuItem(
                            text = { Text("${mIdx + 1}", fontSize = 12.sp) },
                            onClick = {
                                onMonthChange(mIdx)
                                monthDropdownShow = false
                            }
                        )
                    }
                }
            }

            // Year Selector
            Box {
                Row(
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .clickable { yearDropdownShow = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedYear.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
                DropdownMenu(
                    expanded = yearDropdownShow,
                    onDismissRequest = { yearDropdownShow = false }
                ) {
                    val currYear = Calendar.getInstance().get(Calendar.YEAR)
                    (currYear - 4..currYear + 2).forEach { yr ->
                        DropdownMenuItem(
                            text = { Text(yr.toString(), fontSize = 12.sp) },
                            onClick = {
                                onYearChange(yr)
                                yearDropdownShow = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarGrid(
    selectedMonth: Int,
    selectedYear: Int,
    transactionsByDayKey: Map<String, List<Transaction>>,
    selectedCalendarDay: CalendarDay?,
    onDayClick: (CalendarDay) -> Unit,
    onDayLongPress: (CalendarDay) -> Unit
) {
    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Week Header (Thứ hai, ... Chủ nhật as shown in the reference image)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            val weekdays = listOf("T.Hai", "T.Ba", "T.Tư", "T.Năm", "T.Sáu", "T.Bảy", "C.Nhật")
            weekdays.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF51AE5A)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 4.dp))

        // Grid Cells
        val gridDays = remember(selectedMonth, selectedYear) {
            getCalendarGridDays(selectedYear, selectedMonth)
        }
        val rows = remember(gridDays) {
            gridDays.chunked(7)
        }

        rows.forEach { rowDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowDays.forEach { day ->
                    val dayKey = "${day.year}-${day.month}-${day.dayOfMonth}"
                    val dayTxs = transactionsByDayKey[dayKey] ?: emptyList()

                    val isToday = day.year == todayYear && day.month == todayMonth && day.dayOfMonth == todayDay
                    
                    val isFuture = when {
                        day.year > todayYear -> true
                        day.year < todayYear -> false
                        else -> {
                            if (day.month > todayMonth) true
                            else if (day.month < todayMonth) false
                            else day.dayOfMonth > todayDay
                        }
                    }

                    val isSelected = selectedCalendarDay?.run {
                        this.year == day.year && this.month == day.month && this.dayOfMonth == day.dayOfMonth
                    } == true

                    CalendarCell(
                        day = day,
                        isToday = isToday,
                        isSelected = isSelected,
                        isFuture = isFuture,
                        transactions = dayTxs,
                        onClick = { onDayClick(day) },
                        onLongPress = { onDayLongPress(day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarCell(
    day: CalendarDay,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    transactions: List<Transaction>,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val daySummary = remember(transactions) {
        com.app.ui.calculateRealFinancialSummary(transactions)
    }
    val totalIncome = daySummary.realIncome
    val totalExpense = daySummary.realExpense
    
    val hasTransactions = transactions.isNotEmpty()
    
    val cellBgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        hasTransactions -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    
    val cellBorderModifier = when {
        isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
        isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
        hasTransactions -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        else -> Modifier
    }

    val contentAlpha = if (day.isCurrentMonth) {
        if (isFuture) 0.5f else 1.0f
    } else {
        0.35f
    }

    Box(
        modifier = modifier
            .aspectRatio(0.65f)
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cellBgColor)
            .then(cellBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Day Number Label
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 2.dp, top = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = day.dayOfMonth.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    }
                )
            }
            
            // Amounts Container
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (totalExpense > 0.0) {
                    Text(
                        text = "-${formatCompactVND(totalExpense)}",
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336).copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (totalIncome > 0.0) {
                    Text(
                        text = "+${formatCompactVND(totalIncome)}",
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50).copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Keep layout spacing consistent
                if (totalIncome == 0.0 && totalExpense == 0.0) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun DayTransactionsInline(
    day: CalendarDay,
    transactions: List<Transaction>,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onNavigateToTimeline: (String) -> Unit = {},
    walletsList: List<Wallet> = emptyList()
) {
    val dateLabel = "%02d/%02d/%d".format(day.dayOfMonth, day.month + 1, day.year)
    val savingsWalletIds = remember(walletsList) {
        walletsList.filter { it.type == "SAVINGS" }.map { it.id }.toSet()
    }
    val daySummary = remember(transactions, savingsWalletIds) {
        com.app.ui.calculateRealFinancialSummary(transactions, savingsWalletIds)
    }
    val totalIncome = daySummary.realIncome
    val totalExpense = daySummary.realExpense

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header of selected day
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Giao dịch ngày $dateLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (transactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.IconButton(
                            onClick = { onNavigateToTimeline(dateLabel) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Xem Dòng thời gian",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalExpense > 0.0) {
                        Text(
                            text = "-${FormatHelper.formatVND(totalExpense)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF44336)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    if (totalIncome > 0.0) {
                        Text(
                            text = "+${FormatHelper.formatVND(totalIncome)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val context = LocalContext.current
                    val imageLoader = remember(context) {
                        coil.ImageLoader.Builder(context)
                            .components {
                                if (android.os.Build.VERSION.SDK_INT >= 28) {
                                    add(coil.decode.ImageDecoderDecoder.Factory())
                                } else {
                                    add(coil.decode.GifDecoder.Factory())
                                }
                            }
                            .build()
                    }
                    val painter = coil.compose.rememberAsyncImagePainter(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(R.drawable.empty_state)
                            .build(),
                        imageLoader = imageLoader
                    )
                    Image(
                        painter = painter,
                        contentDescription = "No transactions today",
                        modifier = Modifier.size(110.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Không có giao dịch nào trong ngày này.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transactions.forEach { tx ->
                    RemovableTransactionItem(
                        tx = tx,
                        onDelete = { onDeleteTransaction(tx) },
                        onEdit = { onEditTransaction(tx) },
                        walletsList = walletsList
                    )
                }
            }
        }
    }
}

@Composable
fun DayQuickActionsDialog(
    day: CalendarDay,
    hasTransactions: Boolean,
    onDismiss: () -> Unit,
    onQuickAdd: () -> Unit,
    onManageDay: () -> Unit
) {
    val dateLabel = "%02d/%02d/%d".format(day.dayOfMonth, day.month + 1, day.year)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Thao tác ngày $dateLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Action: Quick Add
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onQuickAdd() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Thêm Giao Dịch Nhanh", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Tạo nhanh một giao dịch cho ngày này", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Action: Manage Day (only if hasTransactions)
                if (hasTransactions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onManageDay() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Xem chi tiết các giao dịch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Xóa, sửa giao dịch của ngày $dateLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ĐÓNG")
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAddTransactionDialog(
    initialTimestamp: Long,
    categoriesList: List<FinanceCategory>,
    walletsList: List<Wallet>,
    onDismiss: () -> Unit,
    onSave: (walletId: Int, type: String, amount: Double, categoryName: String, categoryIcon: String, categoryColor: String, note: String, timestamp: Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME
    var selectedCategoryName by remember { mutableStateOf(categoriesList.firstOrNull { it.type == "EXPENSE" || it.type == "BOTH" }?.name ?: "") }
    var selectedWalletId by remember { mutableStateOf<Int?>(walletsList.firstOrNull()?.id) }
    var noteText by remember { mutableStateOf("") }
    
    // Dropdowns visibility
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var walletDropdownExpanded by remember { mutableStateOf(false) }
    
    // Filter categories depending on selectedType. Only leaf categories (no children) are shown.
    val filteredCategories = remember(categoriesList, selectedType) {
        val typeFiltered = categoriesList.filter { it.type == selectedType || it.type == "BOTH" }
        val parentNames = categoriesList.mapNotNull { it.parentName }.toSet()
        typeFiltered.filter { it.name !in parentNames }
    }
    
    // Ensure the category chosen is kept valid
    LaunchedEffect(selectedType) {
        val hasCategory = filteredCategories.any { it.name == selectedCategoryName }
        if (!hasCategory && filteredCategories.isNotEmpty()) {
            selectedCategoryName = filteredCategories.first().name
        }
    }

    val dateTimeFormatter = remember { SimpleDateFormat("dd/MM/yyyy", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()) }
    val dateLabel = dateTimeFormatter.format(initialTimestamp)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Thêm Giao Dịch Nhanh",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ngày: $dateLabel",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Add")
                    }
                }

                // 1. Transaction Type (Income / Expense Selector)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "EXPENSE" to "Khoản Chi",
                        "INCOME" to "Khoản Thu"
                    ).forEach { (typeVal, label) ->
                        val isSelected = selectedType == typeVal
                        val targetBgColor = if (isSelected) {
                            if (typeVal == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                        val targetContentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                        Button(
                            onClick = { selectedType = typeVal },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = targetBgColor,
                                contentColor = targetContentColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 2. Amount Input field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Số tiền *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    val expressionValue = FormatHelper.evaluateExpression(amountText)
                    if (expressionValue > 0.0) {
                        Text(
                            text = "= " + FormatHelper.formatVND(expressionValue),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedType == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // 3. Note Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Ghi chú",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Mô tả chi tiết giao dịch...") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Category Selector Dropdown Button
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Danh mục *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentCategoryObject = categoriesList.find { it.name == selectedCategoryName }
                        
                        OutlinedTextField(
                            value = selectedCategoryName,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = currentCategoryObject?.let { cat ->
                                {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(cat.iconName),
                                        contentDescription = cat.name,
                                        tint = FormatHelper.parseColor(cat.colorHex),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { categoryDropdownExpanded = !categoryDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            filteredCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.getIconByName(cat.iconName),
                                                contentDescription = cat.name,
                                                tint = FormatHelper.parseColor(cat.colorHex),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(cat.name, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedCategoryName = cat.name
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 5. Wallet Dropdown Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Chọn tài khoản / Ví *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentWalletObject = walletsList.find { it.id == selectedWalletId }
                        
                        OutlinedTextField(
                            value = currentWalletObject?.name ?: "Chưa chọn tài khoản",
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = currentWalletObject?.let { w ->
                                {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(w.iconName),
                                        contentDescription = w.name,
                                        tint = FormatHelper.parseColor(w.colorHex),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { walletDropdownExpanded = !walletDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { walletDropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = walletDropdownExpanded,
                            onDismissRequest = { walletDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            walletsList.forEach { w ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.getIconByName(w.iconName),
                                                contentDescription = w.name,
                                                tint = FormatHelper.parseColor(w.colorHex),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(w.name, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedWalletId = w.id
                                        walletDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("HỦY BỎ")
                    }

                    val finalAmount = FormatHelper.evaluateExpression(amountText)
                    Button(
                        onClick = {
                            val targetCat = categoriesList.find { it.name == selectedCategoryName } ?: categoriesList.first()
                            onSave(
                                selectedWalletId ?: walletsList.first().id,
                                selectedType,
                                finalAmount,
                                targetCat.name,
                                targetCat.iconName,
                                targetCat.colorHex,
                                noteText.ifEmpty { targetCat.name },
                                initialTimestamp
                            )
                        },
                        enabled = finalAmount > 0.0 && selectedWalletId != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                    ) {
                        Text("THÊM", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatCompactVND(amount: Double): String {
    if (amount <= 0.0) return ""
    return when {
        amount >= 1_000_000_000 -> {
            val bnVal = amount / 1_000_000_000.0
            val formatted = if (bnVal % 1.0 == 0.0) "%.0f".format(Locale.US, bnVal) else "%.1f".format(Locale.US, bnVal)
            "${formatted}B"
        }
        amount >= 1_000_000 -> {
            val mVal = amount / 1_000_000.0
            val formatted = if (mVal % 1.0 == 0.0) "%.0f".format(Locale.US, mVal) else "%.1f".format(Locale.US, mVal)
            "${formatted}tr"
        }
        amount >= 1_000 -> {
            val kVal = amount / 1_000.0
            val formatted = if (kVal % 1.0 == 0.0) "%.0f".format(Locale.US, kVal) else "%.1f".format(Locale.US, kVal)
            "${formatted}k"
        }
        else -> "%.0f".format(Locale.US, amount)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSummaryBottomSheet(
    filteredTransactions: List<Transaction>,
    walletsList: List<Wallet>,
    filterSummary: String,
    searchQuery: String,
    selectedTypeFilter: String,
    selectedCategoryFilter: String,
    activeTimeFilterMode: String,
    selectedRangeStr: String,
    onDismissRequest: () -> Unit
) {
    val savingsWalletIds = remember(walletsList) {
        walletsList.filter { it.type == "SAVINGS" }.map { it.id }.toSet()
    }
    val financialSummary = remember(filteredTransactions, savingsWalletIds) {
        com.app.ui.calculateRealFinancialSummary(filteredTransactions, savingsWalletIds)
    }
    val totalIncome = financialSummary.realIncome
    val totalExpense = financialSummary.realExpense

    val categoryStats = remember(filteredTransactions) {
        filteredTransactions.filter { it.type != "TRANSFER" }
            .groupBy { Triple(it.categoryName, it.categoryIcon, it.categoryColor) }
            .map { (cat, txs) ->
                val income = txs.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expense = txs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                Triple(cat, income, expense)
            }
            .filter { it.second > 0 || it.third > 0 }
            .sortedByDescending { it.second + it.third }
    }

    val totalTransfer = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == "TRANSFER" }.sumOf { it.amount }
    }
    val hasTransfer = totalTransfer > 0.0
    val hasIncome = categoryStats.any { it.second > 0 } || hasTransfer
    val hasExpense = categoryStats.any { it.third > 0 } || hasTransfer

    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        title = "TỔNG HỢP KẾT QUẢ",
        footer = {
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ĐÓNG", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Filter Criteria Tags (Các yếu tố lọc)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TIÊU CHÍ BỘ LỌC ĐANG ÁP DỤNG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time filter tag
                    AssistChip(
                        onClick = {},
                        label = { Text(selectedRangeStr, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )

                    // Type filter tag
                    if (selectedTypeFilter != "ALL") {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (selectedTypeFilter == "EXPENSE") "Khoản Chi" else "Khoản Thu", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (selectedTypeFilter == "EXPENSE") Icons.Default.North else Icons.Default.South,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedTypeFilter == "EXPENSE") Color(0xFFDC2626) else Color(0xFF16A34A)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = (if (selectedTypeFilter == "EXPENSE") Color(0xFFFEE2E2) else Color(0xFFDCFCE7)).copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )
                    }

                    // Category filter tag
                    if (selectedCategoryFilter != "ALL") {
                        AssistChip(
                            onClick = {},
                            label = { Text("Hạng mục: $selectedCategoryFilter", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )
                    }

                    // Search query tag
                    if (searchQuery.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("\"$searchQuery\"", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )
                    }

                    // Transaction count tag
                    AssistChip(
                        onClick = {},
                        label = { Text("${filteredTransactions.size} giao dịch", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )
                }
            }

            // 2. Summary Cards (2 thẻ Tổng thu & Tổng chi - thiết kế chuẩn Ảnh 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Thẻ Tổng thu
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.South,
                                contentDescription = "Tổng thu",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Tổng thu",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (totalIncome > 0) "+${FormatHelper.formatVND(totalIncome)}" else "0 đ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Thẻ Tổng chi
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF5F5),
                    border = BorderStroke(1.dp, Color(0xFFFEE2E2))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.North,
                                contentDescription = "Tổng chi",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Tổng chi",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (totalExpense > 0) "-${FormatHelper.formatVND(totalExpense)}" else "0 đ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Category Breakdown Table (Thiết kế Bảng thu chi theo danh mục - chuẩn Ảnh 2)
            if (categoryStats.isNotEmpty() || hasTransfer) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "BẢNG THU CHI THEO HẠNG MỤC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        // Table Column Headers Row (Thu / Chi)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Hạng mục", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }

                            if (hasIncome) {
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Thu", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (hasExpense) {
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Chi", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        HorizontalDivider(color = borderColor)

                        // Category Rows
                        categoryStats.forEachIndexed { index, stat ->
                            val (cat, income, expense) = stat
                            val rowBgColor = try {
                                FormatHelper.parseColor(cat.third).copy(alpha = 0.08f)
                            } catch (e: Exception) {
                                Color.Transparent
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(rowBgColor),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Col
                                Row(
                                    modifier = Modifier.weight(1.1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(FormatHelper.parseColor(cat.third)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.getIconByName(cat.second),
                                            contentDescription = cat.first,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = cat.first,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (hasIncome) {
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (income > 0) {
                                            Text(
                                                text = FormatHelper.formatVND(income),
                                                fontSize = 12.sp,
                                                color = Color(0xFF16A34A),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (hasExpense) {
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (expense > 0) {
                                            Text(
                                                text = FormatHelper.formatVND(expense),
                                                fontSize = 12.sp,
                                                color = Color(0xFFDC2626),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            if (index < categoryStats.size - 1 || hasTransfer) {
                                HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
                            }
                        }

                        // Transfer Row (if any)
                        if (hasTransfer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(Color(0xFF2196F3).copy(alpha = 0.08f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1.1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2196F3)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                            contentDescription = "Nội bộ",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "Nội bộ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (hasIncome) {
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = "±${FormatHelper.formatVND(totalTransfer)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF2196F3),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (hasExpense) {
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = "±${FormatHelper.formatVND(totalTransfer)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF2196F3),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không có dữ liệu giao dịch trong bộ lọc",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
