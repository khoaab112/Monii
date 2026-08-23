package com.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalFocusManager
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.AppNotification
import com.app.ui.NotificationType
import com.app.ui.screens.*
import com.app.ui.theme.MyApplicationTheme

object Routes {
    const val DASHBOARD = "dashboard"
    const val WALLETS = "wallets"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val BUDGET_GOAL = "budget_goal"
    const val SAVINGS_VAULT = "savings_vault"
    const val SETTINGS = "settings"
    const val ADD_TRANSACTION = "add_transaction"
    const val BANK_NOTIFICATION_HISTORY = "bank_notification_history"
    const val TIMELINE = "timeline"
    const val EVENTS = "events"
    const val DEBT_BOOK = "debt_book"
    const val AI_ADVISOR = "ai_advisor"
    const val WALLET_MANAGEMENT = "wallet_management"
    const val CATEGORY_MANAGEMENT = "category_management"
}

class MainActivity : ComponentActivity() {
    private val viewModel: FinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openBankNotifications = intent?.getBooleanExtra("OPEN_BANK_NOTIFICATIONS", false) ?: false
        setContent {
            MyApplicationTheme {
                MainContent(viewModel = viewModel, forceStartWithBankNotifications = openBankNotifications)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Note: Full recomposition will happen if we pass state, but this simple approach is fine
        // if the app is heavily recreated.
        // Actually to handle onNewIntent reliably during foreground, we can just send an event to viewModel.
        if (intent.getBooleanExtra("OPEN_BANK_NOTIFICATIONS", false)) {
            viewModel.triggerOpenBankNotifications()
        }
    }

    override fun onStop() {
        super.onStop()
        // Gracefully lock app if PIN protection is active to enforce background security protection
        viewModel.lockApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    forceStartWithBankNotifications: Boolean = false
) {
    val navController = rememberNavController()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val isLoadingSettings by viewModel.isLoadingSettings.collectAsState()
    val startScreen by viewModel.startScreen.collectAsState()
    
    val openBankNotificationsEvent by viewModel.openBankNotificationsEvent.collectAsState()

    // Observe active routes
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startScreen

    // Keep track of the last primary route to keep bottom bar item selected even when in "add_transaction"
    var lastPrimaryRoute by remember(startScreen) { mutableStateOf(startScreen) }

    LaunchedEffect(isAppUnlocked) {
        if (isAppUnlocked && forceStartWithBankNotifications) {
            navController.navigate(Routes.BANK_NOTIFICATION_HISTORY) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openBankNotificationsEvent, isAppUnlocked) {
        if (openBankNotificationsEvent && isAppUnlocked) {
            navController.navigate(Routes.BANK_NOTIFICATION_HISTORY) {
                launchSingleTop = true
            }
            viewModel.consumeOpenBankNotificationsEvent()
        }
    }

    LaunchedEffect(currentRoute) {
        val baseRoute = currentRoute.substringBefore("?")
        if (baseRoute in listOf(Routes.DASHBOARD, Routes.HISTORY, Routes.BUDGET_GOAL, Routes.SAVINGS_VAULT, Routes.STATS, Routes.SETTINGS, Routes.BANK_NOTIFICATION_HISTORY)) {
            lastPrimaryRoute = baseRoute
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationLogs by viewModel.notificationLogs.collectAsState()
    val notificationReaderEnabled by viewModel.notificationReaderEnabled.collectAsState()

    val pendingCount = remember(notificationLogs) {
        notificationLogs.count { it.status == "PENDING" }
    }

    var showScanResultPopup by remember { mutableStateOf(false) }
    var scannedLogsList by remember { mutableStateOf<List<com.app.ui.NotificationLog>>(emptyList()) }
    var showPermissionErrorPopup by remember { mutableStateOf(false) }
    var isScanningNotifications by remember { mutableStateOf(false) }
    var showScanningBadge by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isAppUnlocked, notificationReaderEnabled) {
        if (isAppUnlocked && notificationReaderEnabled) {
            val isPermitted = com.app.ui.screens.isNotificationServiceEnabled(context)
            if (!isPermitted) {
                showPermissionErrorPopup = true
            } else {
                // Ensure service is actively bound on startup/unlock
                com.app.service.BankNotificationListenerService.forceRebindService(context)
                isScanningNotifications = true
                showScanningBadge = true
                val startTime = System.currentTimeMillis()
                viewModel.scanNotificationsManual(
                    context = context,
                    onSuccess = { count ->
                        coroutineScope.launch {
                            val elapsed = System.currentTimeMillis() - startTime
                            val remaining = 2500L - elapsed
                            if (remaining > 0) {
                                kotlinx.coroutines.delay(remaining)
                            }
                            isScanningNotifications = false
                            showScanningBadge = false
                            if (count > 0) {
                                val activePending = viewModel.notificationLogs.value.filter { it.status == "PENDING" }.take(count)
                                scannedLogsList = activePending
                                showScanResultPopup = true
                            }
                        }
                    },
                    onError = { err ->
                        coroutineScope.launch {
                            val elapsed = System.currentTimeMillis() - startTime
                            val remaining = 2000L - elapsed
                            if (remaining > 0) {
                                kotlinx.coroutines.delay(remaining)
                            }
                            isScanningNotifications = false
                            showScanningBadge = false
                        }
                    }
                )
            }
        }
    }

    // Determine adaptive layout size classes
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val navigationItems = listOf(
        MainNavigationItem(Routes.DASHBOARD, "Tổng quan", Icons.Default.GridView),
        MainNavigationItem(Routes.HISTORY, "Lịch sử", Icons.Default.History),
        MainNavigationItem(Routes.ADD_TRANSACTION, "Giao dịch", Icons.Default.Add),
        MainNavigationItem(Routes.BANK_NOTIFICATION_HISTORY, "Thông báo", Icons.Default.Notifications),
        MainNavigationItem(Routes.SETTINGS, "Cài đặt", Icons.Default.Settings)
    )

    if (isLoadingSettings) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "App Logo",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Sổ Thu Chi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Đang tải dữ liệu...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))
                com.app.ui.components.AppLoadingIndicator(size = 64.dp)
            }
        }
    } else if (!isAppUnlocked) {
        // App is locked by PIN protection -> display overlay lock screen gate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PinLockScreen(viewModel = viewModel)
        }
    } else {
        val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()
        val isDatabaseEmpty by viewModel.isDatabaseEmpty.collectAsState()
        var showDonateModal by remember { mutableStateOf(false) }

        if (!hasSeenOnboarding) {
            com.app.ui.screens.OnboardingScreen(
                viewModel = viewModel,
                onComplete = { viewModel.setHasSeenOnboarding(true) }
            )
        } else {
            // App is unlocked -> display primary dashboard or nested modules
            val focusManager = LocalFocusManager.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    }
            ) {
            val topLevelRoutes = remember {
                listOf(
                    Routes.DASHBOARD,
                    Routes.HISTORY,
                    Routes.ADD_TRANSACTION,
                    Routes.BANK_NOTIFICATION_HISTORY,
                    Routes.SETTINGS
                )
            }
            val baseRoute = currentRoute.substringBefore("?")
            val canPop = navController.previousBackStackEntry != null && baseRoute !in topLevelRoutes

            if (isWideScreen) {
                // Adaptive wide layout: use Left Navigation Rail + Right Column with Header and Body
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        navigationItems.forEach { item ->
                            val isSelected = when (item.route) {
                                Routes.DASHBOARD -> currentRoute == Routes.DASHBOARD || currentRoute == Routes.WALLETS
                                Routes.HISTORY -> currentRoute == Routes.HISTORY
                                Routes.ADD_TRANSACTION -> currentRoute == Routes.ADD_TRANSACTION
                                Routes.BANK_NOTIFICATION_HISTORY -> currentRoute == Routes.BANK_NOTIFICATION_HISTORY
                                Routes.SETTINGS -> currentRoute == Routes.SETTINGS
                                else -> false
                            }
                            if (item.route == Routes.ADD_TRANSACTION) {
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = item.label, tint = Color.White)
                                        }
                                    },
                                    label = { Text(item.label, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("nav_rail_item_${item.route}")
                                )
                            } else {
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    icon = {
                                        if (item.route == Routes.BANK_NOTIFICATION_HISTORY && pendingCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge {
                                                        Text(text = pendingCount.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(imageVector = item.icon, contentDescription = item.label)
                                            }
                                        } else {
                                            Icon(imageVector = item.icon, contentDescription = item.label)
                                        }
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag("nav_rail_item_${item.route}")
                                )
                            }
                        }
                    }

                    val handleBackNavigation = {
                        val customTitle = viewModel.customTopBarTitle.value
                        if (customTitle != null) {
                            viewModel.setCustomTopBarTitle(null)
                        } else {
                            navController.popBackStack()
                        }
                    }

                    // Screen container with Header and #D9D9D9 background
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFBFBFB))
                        ) {
                            NavHostContainer(
                                navController = navController,
                                viewModel = viewModel,
                                onOpenDonateModal = { showDonateModal = true },
                                modifier = Modifier
                            )
                        }
                    }
                }
            } else {
                val handleBackNavigation = {
                    val customTitle = viewModel.customTopBarTitle.value
                    if (customTitle != null) {
                        viewModel.setCustomTopBarTitle(null)
                    } else {
                        navController.popBackStack()
                    }
                }

                // Adaptive portrait layout: Standard Scaffold + Bottom Navigation Bar
                Scaffold(
                    modifier = modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0.dp),
                    topBar = {},
                    bottomBar = {
                        NavigationBar {
                            navigationItems.forEach { item ->
                                val isSelected = when (item.route) {
                                    Routes.DASHBOARD -> currentRoute == Routes.DASHBOARD || currentRoute == Routes.WALLETS
                                    Routes.HISTORY -> currentRoute == Routes.HISTORY
                                    Routes.ADD_TRANSACTION -> currentRoute == Routes.ADD_TRANSACTION
                                    Routes.BANK_NOTIFICATION_HISTORY -> currentRoute == Routes.BANK_NOTIFICATION_HISTORY
                                    Routes.SETTINGS -> currentRoute == Routes.SETTINGS
                                    else -> false
                                }
                                if (item.route == Routes.ADD_TRANSACTION) {
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.startDestinationId)
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary 
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), 
                                                        shape = CircleShape
                                                    )
                                                    .padding(10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = item.label,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.testTag("nav_bar_item_${item.route}")
                                    )
                                } else {
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.startDestinationId)
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        icon = {
                                            if (item.route == Routes.BANK_NOTIFICATION_HISTORY && pendingCount > 0) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge {
                                                            Text(text = pendingCount.toString(), modifier = Modifier.testTag("notification_badge_count"))
                                                        }
                                                    }
                                                ) {
                                                    Icon(imageVector = item.icon, contentDescription = item.label)
                                                }
                                            } else {
                                                Icon(imageVector = item.icon, contentDescription = item.label)
                                            }
                                        },
                                        label = { Text(item.label, fontSize = 11.sp, maxLines = 1) },
                                        modifier = Modifier.testTag("nav_bar_item_${item.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFBFBFB))
                            .padding(innerPadding)
                    ) {
                        NavHostContainer(
                            navController = navController,
                            viewModel = viewModel,
                            onOpenDonateModal = { showDonateModal = true },
                            modifier = Modifier
                        )

                    // -------------------- POPUPS AND DIALOGS --------------------
                    if (showDonateModal) {
                        com.app.ui.components.AppModalBottomSheet(
                            onDismissRequest = { showDonateModal = false },
                            title = "Ủng hộ",
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_donate_qr),
                                    contentDescription = "Mã QR Ủng hộ",
                                    modifier = Modifier
                                        .fillMaxWidth(0.95f)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                )
                            }
                        }
                    }

                    if (showPermissionErrorPopup) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { showPermissionErrorPopup = false }
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(Color(0xFFFFF0F0), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Cấp quyền đọc thông báo",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Text(
                                        text = "Tính năng đọc tin nhắn ngân hàng tự động đang bật, nhưng ứng dụng chưa được cấp quyền truy cập thông báo hệ thống.",
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFF0F0), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = Color(0xFFD32F2F)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Vui lòng cấp quyền trong cài đặt để sử dụng tính năng này.",
                                            fontSize = 13.sp,
                                            color = Color(0xFFD32F2F),
                                            lineHeight = 18.sp
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showPermissionErrorPopup = false },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Để sau",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Button(
                                            onClick = {
                                                showPermissionErrorPopup = false
                                                try {
                                                    val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    viewModel.showErrorNotification("Không thể mở cài đặt")
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Cấp quyền ngay",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Thông tin của bạn luôn được bảo mật",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showScanResultPopup) {
                        AlertDialog(
                            onDismissRequest = { showScanResultPopup = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = "Phát hiện giao dịch mới!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Hệ thống đã tự động quét được ${scannedLogsList.size} thông báo ngân hàng mới cần phê duyệt:",
                                        fontSize = 14.sp
                                    )
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        scannedLogsList.forEach { log ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = log.bankName.ifEmpty { "Giao dịch" },
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = "${if (log.type == "INCOME") "+" else "-"}${FormatHelper.formatVND(log.amount)}",
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 12.sp,
                                                            color = if (log.type == "INCOME") Color(0xFF2E7D32) else Color(0xFFC62828)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = log.text,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showScanResultPopup = false
                                        navController.navigate(Routes.BANK_NOTIFICATION_HISTORY) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Xem chi tiết")
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Navigate to bank notifications",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showScanResultPopup = false }
                                ) {
                                    Text("Đóng")
                                }
                            }
                        )
                    }

                    // Floating Mini Scanning Badge - Nằm ở góc TRÊN bên phải (dưới header) như ảnh 2
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 16.dp, top = 8.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        AnimatedVisibility(
                            visible = isScanningNotifications && showScanningBadge,
                            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier
                                    .widthIn(min = 200.dp, max = 280.dp)
                                    .testTag("floating_scanning_badge"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val context = LocalContext.current
                                    val imageLoader = remember(context) {
                                        ImageLoader.Builder(context)
                                            .components {
                                                if (Build.VERSION.SDK_INT >= 28) {
                                                    add(ImageDecoderDecoder.Factory())
                                                } else {
                                                    add(GifDecoder.Factory())
                                                }
                                            }
                                            .build()
                                    }
                                    val painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(context)
                                            .data(R.drawable.searching)
                                            .build(),
                                        imageLoader = imageLoader
                                    )
                                    Image(
                                        painter = painter,
                                        contentDescription = "Đang quét",
                                        modifier = Modifier.size(32.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Đang quét thông báo",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Kiểm tra biến động số dư...",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(
                                        onClick = { showScanningBadge = false },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Đóng",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Global Notifications Overlay - Displayed in top-right corner
            val appNotifications by viewModel.appNotifications.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                contentAlignment = Alignment.TopEnd
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .padding(top = 16.dp, end = 16.dp)
                        .widthIn(max = 340.dp)
                ) {
                    appNotifications.forEach { notification ->
                        key(notification.id) {
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("notification_card_${notification.type.name.lowercase()}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (notification.type) {
                                            NotificationType.SUCCESS -> Color(0xFFE8F5E9)
                                            NotificationType.WARNING -> Color(0xFFFFF8E1)
                                            NotificationType.ERROR -> Color(0xFFFFEBEE)
                                        }
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        when (notification.type) {
                                            NotificationType.SUCCESS -> Color(0xFF81C784)
                                            NotificationType.WARNING -> Color(0xFFFFD54F)
                                            NotificationType.ERROR -> Color(0xFFE57373)
                                        }
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val context = LocalContext.current
                                        val gifRes = when (notification.type) {
                                            NotificationType.SUCCESS -> R.drawable.success
                                            NotificationType.WARNING -> R.drawable.warning
                                            NotificationType.ERROR -> R.drawable.error
                                        }
                                        val imageLoader = remember(context) {
                                            ImageLoader.Builder(context)
                                                .components {
                                                    if (Build.VERSION.SDK_INT >= 28) {
                                                        add(ImageDecoderDecoder.Factory())
                                                    } else {
                                                        add(GifDecoder.Factory())
                                                    }
                                                }
                                                .build()
                                        }
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(gifRes)
                                                .build(),
                                            imageLoader = imageLoader
                                        )
                                        Image(
                                            painter = painter,
                                            contentDescription = notification.type.name,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when (notification.type) {
                                                    NotificationType.SUCCESS -> "Thành công"
                                                    NotificationType.WARNING -> "Cảnh báo"
                                                    NotificationType.ERROR -> "Lỗi"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = when (notification.type) {
                                                    NotificationType.SUCCESS -> Color(0xFF1B5E20)
                                                    NotificationType.WARNING -> Color(0xFFE65100)
                                                    NotificationType.ERROR -> Color(0xFFB71C1C)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = notification.message,
                                                fontSize = 12.sp,
                                                color = when (notification.type) {
                                                    NotificationType.SUCCESS -> Color(0xFF2E7D32)
                                                    NotificationType.WARNING -> Color(0xFF5D4037)
                                                    NotificationType.ERROR -> Color(0xFF7F0000)
                                                },
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
    }
}

@Composable
fun NavHostContainer(
    navController: androidx.navigation.NavHostController,
    viewModel: FinanceViewModel,
    onOpenDonateModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startScreen by viewModel.startScreen.collectAsState()
    NavHost(
        navController = navController,
        startDestination = startScreen,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(150))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(150))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(150))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(150))
        }
    ) {
        composable(Routes.DASHBOARD) {
            val canPop = navController.previousBackStackEntry != null
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.DASHBOARD,
                    canPop = canPop,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateBack = if (canPop) { { navController.popBackStack() } } else null,
                    onNavigateToWallets = { wallet ->
                        if (wallet != null) {
                            viewModel.setFocusedWalletId(wallet.id)
                        }
                        navController.navigate(Routes.WALLETS)
                    },
                    onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                    onNavigateToStats = { navController.navigate(Routes.STATS) },
                    onNavigateToBudget = { navController.navigate(Routes.BUDGET_GOAL) },
                    onNavigateToSavings = { navController.navigate(Routes.SAVINGS_VAULT) },
                    onNavigateToBankNotifications = { navController.navigate(Routes.BANK_NOTIFICATION_HISTORY) },
                    onNavigateToDebtBook = { tabIdx -> navController.navigate("${Routes.DEBT_BOOK}?tab=$tabIdx") },
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onNavigateToEvents = { navController.navigate(Routes.EVENTS) }
                )
            }
        }

        composable(
            route = Routes.WALLETS,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            WalletsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            val canPop = navController.previousBackStackEntry != null
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.HISTORY,
                    canPop = canPop,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToTimeline = { dateStr -> 
                        navController.navigate("${Routes.TIMELINE}/${android.net.Uri.encode(dateStr)}")
                    }
                )
            }
        }

        composable(Routes.STATS) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.STATS,
                    canPop = true,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                ReportsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = Routes.BUDGET_GOAL,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            BudgetGoalScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SAVINGS_VAULT) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.SAVINGS_VAULT,
                    canPop = true,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                com.app.ui.screens.SavingsVaultScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.SETTINGS,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            val canPop = navController.previousBackStackEntry != null
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.SETTINGS,
                    canPop = canPop,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToBankNotificationHistory = { navController.navigate(Routes.BANK_NOTIFICATION_HISTORY) },
                    onNavigateToEvents = { navController.navigate(Routes.EVENTS) },
                    onNavigateToStats = { navController.navigate(Routes.STATS) },
                    onNavigateToSavings = { navController.navigate(Routes.SAVINGS_VAULT) },
                    onNavigateToDebtBook = { navController.navigate(Routes.DEBT_BOOK) },
                    onNavigateToWalletManagement = { navController.navigate(Routes.WALLET_MANAGEMENT) },
                    onNavigateToCategoryManagement = { navController.navigate(Routes.CATEGORY_MANAGEMENT) },
                    onNavigateToBudgetGoal = { navController.navigate(Routes.BUDGET_GOAL) }
                )
            }
        }

        composable(
            route = Routes.WALLET_MANAGEMENT,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            WalletManagementScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CATEGORY_MANAGEMENT,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            CategoryManagementScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.DEBT_BOOK}?tab={tab}",
            arguments = listOf(navArgument("tab") { defaultValue = "0" })
        ) { backStackEntry ->
            val tabStr = backStackEntry.arguments?.getString("tab") ?: "0"
            val initialTab = tabStr.toIntOrNull() ?: 0
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.DEBT_BOOK,
                    canPop = true,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                com.app.ui.screens.DebtBookScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    initialTab = initialTab
                )
            }
        }

        composable(Routes.AI_ADVISOR) {
            com.app.ui.screens.AIAdvisorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(Routes.DASHBOARD)
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.EVENTS,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(320)
                ) + fadeIn(animationSpec = tween(320))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(320)
                ) + fadeOut(animationSpec = tween(320))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(320)
                ) + fadeIn(animationSpec = tween(320))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(320)
                ) + fadeOut(animationSpec = tween(320))
            }
        ) {
            com.app.ui.components.EventManagementScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BANK_NOTIFICATION_HISTORY) {
            BankNotificationHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.TIMELINE}/{dateStr}",
            arguments = listOf(androidx.navigation.navArgument("dateStr") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val dateStr = backStackEntry.arguments?.getString("dateStr") ?: ""
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.TIMELINE,
                    canPop = true,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                TimelineScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    initialDateStr = dateStr
                )
            }
        }

        composable(
            route = Routes.ADD_TRANSACTION,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                null
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    currentRoute = Routes.ADD_TRANSACTION,
                    canPop = true,
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                    onNavigateToAIAdvisor = { navController.navigate(Routes.AI_ADVISOR) },
                    onOpenDonateModal = onOpenDonateModal
                )
                AddTransactionScreen(
                    viewModel = viewModel,
                    onSuccess = {
                        navController.navigate(Routes.HISTORY) {
                            popUpTo(Routes.ADD_TRANSACTION) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

data class MainNavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    currentRoute: String,
    canPop: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: FinanceViewModel? = null,
    onNavigateToAIAdvisor: (() -> Unit)? = null,
    onOpenDonateModal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val customTitle = viewModel?.customTopBarTitle?.collectAsState()?.value
    val baseRoute = currentRoute.substringBefore("?")
    val title = customTitle ?: when {
        baseRoute == Routes.DASHBOARD -> "TỔNG QUAN"
        baseRoute == Routes.WALLETS -> "QUẢN LÝ VÍ"
        baseRoute == Routes.HISTORY -> "LỊCH SỬ GIAO DỊCH"
        baseRoute == Routes.STATS -> "THỐNG KÊ & BÁO CÁO"
        baseRoute == Routes.BUDGET_GOAL -> "HẠN MỨC CHI TIÊU"
        baseRoute == Routes.SAVINGS_VAULT -> "SỔ TIẾT KIỆM"
        baseRoute == Routes.SETTINGS -> "CÀI ĐẶT"
        baseRoute == Routes.ADD_TRANSACTION -> "THÊM GIAO DỊCH"
        baseRoute == Routes.BANK_NOTIFICATION_HISTORY -> "THÔNG BÁO NGÂN HÀNG"
        baseRoute.startsWith(Routes.TIMELINE) -> "DÒNG THỜI GIAN"
        baseRoute == Routes.EVENTS -> "QUẢN LÝ SỰ KIỆN"
        baseRoute == Routes.DEBT_BOOK -> "SỔ NỢ"
        baseRoute == Routes.AI_ADVISOR -> "CỐ VẤN TÀI CHÍNH AI"
        baseRoute == Routes.WALLET_MANAGEMENT -> "QUẢN LÝ VÍ & TÀI KHOẢN"
        baseRoute == Routes.CATEGORY_MANAGEMENT -> "QUẢN LÝ DANH MỤC"
        else -> "QUẢN LÝ THU CHI"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        tonalElevation = 1.dp
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                if (canPop) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            actions = {
                if (baseRoute == Routes.SETTINGS && onOpenDonateModal != null) {
                    IconButton(
                        onClick = onOpenDonateModal,
                        modifier = Modifier.testTag("app_header_donate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Ủng hộ",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (baseRoute == Routes.DASHBOARD && onNavigateToAIAdvisor != null) {
                    IconButton(
                        onClick = onNavigateToAIAdvisor,
                        modifier = Modifier.testTag("app_header_ai_advisor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Trợ lý AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if ((baseRoute == Routes.STATS || baseRoute == Routes.HISTORY) && viewModel != null) {
                    val exportContext = androidx.compose.ui.platform.LocalContext.current
                    val activeMonth by viewModel.activeMonth.collectAsState()
                    val transactions by viewModel.dailyTransactions.collectAsState()
                    val historyFilteredTransactions by viewModel.filteredTransactions.collectAsState()
                    val eventsList by viewModel.allEvents.collectAsState()
                    val debtsList by viewModel.allDebts.collectAsState()
                    
                    IconButton(
                        onClick = {
                            if (baseRoute == Routes.STATS) {
                                val monthTxs = transactions.filter { tx ->
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                                    val txMonth = String.format("%04d-%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
                                    txMonth == activeMonth
                                }
                                com.app.ui.ExcelExportHelper.exportTransactionsToCsv(
                                    context = exportContext,
                                    transactions = monthTxs,
                                    events = eventsList,
                                    debts = debtsList,
                                    onWarning = { viewModel.showWarningNotification(it) },
                                    onError = { viewModel.showErrorNotification(it) }
                                )
                            } else {
                                com.app.ui.ExcelExportHelper.exportTransactionsToCsv(
                                    context = exportContext,
                                    transactions = historyFilteredTransactions,
                                    events = eventsList,
                                    debts = debtsList,
                                    onWarning = { viewModel.showWarningNotification(it) },
                                    onError = { viewModel.showErrorNotification(it) }
                                )
                            }
                        },
                        modifier = Modifier.testTag("report_export_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Xuất Excel",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

