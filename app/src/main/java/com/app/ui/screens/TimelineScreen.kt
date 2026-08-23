package com.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.data.Transaction
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.IconMapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    initialDateStr: String
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    var currentDateStr by rememberSaveable(initialDateStr) { mutableStateOf(initialDateStr) }
    val context = LocalContext.current

    val currentCal = remember(currentDateStr) {
        val cal = Calendar.getInstance()
        try {
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(currentDateStr)
            if (date != null) cal.time = date
        } catch (e: Exception) {
            // fallback
        }
        cal
    }

    val onPreviousDay: () -> Unit = {
        val cal = Calendar.getInstance().apply { timeInMillis = currentCal.timeInMillis }
        cal.add(Calendar.DAY_OF_MONTH, -1)
        currentDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
    }

    val onNextDay: () -> Unit = {
        val cal = Calendar.getInstance().apply { timeInMillis = currentCal.timeInMillis }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        currentDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
    }

    val onSelectDate: () -> Unit = {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                currentDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(newCal.time)
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    // Filter for the specific day
    val dayTransactions = remember(allTransactions, currentDateStr) {
        allTransactions
            .filter { FormatHelper.formatDate(it.timestamp) == currentDateStr }
            .sortedByDescending { it.timestamp }
    }
    
    val savingsWallets by viewModel.savingsWallets.collectAsState()
    val savingsWalletIds = remember(savingsWallets) { savingsWallets.map { it.id }.toSet() }
    val daySummary = remember(dayTransactions, savingsWalletIds) {
        com.app.ui.calculateRealFinancialSummary(dayTransactions, savingsWalletIds)
    }
    val totalIncome = daySummary.realIncome
    val totalExpense = daySummary.realExpense

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val seenKeys = remember(currentDateStr) { mutableSetOf<String>() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp, top = 8.dp)
        ) {
            item(key = "header_$currentDateStr") {
                Box(modifier = Modifier.staggeredEntrance(0, "header", seenKeys)) {
                    TimelineDayHeaderCards(
                        dateStr = currentDateStr,
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        onPreviousDay = onPreviousDay,
                        onNextDay = onNextDay,
                        onSelectDate = onSelectDate
                    )
                }
                if (dayTransactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.staggeredEntrance(1, "summary_table", seenKeys)) {
                        TimelineDailySummaryTable(dayTransactions = dayTransactions)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (dayTransactions.isEmpty()) {
                item(key = "empty_$currentDateStr") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Chưa có giao dịch trong ngày $currentDateStr",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(dayTransactions, key = { index, tx -> "${tx.id}_${tx.timestamp}_$index" }) { index, tx -> 
                    Box(modifier = Modifier.staggeredEntrance(2 + index, "tx_${tx.id}_${tx.timestamp}_$index", seenKeys)) {
                        TimelineTransactionUpdated(tx = tx, isLast = index == dayTransactions.size - 1)
                    }
                }

                item(key = "footer_$currentDateStr") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(2 + dayTransactions.size, "footer", seenKeys)
                            .padding(top = 16.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 1.dp
                            )
                            Text(
                                text = "Hết",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineDayHeaderUpdated(dateStr: String, totalIncome: Double, totalExpense: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Date and Expense (Chi)
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = dateStr,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (totalExpense > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "-${FormatHelper.formatVND(totalExpense)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
            }
        }

        // Right Column: Income (Thu)
        Column(horizontalAlignment = Alignment.End) {
            if (totalIncome > 0) {
                Text(
                    text = "+${FormatHelper.formatVND(totalIncome)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun TimelineTransactionUpdated(tx: Transaction, isLast: Boolean) {
    val isAdjustmentDecrease = tx.type == "ADJUSTMENT" && !tx.note.contains("tăng")
    val isExpense = tx.type == "EXPENSE" || isAdjustmentDecrease
    val isTransfer = tx.type == "TRANSFER"
    val amountColor = if (isExpense) Color(0xFFF44336) else if (isTransfer) Color(0xFF2196F3) else Color(0xFF4CAF50)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(IntrinsicSize.Min)
    ) {
        // Timeline graphic
        Box(modifier = Modifier.width(48.dp)) {
            // Line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp) // Start slightly below the center of the circle
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
            // Circle with Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isTransfer) Color(0xFF2196F3) else FormatHelper.parseColor(tx.categoryColor))
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                 Icon(
                     imageVector = if (isTransfer) Icons.AutoMirrored.Filled.CompareArrows else IconMapper.getIconByName(tx.categoryIcon),
                     contentDescription = if (isTransfer) "Nội bộ" else tx.categoryName,
                     tint = Color.White,
                     modifier = Modifier.size(20.dp)
                 )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 32.dp) // Spacing between items
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTransfer) "Nội bộ" else tx.categoryName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (tx.note.isNotBlank()) "${tx.walletName} - ${tx.note}" else tx.walletName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isExpense) "-" else if (isTransfer) "±" else "+") + FormatHelper.formatVND(tx.amount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatHelper.formatTime(tx.timestamp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineDayHeaderCards(
    dateStr: String,
    totalIncome: Double,
    totalExpense: Double,
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onSelectDate: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Centered Date Pill with Previous and Next Buttons (Hàng chuyển +-1 ngày)
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Previous Day Button (Nhỏ gọn, không dính sát)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE))
                    .clickable(onClick = onPreviousDay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Ngày trước",
                    tint = Color(0xFF6D28D9),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Date Pill (Clickable to open DatePickerDialog)
            Surface(
                shape = CircleShape,
                color = Color(0xFFEDE9FE),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelectDate() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Ngày",
                        tint = Color(0xFF6D28D9),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = dateStr,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6D28D9)
                    )
                }
            }

            // Next Day Button (Nhỏ gọn, không dính sát)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE))
                    .clickable(onClick = onNextDay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ngày sau",
                    tint = Color(0xFF6D28D9),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 2. Summary Cards Row (2 Thẻ Tổng thu & Tổng chi thu nhỏ ~40%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Card: Tổng thu
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.South,
                            contentDescription = "Tổng thu",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Tổng thu",
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = if (totalIncome > 0) "+${FormatHelper.formatVND(totalIncome)}" else "0 đ",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right Card: Tổng chi
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF5F5),
                border = BorderStroke(1.dp, Color(0xFFFEE2E2))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.North,
                            contentDescription = "Tổng chi",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Tổng chi",
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = if (totalExpense > 0) "-${FormatHelper.formatVND(totalExpense)}" else "0 đ",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineDailySummaryTable(dayTransactions: List<Transaction>) {
    val categoryStats = remember(dayTransactions) {
        dayTransactions.filter { it.type != "TRANSFER" }
            .groupBy { Triple(it.categoryName, it.categoryIcon, it.categoryColor) }
            .map { (cat, txs) ->
                val income = txs.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expense = txs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                Triple(cat, income, expense)
            }
            .filter { it.second > 0 || it.third > 0 }
    }

    val totalTransfer = remember(dayTransactions) {
        dayTransactions.filter { it.type == "TRANSFER" }.sumOf { it.amount }
    }

    val hasTransfer = totalTransfer > 0.0
    val hasIncome = categoryStats.any { it.second > 0 } || hasTransfer
    val hasExpense = categoryStats.any { it.third > 0 } || hasTransfer

    if (categoryStats.isEmpty() && !hasTransfer) return

    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Table Column Headers Row (Thu / Chi)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(0.9f).fillMaxHeight().padding(8.dp)) 

            if (hasIncome) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Thu", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (hasExpense) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Chi", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        HorizontalDivider(color = borderColor)

        // Category Breakdown Rows
        categoryStats.forEachIndexed { index, stat ->
            val (cat, income, expense) = stat
            val rowBgColor = try {
                FormatHelper.parseColor(cat.third).copy(alpha = 0.12f)
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
                    modifier = Modifier.weight(0.9f).fillMaxHeight().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
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
                    // Income Col
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (income > 0) {
                            Text(
                                text = FormatHelper.formatVND(income),
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (hasExpense) {
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                    // Expense Col
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (expense > 0) {
                            Text(
                                text = FormatHelper.formatVND(expense),
                                fontSize = 12.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            if (index < categoryStats.size - 1 || hasTransfer) {
                HorizontalDivider(color = borderColor)
            }
        }

        // Internal Transfer Row (Nội bộ)
        if (hasTransfer) {
            val transferBgColor = Color(0xFF2196F3).copy(alpha = 0.1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(transferBgColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Col (Nội bộ)
                Row(
                    modifier = Modifier.weight(0.9f).fillMaxHeight().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2196F3)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Chuyển khoản nội bộ",
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

                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(borderColor))
                
                // Merged Cell spanning both Thu and Chi
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
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

@Composable
private fun Modifier.staggeredEntrance(
    index: Int,
    key: String,
    seenKeys: MutableSet<String>
): Modifier {
    val alreadySeen = remember(key) { seenKeys.contains(key) }
    var isVisibleOnScreen by remember { mutableStateOf(alreadySeen) }
    var skipAnimation by remember { mutableStateOf(alreadySeen) }

    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisibleOnScreen) 1f else 0f,
        animationSpec = if (skipAnimation) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(
            durationMillis = 380,
            delayMillis = (index * 45).coerceAtMost(180),
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "timeline_staggered_$index"
    )

    val density = LocalDensity.current
    val offsetYPx = remember(density) { with(density) { 35.dp.toPx() } }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    return this
        .onGloballyPositioned { coordinates ->
            if (!isVisibleOnScreen) {
                val y = coordinates.positionInRoot().y
                if (y < screenHeightPx - 20f) {
                    if (y <= 0) skipAnimation = true
                    seenKeys.add(key)
                    isVisibleOnScreen = true
                }
            }
        }
        .fillMaxWidth()
        .graphicsLayer {
            alpha = progress
            translationY = if (skipAnimation) 0f else (1f - progress) * offsetYPx
        }
}
