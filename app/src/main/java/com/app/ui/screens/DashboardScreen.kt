package com.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.grid.items
import com.app.data.Transaction
import com.app.data.Wallet
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.IconMapper
import com.app.ui.components.StripedProgressIndicator
import java.util.Calendar
import android.os.Build.VERSION.SDK_INT
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.app.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToWallets: (Wallet?) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToSavings: () -> Unit = {},
    onNavigateToBankNotifications: () -> Unit = {},
    onNavigateToDebtBook: (Int) -> Unit = {},
    onNavigateToAIAdvisor: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val wallets by viewModel.dailyWallets.collectAsState()
    val transactions by viewModel.dailyTransactions.collectAsState()
    val savingsWallets by viewModel.savingsWallets.collectAsState()
    val budgets by viewModel.allBudgets.collectAsState()
    val events by viewModel.allEvents.collectAsState()
    val debts by viewModel.allDebts.collectAsState()
    var eventToView by remember { mutableStateOf<com.app.data.Event?>(null) }
    val context = LocalContext.current
    val bellGifImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val currentRealMonthStr = remember {
        Calendar.getInstance().let { cal ->
            String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
    }
    val currentMonthBudgets = remember(budgets, currentRealMonthStr) {
        budgets.filter { it.month == currentRealMonthStr }
    }
    val savingsGoals by viewModel.allSavingsGoals.collectAsState()

    val totalBalance = wallets.sumOf { it.balance }

    // Filter current month transactions for stats - optimized to reuse a single Calendar instance
    val currentMonthTransactions = remember(transactions, currentRealMonthStr) {
        val cal = Calendar.getInstance()
        transactions.filter {
            cal.timeInMillis = it.timestamp
            val txMonth = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            txMonth == currentRealMonthStr
        }
    }

    val savingsWalletIds = remember(savingsWallets) { savingsWallets.map { it.id }.toSet() }
    val financialSummary = remember(currentMonthTransactions, savingsWalletIds) {
        com.app.ui.calculateRealFinancialSummary(currentMonthTransactions, savingsWalletIds)
    }
    val totalIncome = financialSummary.realIncome
    val totalExpense = financialSummary.realExpense
    val totalNetSavings = financialSummary.netSavings

    var isBalanceVisible by remember { mutableStateOf(true) }
    val incomeCount = remember(currentMonthTransactions) { currentMonthTransactions.count { it.type == "INCOME" } }
    val expenseCount = remember(currentMonthTransactions) { currentMonthTransactions.count { it.type == "EXPENSE" } }

    val displayMonth = remember(currentRealMonthStr) {
        if (currentRealMonthStr.length >= 7) {
            "Tháng ${currentRealMonthStr.substring(5)}/${currentRealMonthStr.substring(0, 4)}"
        } else {
            currentRealMonthStr
        }
    }

    val displayMonthShort = remember(currentRealMonthStr) {
        if (currentRealMonthStr.length >= 7) {
            try {
                "T${currentRealMonthStr.substring(5).toInt()}"
            } catch (e: Exception) {
                "T${currentRealMonthStr.substring(5)}"
            }
        } else {
            currentRealMonthStr
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- Total Balance Section ---
        Card(
            modifier = Modifier
                .staggeredEntrance(0)
                .clickable { onNavigateToStats() }
                .testTag("total_balance_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4564ED)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF4A68F0), Color(0xFF3B54DC))
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Tổng số dư khả dụng",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Icon(
                                imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Ẩn/Hiện số dư",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { isBalanceVisible = !isBalanceVisible }
                            )
                        }

                        // Right Badge (Trending Icon)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = "Báo cáo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Balance Display
                    if (isBalanceVisible) {
                        FormattedVndText(
                            amount = totalBalance,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "•••••••• đ",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom Income and Expense Breakdown
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thu nhập
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Income",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Thu nhập ($incomeCount)",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                FormattedVndText(
                                    amount = totalIncome,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                            }
                        }

                        // Vertical Divider
                        VerticalDivider(
                            color = Color.White.copy(alpha = 0.25f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        // Chi tiêu
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Expense",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Chi tiêu ($expenseCount)",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                FormattedVndText(
                                    amount = totalExpense,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Pending Notifications Banner Alert ---
        val notificationLogs by viewModel.notificationLogs.collectAsState()
        val pendingCount = remember(notificationLogs) { notificationLogs.count { it.status == "PENDING" } }
        if (pendingCount > 0) {
            Card(
                modifier = Modifier
                    .staggeredEntrance(1)
                    .clickable { onNavigateToBankNotifications() }
                    .testTag("dashboard_pending_notifications_banner"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFE0B2), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.notification_bell)
                                .build(),
                            imageLoader = bellGifImageLoader,
                            contentDescription = "Pending Notifications",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Có giao dịch mới!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Phát hiện $pendingCount giao dịch mới.",
                            fontSize = 12.sp,
                            color = Color(0xFF5D4037)
                        )
                    }
                    Button(
                        onClick = onNavigateToBankNotifications,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Duyệt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- Active Debts Alert Banner ---
        val allDebts by viewModel.allDebts.collectAsState()
        val activeDebtsList = remember(allDebts) { allDebts.filter { it.status == "ACTIVE" && it.remainingAmount > 0 } }
        if (activeDebtsList.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val debtItems = remember(activeDebtsList) { activeDebtsList.filter { it.type == "DEBT" } }
            val loanItems = remember(activeDebtsList) { activeDebtsList.filter { it.type == "LOAN" } }
            
            Row(
                modifier = Modifier
                    .staggeredEntrance(2)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // KHOẢN NỢ CARD (Left)
                if (debtItems.isNotEmpty()) {
                    val count = debtItems.size
                    val overdueCount = debtItems.count { it.dueDate != null && it.dueDate < now }
                    val nearestDays = debtItems
                        .filter { it.dueDate != null && it.dueDate >= now }
                        .minOfOrNull { Math.max(0, ((it.dueDate!! - now) / (1000 * 60 * 60 * 24)).toInt()) }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onNavigateToDebtBook(0) }
                            .testTag("dashboard_debt_summary_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F7)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFEBEE))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFFFFEBEE), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = Color(0xFFE53935),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Nợ",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE53935)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFFE53935), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "$count khoản nợ phải trả",
                                    fontSize = 13.sp,
                                    color = Color(0xFF4A5568)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (nearestDays != null) "Gần nhất: sau $nearestDays ngày" else "Gần nhất: Chưa chọn ngày",
                                        fontSize = 13.sp,
                                        color = Color(0xFF4A5568)
                                    )
                                }
                            }

                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFFFEBEE), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFEBEE),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(R.drawable.notification_bell)
                                                .build(),
                                            imageLoader = bellGifImageLoader,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Quá hạn: $overdueCount khoản",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE53935)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // KHOẢN CHO VAY CARD (Right)
                if (loanItems.isNotEmpty()) {
                    val count = loanItems.size
                    val overdueCount = loanItems.count { it.dueDate != null && it.dueDate < now }
                    val nearestDays = loanItems
                        .filter { it.dueDate != null && it.dueDate >= now }
                        .minOfOrNull { Math.max(0, ((it.dueDate!! - now) / (1000 * 60 * 60 * 24)).toInt()) }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onNavigateToDebtBook(1) }
                            .testTag("dashboard_loan_summary_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FF)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE3F2FD))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFFE3F2FD), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = Color(0xFF1E88E5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Vay",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E88E5)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFF1E88E5), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "$count khoản chưa thu",
                                    fontSize = 13.sp,
                                    color = Color(0xFF4A5568)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (nearestDays != null) "Gần nhất: sau $nearestDays ngày" else "Gần nhất: Chưa chọn ngày",
                                        fontSize = 13.sp,
                                        color = Color(0xFF4A5568)
                                    )
                                }
                            }

                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFE3F2FD), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE3F2FD),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Color(0xFF1E88E5),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Quá hạn: $overdueCount khoản",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E88E5)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Events Widget ---
        val nowMs = System.currentTimeMillis()
        val activeOrUpcomingEvents = remember(events, nowMs) {
            events
                .filter { event ->
                    event.isActive && (
                        FormatHelper.isEventOngoing(event.startDate, event.endDate, nowMs) ||
                        (FormatHelper.isEventUpcoming(event.startDate, nowMs) && FormatHelper.getDaysDifference(nowMs, event.startDate) <= 3)
                    )
                }
                .sortedWith(
                    Comparator { e1, e2 ->
                        val isOngoing1 = FormatHelper.isEventOngoing(e1.startDate, e1.endDate, nowMs)
                        val isOngoing2 = FormatHelper.isEventOngoing(e2.startDate, e2.endDate, nowMs)

                        when {
                            // 1. Ongoing events always take precedence over upcoming events
                            isOngoing1 && !isOngoing2 -> -1
                            !isOngoing1 && isOngoing2 -> 1
                            
                            // 2. Both Ongoing: sort by endDate ascending (events ending soonest appear first)
                            isOngoing1 && isOngoing2 -> {
                                val end1 = e1.endDate?.let { FormatHelper.getEndOfDay(it) } ?: Long.MAX_VALUE
                                val end2 = e2.endDate?.let { FormatHelper.getEndOfDay(it) } ?: Long.MAX_VALUE
                                end1.compareTo(end2)
                            }

                            // 3. Both Upcoming: sort by startDate ascending (events starting soonest appear first)
                            else -> {
                                val start1 = FormatHelper.getStartOfDay(e1.startDate)
                                val start2 = FormatHelper.getStartOfDay(e2.startDate)
                                start1.compareTo(start2)
                            }
                        }
                    }
                )
        }

        if (activeOrUpcomingEvents.isNotEmpty()) {
            Column(
                modifier = Modifier.staggeredEntrance(3),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sự kiện",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Xem tất cả",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5C79FF),
                        modifier = Modifier.clickable { onNavigateToEvents() }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    activeOrUpcomingEvents.take(2).forEachIndexed { eventIdx, event ->
                        val eventTransactions = viewModel.allTransactions.collectAsState().value.filter { it.eventId == event.id }
                        val spentAmount = eventTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                        val limitAmount = event.limitAmount ?: 0.0

                        DashboardEventCard(
                            eventIdx = eventIdx,
                            event = event,
                            spentAmount = spentAmount,
                            limitAmount = limitAmount,
                            nowMs = nowMs,
                            onClick = { eventToView = event },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- Wallets Section ---
        val walletUsageMap = remember(transactions) {
            transactions.groupingBy { it.walletId }.eachCount()
        }
        val sortedWallets = remember(wallets, walletUsageMap) {
            wallets.sortedWith(
                compareByDescending<Wallet> { walletUsageMap[it.id] ?: 0 }
                    .thenByDescending { it.balance }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.staggeredEntrance(4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tài khoản của tôi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Xem tất cả",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5C79FF),
                    modifier = Modifier
                        .clickable { onNavigateToWallets(null) }
                        .testTag("view_all_wallets_button")
                )
            }

            if (sortedWallets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không có ví nào. Thêm ví mới!",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sortedWallets.forEachIndexed { walletIdx, wallet ->
                        val walletColor = try {
                            FormatHelper.parseColor(wallet.colorHex)
                        } catch (e: Exception) {
                            Color(0xFF5C79FF)
                        }

                        val subtitleText = when (wallet.type.uppercase()) {
                            "CASH" -> "Tiền mặt"
                            "BANK" -> "Tài khoản ngân hàng"
                            "WALLET" -> "Ví điện tử"
                            "SAVINGS" -> "Tiết kiệm"
                            "CREDIT" -> "Thẻ tín dụng"
                            else -> wallet.type
                        }

                        Card(
                            modifier = Modifier
                                .staggeredEntrance(4 + walletIdx + 1)
                                .clickable { onNavigateToWallets(wallet) }
                                .testTag("dashboard_wallet_card_${wallet.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(walletColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.getIconByName(wallet.iconName),
                                            contentDescription = wallet.name,
                                            tint = walletColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = wallet.name,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = subtitleText,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Số dư",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        FormattedVndText(
                                            amount = wallet.balance,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Chi tiết ví",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.staggeredEntrance(5),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Visual Gauge Visual Component ---
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToStats() }
                    .testTag("stats_donut_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.drawable.growth_chart)
                            .build(),
                        imageLoader = bellGifImageLoader,
                        contentDescription = "Biểu đồ",
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Biểu đồ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }
            }

            // --- Kho Tiết Kiệm Component ---
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToSavings() }
                    .testTag("dashboard_savings_vault_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(R.drawable.save_money)
                            .build(),
                        imageLoader = bellGifImageLoader,
                        contentDescription = "Kho tiết kiệm",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Kho\ntiết kiệm",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // --- Budget Goals Shortcut ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToBudget() }
                .testTag("dashboard_budget_shortcut_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = "Budget",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hạn mức chi tiêu",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Detail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentMonthBudgets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        currentMonthBudgets.forEachIndexed { budgetIdx, budget ->
                            val isExceeded = budget.spentAmount >= budget.limitAmount
                            val progressColor = if (isExceeded) MaterialTheme.colorScheme.error else FormatHelper.parseColor(budget.categoryColor)

                            DashboardBudgetItem(
                                budgetIdx = budgetIdx,
                                categoryName = budget.categoryName,
                                spentAmount = budget.spentAmount,
                                limitAmount = budget.limitAmount,
                                progressColor = progressColor,
                                isExceeded = isExceeded
                            )
                        }
                    }
                }
            }
        }

        // --- Smart Spending Analysis Component ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_smart_insights_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header of Insights
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(0),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Insights,
                                contentDescription = "Trợ lý thông minh",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Phân tích chi tiêu thông minh",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AI Trợ Lý",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Mini Trend Chart / Progress comparing This Week vs Last Week
                val expenses = remember(transactions) { transactions.filter { it.type == "EXPENSE" } }
                val anchorTime = remember(transactions) { System.currentTimeMillis() }
                val MS_IN_DAY = 24 * 60 * 60 * 1000L

                val thisWeekTotal = remember(expenses, anchorTime) {
                    expenses.filter { anchorTime - it.timestamp <= 7 * MS_IN_DAY }.sumOf { it.amount }
                }
                val lastWeekTotal = remember(expenses, anchorTime) {
                    expenses.filter {
                        val diff = anchorTime - it.timestamp
                        diff > 7 * MS_IN_DAY && diff <= 14 * MS_IN_DAY
                    }.sumOf { it.amount }
                }

                if (expenses.isNotEmpty() && (thisWeekTotal > 0.0 || lastWeekTotal > 0.0)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(1)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "So sánh xu hướng chi tiêu tuần này",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Dual Bar visualization
                        val maxTotal = Math.max(thisWeekTotal, lastWeekTotal)
                        val targetThisWeekRatio = if (maxTotal > 0) (thisWeekTotal / maxTotal).toFloat() else 0f
                        val targetLastWeekRatio = if (maxTotal > 0) (lastWeekTotal / maxTotal).toFloat() else 0f

                        var isTrendVisible by remember { mutableStateOf(false) }
                        var skipTrendAnimation by remember { mutableStateOf(false) }
                        val animLastWeekRatio by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isTrendVisible) targetLastWeekRatio.coerceIn(0.01f, 1f) else 0f,
                            animationSpec = if (skipTrendAnimation) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "animLastWeekRatio"
                        )
                        val animThisWeekRatio by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isTrendVisible) targetThisWeekRatio.coerceIn(0.01f, 1f) else 0f,
                            animationSpec = if (skipTrendAnimation) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "animThisWeekRatio"
                        )

                        val densityTrend = androidx.compose.ui.platform.LocalDensity.current
                        val screenHeightPxTrend = with(densityTrend) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

                        Column(
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    if (!isTrendVisible) {
                                        val y = coordinates.positionInRoot().y
                                        if (y < screenHeightPxTrend - 20f) {
                                            if (y <= 0) skipTrendAnimation = true
                                            isTrendVisible = true
                                        }
                                    }
                                },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Last week
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tuần trước",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = FormatHelper.formatVND(lastWeekTotal),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animLastWeekRatio.coerceIn(0.001f, 1f))
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), CircleShape)
                                    )
                                }
                            }

                            // This week
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tuần này",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = FormatHelper.formatVND(thisWeekTotal),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (thisWeekTotal > lastWeekTotal) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animThisWeekRatio.coerceIn(0.001f, 1f))
                                            .fillMaxHeight()
                                            .background(
                                                if (thisWeekTotal > lastWeekTotal) MaterialTheme.colorScheme.error
                                                else Color(0xFF4CAF50),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // Insights section list
                val aiAdvisorData = remember(transactions, currentMonthTransactions, wallets, savingsWallets, currentMonthBudgets, savingsGoals, events, debts, totalIncome, totalExpense) {
                    val riskAlerts = mutableListOf<SmartSpendingInsight>()
                    val recommendations = mutableListOf<SmartSpendingInsight>()
                    val evaluations = mutableListOf<SmartSpendingInsight>()

                    val now = System.currentTimeMillis()
                    val oneDay = 24L * 60L * 60L * 1000L
                    
                    val cal = Calendar.getInstance()
                    val daysElapsed = cal.get(Calendar.DAY_OF_MONTH)
                    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val remainingDays = maxDays - daysElapsed + 1
                    val daysElapsedSafe = if (daysElapsed > 0) daysElapsed.toDouble() else 1.0

                    val availableCash = wallets.filter { it.type != "CREDIT" && it.type != "SAVINGS" && !it.isClosed }.sumOf { it.balance }

                    // --- 1. RISK ALERTS (Tầng 1: Chuẩn đoán Rủi ro) ---
                    // Rủi ro 1: Thiếu hụt dòng tiền trả nợ gối đầu trong 7 ngày tới
                    val activeDebtsDue7Days = debts.filter { 
                        it.type == "DEBT" && it.status == "ACTIVE" && it.dueDate != null && it.dueDate!! >= now && (it.dueDate!! - now) <= 7 * oneDay 
                    }
                    val totalDebtDue7Days = activeDebtsDue7Days.sumOf { it.remainingAmount }
                    if (totalDebtDue7Days > 0 && availableCash < totalDebtDue7Days) {
                        riskAlerts.add(
                            SmartSpendingInsight(
                                title = "🚨 Nguy cơ thiếu tiền trả nợ!",
                                description = "Trong 7 ngày tới có khoản nợ ${FormatHelper.formatVND(totalDebtDue7Days)} sắp đến hạn, nhưng tiền khả dụng chỉ còn ${FormatHelper.formatVND(availableCash)}. Hãy chuẩn bị nguồn tiền!",
                                icon = Icons.Default.Warning,
                                tint = Color(0xFFD32F2F)
                            )
                        )
                    }

                    // Rủi ro 2: Sự kiện vượt ngân sách (Active Event Risk)
                    val activeEvents = events.filter { 
                        it.isActive && FormatHelper.isEventOngoing(it.startDate, it.endDate, now) && (it.limitAmount ?: 0.0) > 0.0 
                    }
                    activeEvents.forEach { ev ->
                        val limit = ev.limitAmount!!
                        val spent = transactions.filter { it.eventId == ev.id && it.type == "EXPENSE" }.sumOf { it.amount }
                        if (spent >= limit) {
                            riskAlerts.add(
                                SmartSpendingInsight(
                                    title = "⚠️ Sự kiện '${ev.name}' vượt ngân sách",
                                    description = "Bạn đã chi ${FormatHelper.formatVND(spent)}, vượt ${FormatHelper.formatVND(spent - limit)} so với hạn mức sự kiện.",
                                    icon = Icons.Default.EventBusy,
                                    tint = Color(0xFFD32F2F)
                                )
                            )
                        } else {
                            val actualStart = FormatHelper.getStartOfDay(ev.startDate)
                            val actualEnd = ev.endDate?.let { FormatHelper.getEndOfDay(it) }
                            val duration = if (actualEnd != null && actualEnd > actualStart) (actualEnd - actualStart).toDouble() else 0.0
                            if (duration > 0) {
                                val timeRatio = ((now - actualStart).toDouble() / duration).coerceIn(0.0, 1.0)
                                val spentRatio = spent / limit
                                if (timeRatio <= 0.5 && spentRatio >= 0.8) {
                                    riskAlerts.add(
                                        SmartSpendingInsight(
                                            title = "⚠️ Sự kiện '${ev.name}' sắp vỡ quỹ!",
                                            description = "Đã tiêu hết ${(spentRatio * 100).toInt()}% hạn mức dù thời gian mới trôi qua ${(timeRatio * 100).toInt()}%. Hãy điều chỉnh nhịp chi tiêu!",
                                            icon = Icons.Default.Report,
                                            tint = Color(0xFFE57373)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Rủi ro 3: Tốc độ cạn kiệt tài khoản (Bộ lọc bảo vệ đầu tháng: daysElapsed >= 5)
                    val monthlyExpense = currentMonthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                    val dailyExpenseRate = monthlyExpense / daysElapsedSafe
                    if (daysElapsed >= 5 && dailyExpenseRate > 0 && availableCash > 0) {
                        val daysToDry = availableCash / dailyExpenseRate
                        if (daysToDry <= 10) {
                            riskAlerts.add(
                                SmartSpendingInsight(
                                    title = "⏳ Kịch bản cạn tiền mặt sau ${daysToDry.toInt()} ngày tới",
                                    description = "Dựa trên thói quen chi ra khoảng ${FormatHelper.formatVND(dailyExpenseRate)}/ngày gần đây, số dư tiền mặt có nguy cơ cạn kiệt trước thời điểm cuối tháng nếu không tiết chế.",
                                    icon = Icons.Default.TrendingDown,
                                    tint = Color(0xFFF44336)
                                )
                            )
                        }
                    }

                    // Rủi ro thói quen & tần suất chi tiêu dồn dập có nguy cơ vỡ quỹ trong tương lai
                    val recent7DaysTxs = currentMonthTransactions.filter { it.type == "EXPENSE" && it.timestamp >= now - 7 * oneDay }
                    val categoryFreq7d = recent7DaysTxs.groupingBy { it.categoryName }.eachCount()
                    val categorySpent7d = recent7DaysTxs.groupingBy { it.categoryName }.fold(0.0) { acc, tx -> acc + tx.amount }

                    currentMonthBudgets.filter { it.limitAmount > 0 && it.spentAmount < it.limitAmount }.forEach { bug ->
                        val freq = categoryFreq7d[bug.categoryName] ?: 0
                        val spent7d = categorySpent7d[bug.categoryName] ?: 0.0
                        if (freq >= 4 && spent7d > 0) {
                            val dailyBurn = spent7d / 7.0
                            val daysToExhaust = (bug.limitAmount - bug.spentAmount) / dailyBurn
                            if (daysToExhaust <= 5 && daysToExhaust <= remainingDays) {
                                riskAlerts.add(
                                    SmartSpendingInsight(
                                        title = "🚨 Nguy cơ vỡ quỹ '${bug.categoryName}' trong ${daysToExhaust.toInt()} ngày tới!",
                                        description = "Với tần suất chi tiêu dồn dập (${freq} lần trong tuần qua) và nhịp độ ${FormatHelper.formatVND(dailyBurn)}/ngày, quỹ '${bug.categoryName}' sẽ bị cạn trước khi hết tháng.",
                                        icon = Icons.Default.TrendingUp,
                                        tint = Color(0xFFE57373)
                                    )
                                )
                            }
                        }
                    }

                    if (riskAlerts.isEmpty()) {
                        riskAlerts.add(
                            SmartSpendingInsight(
                                title = "Chưa phát hiện rủi ro",
                                description = "Tình hình tài chính và dòng tiền hiện tại của bạn vẫn nằm trong ngưỡng an toàn.",
                                icon = Icons.Default.CheckCircle,
                                tint = Color(0xFF4CAF50)
                            )
                        )
                    }

                    // --- 2. RECOMMENDATIONS (Tầng 2: Khuyến nghị Hành động) ---
                    // Khuyến nghị 1: Tiết chế tần suất chi phí phát sinh linh hoạt
                    val discretionaryKeywords = listOf("Ăn uống", "Cà phê", "Mua sắm", "Giải trí", "Di chuyển", "Siêu thị", "Shopping", "Chợ", "Xăng", "Điện thoại", "Quần áo", "Du lịch", "Ăn vặt")
                    val discretionaryTxs = currentMonthTransactions.filter { tx -> tx.type == "EXPENSE" && discretionaryKeywords.any { tx.categoryName.contains(it, ignoreCase = true) } }
                    val discretionaryFreq = discretionaryTxs.groupingBy { it.categoryName }.eachCount()

                    discretionaryFreq.filter { (cat, count) -> count >= 3 }.forEach { (catName, count) ->
                        val bug = currentMonthBudgets.find { it.categoryName == catName }
                        if (bug != null && bug.limitAmount > bug.spentAmount) {
                            val safeDaily = (bug.limitAmount - bug.spentAmount) / remainingDays
                            recommendations.add(
                                SmartSpendingInsight(
                                    title = "💡 Tiết chế các khoản phát sinh '${catName}'",
                                    description = "Hạng mục '${catName}' đang có tần suất phát sinh khá dày (${count} lần trong tháng). Bạn nên giảm bớt các chi phí nhỏ lẻ không thiết yếu để duy trì nhịp chi tiêu dưới ${FormatHelper.formatVND(safeDaily)}/ngày cho đến hết tháng.",
                                    icon = Icons.Default.Lightbulb,
                                    tint = Color(0xFFFFB300)
                                )
                            )
                        } else if (bug == null) {
                            recommendations.add(
                                SmartSpendingInsight(
                                    title = "💡 Kiểm soát tần suất '${catName}'",
                                    description = "Hạng mục '${catName}' đang phát sinh ${count} giao dịch trong tháng này. Hãy cân nhắc đặt hạn mức và giảm bớt các lần chi nhỏ lẻ để tránh lãng phí tiền bạc.",
                                    icon = Icons.Default.Lightbulb,
                                    tint = Color(0xFFFFB300)
                                )
                            )
                        }
                    }

                    // Khuyến nghị 2: Thích ứng với hạng mục vượt mức (Overspending Adaptation)
                    currentMonthBudgets.filter { it.limitAmount > 0 && it.spentAmount >= it.limitAmount }.forEach { exBug ->
                        val excess = exBug.spentAmount - exBug.limitAmount
                        recommendations.add(
                            SmartSpendingInsight(
                                title = "📌 Thích ứng với hạng mục vượt mức '${exBug.categoryName}'",
                                description = "Việc vượt hạn mức '${exBug.categoryName}' (${FormatHelper.formatVND(excess)}) là bình thường nếu do nhu cầu thực tế. Tuy nhiên, từ nay đến cuối tháng, hãy ưu tiên cắt giảm các chi phí phát sinh linh hoạt khác để tổng thể ngân sách tháng không bị ảnh hưởng.",
                                icon = Icons.Default.Info,
                                tint = Color(0xFFFFA000)
                            )
                        )
                    }

                    // Khuyến nghị Hũ tiết kiệm sắp đến hạn trong 30 ngày
                    savingsGoals.filter { it.targetAmount > 0 && it.targetDate >= now && (it.targetDate - now) <= 30 * oneDay && (it.currentAmount / it.targetAmount) < 0.8 }.forEach { sg ->
                        val needed = sg.targetAmount - sg.currentAmount
                        val daysLeft = ((sg.targetDate - now) / oneDay).toInt().coerceAtLeast(1)
                        recommendations.add(
                            SmartSpendingInsight(
                                title = "🎯 Tăng tốc mục tiêu '${sg.name}'",
                                description = "Chỉ còn ${daysLeft} ngày nữa là đến hạn mục tiêu, bạn cần tích lũy thêm ${FormatHelper.formatVND(needed)}. Hãy cố gắng tiết kiệm nhé!",
                                icon = Icons.Default.Lightbulb,
                                tint = Color(0xFF4CAF50)
                            )
                        )
                    }

                    if (recommendations.isEmpty()) {
                        recommendations.add(
                            SmartSpendingInsight(
                                title = "Chưa có khuyến nghị",
                                description = "Hãy tạo thêm Hạn mức chi tiêu hoặc Hũ tiết kiệm để trợ lý AI tính toán lời khuyên định mức tối ưu.",
                                icon = Icons.Default.Lightbulb,
                                tint = Color(0xFF78909C)
                            )
                        )
                    }

                    // --- 3. EVALUATIONS (Tầng 3: Đánh giá Xu hướng) ---
                    val exps = transactions.filter { it.type == "EXPENSE" }
                    if (exps.isNotEmpty()) {
                        val cWeek = exps.filter { now - it.timestamp <= 7 * oneDay }.sumOf { it.amount }
                        val pWeek = exps.filter { 
                            val diff = now - it.timestamp
                            diff > 7 * oneDay && diff <= 14 * oneDay 
                        }.sumOf { it.amount }

                        val goodKeywords = listOf("Trả nợ", "Tiết kiệm", "Đầu tư", "Bảo hiểm", "Học tập")
                        val allCategoryNames = transactions.map { it.categoryName }.distinct()
                        var foundCatEval = false

                        for (catName in allCategoryNames) {
                            val isGoodExpense = goodKeywords.any { catName.contains(it, ignoreCase = true) }
                            val cCatSpent = exps.filter { it.categoryName == catName && now - it.timestamp <= 7 * oneDay }.sumOf { it.amount }
                            val pCatSpent = exps.filter { 
                                it.categoryName == catName && (now - it.timestamp) > 7 * oneDay && (now - it.timestamp) <= 14 * oneDay 
                            }.sumOf { it.amount }
                            
                            if (pCatSpent > 0.0) {
                                val change = ((cCatSpent - pCatSpent) / pCatSpent * 100).toInt()
                                if (isGoodExpense) {
                                    if (change >= 20) {
                                        evaluations.add(
                                            SmartSpendingInsight(
                                                title = "🌟 Tốt lắm! Tăng tích lũy '${catName}'",
                                                description = "Bạn đã gia tăng ngân sách cho việc tích lũy/trả nợ thêm ${change}% so với tuần trước. Rất đáng khen!",
                                                icon = Icons.Default.TrendingUp,
                                                tint = Color(0xFF4CAF50)
                                            )
                                        )
                                        foundCatEval = true
                                    }
                                } else {
                                    if (change <= -20) {
                                        evaluations.add(
                                            SmartSpendingInsight(
                                                title = "🎉 Giảm chi tiêu '${catName}'",
                                                description = "Chúc mừng! Bạn đã cắt giảm thành công chi phí nhóm '${catName}' đi ${-change}% so với tuần trước.",
                                                icon = Icons.Default.TrendingDown,
                                                tint = Color(0xFF4CAF50)
                                            )
                                        )
                                        foundCatEval = true
                                    } else if (change >= 35) {
                                        evaluations.add(
                                            SmartSpendingInsight(
                                                title = "📈 Tăng chi '${catName}'",
                                                description = "Cảnh báo: Chi phí dành cho nhóm '${catName}' đang tăng mạnh thêm ${change}% so với tuần trước.",
                                                icon = Icons.Default.TrendingUp,
                                                tint = Color(0xFFF44336)
                                            )
                                        )
                                        foundCatEval = true
                                    }
                                }
                            }
                        }
                        
                        if (!foundCatEval && pWeek > 0.0) {
                            val diffPercent = ((pWeek - cWeek) / pWeek * 100).toInt()
                            if (diffPercent >= 10) {
                                evaluations.add(
                                    SmartSpendingInsight(
                                        title = "Đánh giá chung: Tiết kiệm tốt",
                                        description = "Tổng chi tiêu tuần này giảm ${diffPercent}% so với tuần trước. Bạn quản lý ngân sách rất tốt!",
                                        icon = Icons.Default.CheckCircle,
                                        tint = Color(0xFF4CAF50)
                                    )
                                )
                            } else if (diffPercent <= -20) {
                                evaluations.add(
                                    SmartSpendingInsight(
                                        title = "Đánh giá chung: Tăng chi tiêu",
                                        description = "Tổng chi tiêu tuần này đang tăng ${-diffPercent}% so với tuần trước. Cần rà soát các khoản chi lớn.",
                                        icon = Icons.Default.TrendingUp,
                                        tint = Color(0xFFE91E63)
                                    )
                                )
                            }
                        }
                    }

                    if (evaluations.isEmpty()) {
                        evaluations.add(
                            SmartSpendingInsight(
                                title = "Chưa có đánh giá",
                                description = "Tiếp tục chi tiêu và ghi chép để AI đánh giá và so sánh định kỳ giữa các tuần.",
                                icon = Icons.Default.History,
                                tint = Color(0xFF90A4AE)
                            )
                        )
                    }

                    Triple(riskAlerts, recommendations, evaluations)
                }

                // Financial Health Score UI
                val healthScore = remember(totalIncome, totalExpense, savingsWallets, budgets, savingsGoals) {
                    var score = 100
                    
                    if (totalIncome > 0) {
                        val svRatio = (totalIncome - totalExpense) / totalIncome
                        if (svRatio < 0) score -= 30
                        else if (svRatio < 0.1) score -= 15
                    } else if (totalExpense > 0) {
                        score -= 20
                    }

                    budgets.forEach { b ->
                        val sp = b.spentAmount
                        if (b.limitAmount > 0 && sp > b.limitAmount) score -= 10
                    }
                    
                    wallets.forEach { 
                        if (it.balance < 0) score -= 20 
                    }
                    
                    if (savingsWallets.isNotEmpty()) score += 10
                    
                    score.coerceIn(0, 100)
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(2)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Điểm Sức Khỏe Tài Chính", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = when(healthScore) {
                                in 85..100 -> "Xuất sắc! Mood: Tự hào 🌟"
                                in 65..84 -> "Tốt! Mood: Thoải mái 😊"
                                in 40..64 -> "Trung bình. Mood: Cần chú ý 🤔"
                                else -> "Báo động! Mood: Căng thẳng 😰"
                            }, 
                            fontSize = 11.sp, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$healthScore",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            healthScore >= 80 -> Color(0xFF4CAF50)
                            healthScore >= 50 -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    )
                }

                // 1. RISK ALERTS SECTION
                Column(
                    modifier = Modifier.staggeredEntrance(3),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚠️ Cảnh báo rủi ro",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    aiAdvisorData.first.take(2).forEach { ins ->
                        InsightRow(ins)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                // 2. RECOMMENDATIONS SECTION
                Column(
                    modifier = Modifier.staggeredEntrance(4),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 Khuyến nghị",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    aiAdvisorData.second.take(2).forEach { ins ->
                        InsightRow(ins)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                // 3. EVALUATIONS SECTION
                Column(
                    modifier = Modifier.staggeredEntrance(5),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "📊 Đánh giá xu hướng",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    aiAdvisorData.third.take(2).forEach { ins ->
                        InsightRow(ins)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (eventToView != null) {
        val event = eventToView!!
        val eventTransactions = viewModel.allTransactions.collectAsState().value.filter { it.eventId == event.id }
        val limit = event.limitAmount ?: 0.0
        val spent = eventTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { eventToView = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header (System standard - like Image 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { eventToView = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                if (event.description.isNotBlank()) {
                    Text(event.description, fontSize = 14.sp)
                }
                val startStr = FormatHelper.formatDate(event.startDate)
                val endStr = event.endDate?.let { FormatHelper.formatDate(it) } ?: "Không tính"
                Text("Thời gian: $startStr - $endStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                if (limit > 0) {
                    Text("Tiến độ chi tiêu:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    val progress = (spent / limit).toFloat().coerceIn(0f, 1f)
                    StripedProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = if (progress >= 0.9f) MaterialTheme.colorScheme.error else try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(event.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${FormatHelper.formatVND(spent)} / ${FormatHelper.formatVND(limit)}",
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                } else {
                    Text("Đã chi: ${FormatHelper.formatVND(spent)}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }

                if (eventTransactions.isNotEmpty()) {
                    Text("Lịch sử giao dịch liên quan:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(eventTransactions.sortedByDescending { it.timestamp }) { tx ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.categoryName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(FormatHelper.formatDate(tx.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                val isAdjustmentDecrease = tx.type == "ADJUSTMENT" && !tx.note.contains("tăng")
                                Text(
                                    text = "${if (tx.type == "EXPENSE" || isAdjustmentDecrease) "-" else "+"}${FormatHelper.formatVND(tx.amount)}",
                                    color = if (tx.type == "EXPENSE" || isAdjustmentDecrease) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    Text("Chưa có giao dịch.", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun WalletMiniCard(
    wallet: Wallet,
    onClick: (Wallet) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick(wallet) }
            .testTag("wallet_mini_${wallet.id}"),
        colors = CardDefaults.cardColors(
            containerColor = FormatHelper.parseColor(wallet.colorHex).copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, FormatHelper.parseColor(wallet.colorHex).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FormatHelper.parseColor(wallet.colorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconMapper.getIconByName(wallet.iconName),
                        contentDescription = wallet.name,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = wallet.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column {
                Text(
                    text = "Số dư",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = FormatHelper.formatVND(wallet.balance),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

data class SmartSpendingInsight(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val score: Int = 0
)

@Composable
private fun InsightRow(ins: SmartSpendingInsight) {
    Row(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(ins.tint.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ins.icon,
                contentDescription = ins.title,
                tint = ins.tint,
                modifier = Modifier.size(14.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ins.title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = ins.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun FormattedVndText(
    amount: Double,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    val rawStr = FormatHelper.formatVND(amount)
    val baseAmountStr = rawStr.replace("₫", "").replace("đ", "").trim()
    val annotatedString = buildAnnotatedString {
        append(baseAmountStr)
        append(" ")
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
            append("đ")
        }
    }
    Text(
        text = annotatedString,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun DashboardEventCard(
    eventIdx: Int,
    event: com.app.data.Event,
    spentAmount: Double,
    limitAmount: Double,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisibleOnScreen by remember { mutableStateOf(false) }
    var skipAnimation by remember { mutableStateOf(false) }

    val entranceProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisibleOnScreen) 1f else 0f,
        animationSpec = if (skipAnimation) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(
            durationMillis = 480,
            delayMillis = (eventIdx * 80).coerceAtMost(200),
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "event_entrance_$eventIdx"
    )

    val targetProgress = if (limitAmount > 0.0) {
        (spentAmount / limitAmount).toFloat().coerceIn(0f, 1f)
    } else 0.5f

    val animProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisibleOnScreen) targetProgress else 0f,
        animationSpec = if (skipAnimation) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "event_progress_$eventIdx"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetYPx = remember(density) { with(density) { 35.dp.toPx() } }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val isOngoing = FormatHelper.isEventOngoing(event.startDate, event.endDate, nowMs)
    val daysText = if (isOngoing) {
        if (event.endDate != null) {
            val diffDays = FormatHelper.getDaysDifference(nowMs, event.endDate)
            if (diffDays == 0) "Hôm nay hết hạn" else "Còn $diffDays ngày"
        } else "Đang diễn ra"
    } else {
        val daysStart = FormatHelper.getDaysDifference(nowMs, event.startDate)
        if (daysStart > 0) "Sắp diễn ra sau $daysStart ngày" else "Bắt đầu hôm nay"
    }

    val baseEventColor = try {
        FormatHelper.parseColor(event.colorHex)
    } catch (e: Exception) {
        if (isOngoing) Color(0xFF4CAF50) else Color(0xFFF57C00)
    }

    val cardBg = baseEventColor.copy(alpha = 0.08f)
    val iconBg = baseEventColor.copy(alpha = 0.16f)
    val primaryColor = baseEventColor
    val trackBg = baseEventColor.copy(alpha = 0.25f)

    val pillBg = Color(0xFFFFF3E0)
    val pillIconColor = Color(0xFFEF6C00)
    val pillTextColor = Color(0xFFEF6C00)
    val pillText = if (isOngoing) daysText else "Chuẩn bị bắt đầu"

    Card(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                if (!isVisibleOnScreen) {
                    val y = coordinates.positionInRoot().y
                    if (y < screenHeightPx - 20f) {
                        if (y <= 0) skipAnimation = true
                        isVisibleOnScreen = true
                    }
                }
            }
            .graphicsLayer {
                alpha = entranceProgress
                translationY = if (skipAnimation) 0f else (1f - entranceProgress) * offsetYPx
            }
            .clickable { onClick() }
            .testTag("dashboard_event_card_${event.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, iconBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        event.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = pillBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = pillIconColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pillText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = pillTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (limitAmount > 0.0) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFF616161))) {
                            append(FormatHelper.formatVND(spentAmount))
                        }
                        withStyle(style = SpanStyle(color = Color(0xFF9E9E9E))) {
                            append(" / ${FormatHelper.formatVND(limitAmount)}")
                        }
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = FormatHelper.formatVND(spentAmount),
                    fontSize = 12.sp,
                    color = Color(0xFF616161),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(trackBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animProgress.coerceIn(0.001f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(primaryColor)
                )
            }
        }
    }
}

@Composable
private fun DashboardBudgetItem(
    budgetIdx: Int,
    categoryName: String,
    spentAmount: Double,
    limitAmount: Double,
    progressColor: Color,
    isExceeded: Boolean
) {
    var isVisibleOnScreen by remember { mutableStateOf(false) }
    var skipAnimation by remember { mutableStateOf(false) }

    val entranceProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisibleOnScreen) 1f else 0f,
        animationSpec = if (skipAnimation) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(
            durationMillis = 480,
            delayMillis = (budgetIdx * 60).coerceAtMost(180),
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "budget_entrance_$budgetIdx"
    )

    val targetBarProgress = if (limitAmount > 0) (spentAmount / limitAmount).toFloat().coerceIn(0f, 1f) else 0f
    val barProgress = if (isVisibleOnScreen) targetBarProgress else 0f

    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetYPx = remember(density) { with(density) { 35.dp.toPx() } }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (!isVisibleOnScreen) {
                    val y = coordinates.positionInRoot().y
                    if (y < screenHeightPx - 20f) {
                        if (y <= 0) skipAnimation = true
                        isVisibleOnScreen = true
                    }
                }
            }
            .graphicsLayer {
                alpha = entranceProgress
                translationY = if (skipAnimation) 0f else (1f - entranceProgress) * offsetYPx
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = categoryName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${FormatHelper.formatVND(spentAmount)} / ${FormatHelper.formatVND(limitAmount)}",
                fontSize = 11.sp,
                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        StripedProgressIndicator(
            progress = barProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    var isVisibleOnScreen by remember { mutableStateOf(false) }
    var skipAnimation by remember { mutableStateOf(false) }

    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVisibleOnScreen) 1f else 0f,
        animationSpec = if (skipAnimation) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(
            durationMillis = 380,
            delayMillis = (index * 45).coerceAtMost(130),
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "staggered_$index"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetYPx = remember(density) { with(density) { 35.dp.toPx() } }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    return this
        .onGloballyPositioned { coordinates ->
            if (!isVisibleOnScreen) {
                val y = coordinates.positionInRoot().y
                if (y < screenHeightPx - 20f) {
                    if (y <= 0) skipAnimation = true
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



