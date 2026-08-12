package com.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.data.FinanceCategory
import com.app.data.Transaction
import com.app.data.Wallet
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.IconMapper
import com.app.ui.components.CustomMoneyInputField
import com.app.service.GeminiAdvisorService
import android.os.Build
import androidx.compose.foundation.Image
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun isNotificationServiceEnabled(context: android.content.Context): Boolean {
    val pkgName = context.packageName
    val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}

@Composable
private fun Modifier.staggeredEntrance(
    index: Int,
    key: String,
    seenKeys: MutableList<String>
): Modifier {
    val alreadySeen = remember(key) { seenKeys.contains(key) }
    var cardVisible by rememberSaveable(key) { mutableStateOf(alreadySeen) }

    LaunchedEffect(key) {
        if (!alreadySeen) {
            cardVisible = true
            seenKeys.add(key)
        }
    }

    val alphaProgress by animateFloatAsState(
        targetValue = if (cardVisible || alreadySeen) 1f else 0f,
        animationSpec = if (alreadySeen) snap() else tween(
            durationMillis = 400,
            delayMillis = (index * 50).coerceAtMost(250),
            easing = LinearOutSlowInEasing
        ),
        label = "settings_stagger_alpha_$index"
    )

    val offsetYProgress by animateDpAsState(
        targetValue = if (cardVisible || alreadySeen) 0.dp else 24.dp,
        animationSpec = if (alreadySeen) snap() else tween(
            durationMillis = 400,
            delayMillis = (index * 50).coerceAtMost(250),
            easing = LinearOutSlowInEasing
        ),
        label = "settings_stagger_offset_$index"
    )

    return this.graphicsLayer {
        alpha = alphaProgress
        translationY = offsetYProgress.toPx()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    onNavigateToBankNotificationHistory: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    onNavigateToDebtBook: () -> Unit = {},
    onNavigateToWalletManagement: () -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onNavigateToBudgetGoal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val seenKeys = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } }
    )) { mutableStateListOf<String>() }

    val isPinEnabled by viewModel.isPinEnabled.collectAsState()

    val startScreen by viewModel.startScreen.collectAsState()
    val preferredStartScreen by viewModel.preferredStartScreen.collectAsState()
    var isPreferredScreenExpanded by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showEventManagement by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showCloudRestoreDialog by remember { mutableStateOf(false) }
    var isDeveloperExpanded by remember { mutableStateOf(false) }

    var isManagementExpanded by remember { mutableStateOf(true) }
    var isAdvancedExpanded by remember { mutableStateOf(true) }
    var isAiExpanded by remember { mutableStateOf(true) }
    var isCloudSyncExpanded by remember { mutableStateOf(true) }
    var isLocalBackupExpanded by remember { mutableStateOf(true) }
    var isInfoExpanded by remember { mutableStateOf(true) }

    // Developer simulation states
    var simTitle by remember { mutableStateOf("Vietcombank") }
    var simText by remember { mutableStateOf("TK 1012938475 +5,000,000 VND luc 14:32. ND: Chuyen khoan luong thang 5") }
    var simPackage by remember { mutableStateOf("com.vietcombank.card") }

    val localBackupLastTime by viewModel.localBackupLastTime.collectAsState()
    val localBackupCount by viewModel.localBackupCount.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val syncProgressLogs by viewModel.syncProgressLogs.collectAsState()

    val wallets by viewModel.allWallets.collectAsState()
    val categories by viewModel.categoriesList.collectAsState()

    val backupFilePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.importLocalBackup(context, it)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
        var googleAccount by remember(isCloudSyncEnabled) {
            mutableStateOf(com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context))
        }

        val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    val signedAccount = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    googleAccount = signedAccount ?: com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                    viewModel.toggleCloudSync(true)
                    
                    viewModel.checkDriveBackupConflict(context) { hasConflict ->
                        if (hasConflict) {
                            showCloudRestoreDialog = true
                        } else {
                            com.app.service.CloudSyncWorker.setupPeriodicSync(context)
                            viewModel.showSuccessNotification("Đã kết nối Google Drive và bật đồng bộ!")
                        }
                    }
                } catch (e: Exception) {
                    viewModel.showWarningNotification("Lỗi đăng nhập Google: ${e.message}")
                }
            } else {
                var errorMsg = "Đăng nhập bị hủy."
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                } catch (apiException: com.google.android.gms.common.api.ApiException) {
                    errorMsg = "Lỗi Google (${apiException.statusCode}): ${apiException.message}"
                } catch (e: Exception) {
                    // ignore
                }
                viewModel.showWarningNotification(errorMsg)
            }
        }

        val gifImageLoader = remember(context) {
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

        val userAvatarGifPainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(com.app.R.drawable.user_avatar)
                .build(),
            imageLoader = gifImageLoader
        )

        // =============================================================
        // SECTION 1: Đồng bộ google
        // =============================================================
        Text(
            text = "Đồng bộ google",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.staggeredEntrance(0, "settings_txt_sync", seenKeys)
        )

        Card(
            modifier = Modifier.fillMaxWidth().staggeredEntrance(1, "settings_card_sync", seenKeys),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val currentAccount = googleAccount
                if (currentAccount == null) {
                    // TRẠNG THÁI 1: CHƯA ĐĂNG NHẬP GOOGLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Default Avatar",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(
                                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"),
                                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.readonly")
                                    )
                                    .build()
                                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                signInLauncher.launch(client.signInIntent)
                            },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("google_login_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = com.app.R.drawable.ic_google),
                                    contentDescription = "Google Logo",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Đăng nhập",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    // TRẠNG THÁI 2: ĐÃ ĐĂNG NHẬP GOOGLE
                    var isAccountDetailsExpanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isAccountDetailsExpanded = !isAccountDetailsExpanded }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val photoUrl = currentAccount.photoUrl
                            if (photoUrl != null) {
                                coil.compose.AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    val initial = currentAccount.displayName?.firstOrNull()?.toString()?.uppercase() ?: "G"
                                    Text(
                                        text = initial,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 20.sp
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentAccount.displayName ?: "Người dùng Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentAccount.email ?: "",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = if (isAccountDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = {
                                    androidx.work.WorkManager.getInstance(context).cancelUniqueWork("CloudSyncService")
                                    viewModel.toggleCloudSync(false)
                                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
                                        googleAccount = null
                                    }
                                    googleAccount = null
                                    viewModel.clearAllData(context)
                                    viewModel.setHasSeenOnboarding(false)
                                    viewModel.showSuccessNotification("Đã đăng xuất tài khoản Google")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Collapsible section when clicked
                        AnimatedVisibility(
                            visible = isAccountDetailsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { viewModel.backupToDriveNow(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Cưỡng ép đồng bộ lên Google Drive",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =============================================================
        // SECTION 2: Thiết lập (Gộp các mục danh mục)
        // =============================================================
        Text(
            text = "Thiết lập",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.staggeredEntrance(1, "settings_txt_setup", seenKeys)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Quản lý ví
                ListItem(
                    headlineContent = { Text("Quản lý ví", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF2196F3), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = "Wallets", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(2, "setup_item_wallets", seenKeys)
                        .clickable { onNavigateToWalletManagement() }
                        .testTag("manage_wallets_item")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 2. Quản lý các hạng mục
                ListItem(
                    headlineContent = { Text("Quản lý các hạng mục", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFF9800), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Category, contentDescription = "Categories", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(3, "setup_item_categories", seenKeys)
                        .clickable { onNavigateToCategoryManagement() }
                        .testTag("manage_categories_item")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 3. Hạn mức
                ListItem(
                    headlineContent = { Text("Hạn mức", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF00BCD4), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = "Budget", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(4, "setup_item_budget", seenKeys)
                        .clickable { onNavigateToBudgetGoal() }
                        .testTag("manage_budget_goal_item")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 4. Sự kiện
                ListItem(
                    headlineContent = { Text("Sự kiện", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF4CAF50), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Event, contentDescription = "Events", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(5, "setup_item_events", seenKeys)
                        .clickable { onNavigateToEvents() }
                        .testTag("manage_events_item")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 5. Tiết kiệm
                ListItem(
                    headlineContent = { Text("Tiết kiệm", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFE91E63), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Savings, contentDescription = "Savings", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(6, "setup_item_savings", seenKeys)
                        .clickable { onNavigateToSavings() }
                        .testTag("manage_savings_item")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 6. Sổ nợ
                ListItem(
                    headlineContent = { Text("Sổ nợ", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF44336), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Assignment, contentDescription = "Debt Book", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(7, "setup_item_debt", seenKeys)
                        .clickable { onNavigateToDebtBook() }
                        .testTag("manage_debt_book_item")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 7. Thống kê & báo cáo
                ListItem(
                    headlineContent = { Text("Thống kê & báo cáo", fontWeight = FontWeight.Bold) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF9C27B0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.PieChart, contentDescription = "Statistics", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .staggeredEntrance(8, "setup_item_stats", seenKeys)
                        .clickable { onNavigateToStats() }
                        .testTag("navigate_stats_item")
                )
            }
        }

        // =============================================================
        // SECTION 3: Hệ thống
        // =============================================================
        Text(
            text = "Hệ thống",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.staggeredEntrance(9, "settings_txt_system", seenKeys)
        )

        Card(
            modifier = Modifier.fillMaxWidth().staggeredEntrance(10, "settings_card_system", seenKeys),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Bảo mật bằng mã PIN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Bảo mật bằng mã PIN", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { check ->
                            if (check) showPinSetupDialog = true else viewModel.disablePin()
                        },
                        modifier = Modifier.testTag("pin_protection_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. Ưu tiên hiển thị
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPreferredScreenExpanded = !isPreferredScreenExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = "Launch Screen", tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(text = "Ưu tiên hiển thị", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val label = when (preferredStartScreen) {
                                    "dashboard" -> "Tổng quan"
                                    "history" -> "Lịch sử"
                                    "bank_notification_history" -> "Thông báo"
                                    "add_transaction" -> "Giao dịch"
                                    "settings" -> "Cài đặt"
                                    else -> "Giao dịch"
                                }
                                Text(text = "Màn hình mở đầu: $label", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(
                            imageVector = if (isPreferredScreenExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPreferredScreenExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val options = listOf(
                                Triple("dashboard", "Tổng quan", Icons.Default.GridView),
                                Triple("history", "Lịch sử", Icons.Default.History),
                                Triple("bank_notification_history", "Thông báo", Icons.Default.Notifications),
                                Triple("add_transaction", "Giao dịch", Icons.Default.AddCircle),
                                Triple("settings", "Cài đặt", Icons.Default.Settings)
                            )
                            options.forEach { (route, label, icon) ->
                                val isSelected = preferredStartScreen == route
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setStartScreen(route) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(imageVector = icon, contentDescription = label, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        Text(text = label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setStartScreen(route) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3. Sao lưu file
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = "Backup", tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Sao lưu file", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Lần sao lưu cuối", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(localBackupLastTime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Tổng số bản sao lưu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$localBackupCount file", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.openBackupFolder(context) },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("open_folder_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mở thư mục chứa", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = { viewModel.exportLocalBackup(context) },
                            modifier = Modifier.weight(1f).height(44.dp).testTag("start_backup_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao lưu", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = { backupFilePickerLauncher.launch("application/json") },
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("restore_backup_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Khôi phục dữ liệu từ bản sao .json", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 4. Xóa toàn bộ dữ liệu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearDataDialog = true }
                        .padding(vertical = 4.dp)
                        .testTag("clear_database_item"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                    Text(text = "Xóa toàn bộ dữ liệu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // =============================================================
        // SECTION 4: Developer simulation panel & AI Key
        // =============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(6, "settings_txt_dev", seenKeys)
                .clickable { isDeveloperExpanded = !isDeveloperExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "[DEVELOPER]", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(imageVector = if (isDeveloperExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (isDeveloperExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Feature 1: Nạp dữ liệu demo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dataset,
                            contentDescription = "Database",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nạp mẫu giao dịch demo",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Thêm tự động các giao dịch ngẫu nhiên để thẩm định biểu đồ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.seedSampleData()
                                viewModel.showSuccessNotification("Đã nạp dữ liệu mẫu thành công!")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("seed_database_item")
                        ) {
                            Text("Chạy", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Feature 2: Mô phỏng biến động số dư ngân hàng
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Simulate",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Mô phỏng tin nhắn ngân hàng",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Gửi tin nhắn biến động số dư giả lập để thử nghiệm tính năng quét",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                Triple("Vietcombank", "Vietcombank SD TK 0451 thay doi +500,000 VND luc 12:30. GD: Chuyen khoan luong thang 5", "com.vietcombank.restyle"),
                                Triple("Techcombank", "Techcombank: TK 1903 bien dong -150,000 VND. Noi dung: Mua tra sua HighTea", "vn.com.techcombank"),
                                Triple("MoMo", "Ban da thanh toan thanh cong so tien -50,000 d cho dich vu GrabFood qua vi MoMo", "com.mservice.momo"),
                                Triple("MB Bank", "MB_BANK: GD +200,000 VND luc 15:40. ND: Tien mung sinh nhat", "com.mbmobile")
                            )
                            presets.forEach { (bankName, presetText, pkg) ->
                                val isSelected = simTitle == bankName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        simTitle = bankName
                                        simText = presetText
                                        simPackage = pkg
                                    },
                                    label = { Text(bankName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = simText,
                            onValueChange = { simText = it },
                            label = { Text("Nội dung giả lập ngân hàng", fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.simulateBankNotification(simTitle, simText, simPackage)
                                viewModel.showSuccessNotification("Mô phỏng thành công! Đã thêm một giao dịch tin nhắn giả lập.")
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("simulate_notification_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.BugReport, contentDescription = "Simulate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chạy thử mô phỏng", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Feature 3: Gemini API Key
                    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
                    var apiKeyInput by remember { mutableStateOf("") }
                    LaunchedEffect(geminiApiKey) { apiKeyInput = geminiApiKey }

                    Text("Cấu hình Gemini API Key", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.saveGeminiApiKey(apiKeyInput.trim()) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Lưu API Key")
                    }
                }
            }
        }
    }

    // Beautiful Interactive State Dialog for sync feedback
    if (syncStatus.isNotEmpty()) {
        if (syncStatus == "SUCCESS_LOCAL_BACKUP") {
            AlertDialog(
                onDismissRequest = { viewModel.clearSyncLogs() },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.shareBackupFile(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("share_backup_confirm_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chia sẻ")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.clearSyncLogs() },
                        modifier = Modifier.testTag("close_backup_confirm_button")
                    ) {
                        Text("Đóng")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF0F9D58))
                        Text(
                            text = "SAO LƯU THÀNH CÔNG!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Bản sao lưu đã được tạo và lưu trữ an toàn trong ứng dụng cũng như thư mục Downloads công khai.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = "Bạn có muốn chia sẻ tệp sao lưu (.json) này ra ứng dụng ngoài (gửi qua Zalo, Messenger, email hoặc lưu Driver) không?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        Text(
                            text = "Chi tiết tiến trình:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            syncProgressLogs.forEach { log ->
                                Text(
                                    text = log,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.clearSyncLogs() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSyncLogs() }) {
                        Text("Đã hiểu")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (syncStatus) {
                            "SYNCING" -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            "SUCCESS_CLOUD", "SUCCESS", "SUCCESS_CLEAR" -> Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF0F9D58))
                            "SUCCESS_CLIPBOARD" -> Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copied", tint = Color(0xFF4285F4))
                            "ERROR" -> Icon(imageVector = Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            text = when (syncStatus) {
                                "SYNCING" -> "ĐANG ĐỒNG BỘ..."
                                "SUCCESS_CLOUD", "SUCCESS" -> "ĐỒNG BỘ CLOUD THÀNH CÔNG"
                                "SUCCESS_CLEAR" -> "XÓA DỮ LIỆU THÀNH CÔNG"
                                "SUCCESS_CLIPBOARD" -> "ĐÃ COPY DỮ LIỆU ĐỂ DÁN (PASTE)"
                                else -> "ĐỒNG BỘ THẤT BẠI"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        syncProgressLogs.forEach { log ->
                            Text(
                                text = log,
                                fontSize = 11.sp,
                                color = if (log.contains("Lỗi") || log.contains("Thất bại")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    }

    // Clear Data confirmation dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Bạn có chắc chắn muốn xóa tất cả?") },
            text = { Text("Thao tác này sẽ xóa sạch toàn bộ ví tài chính, toàn bộ các giao dịch, báo cáo ngân sách và các mục tiêu tiết kiệm đang có trên thiết bị của bạn. Dữ liệu không thể khôi phục lại trừ khi bạn đã sao lưu trước đó.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        viewModel.clearAllData(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa hết dữ liệu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Hủy bỏ")
                }
            }
        )
    }

    if (showCloudRestoreDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val googleAccount = remember {
            com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
        }
        val dialogGifLoader = remember(context) {
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
        val fileIconGifPainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(com.app.R.drawable.file_icon)
                .build(),
            imageLoader = dialogGifLoader
        )

        ModalBottomSheet(
            onDismissRequest = {
                showCloudRestoreDialog = false
                viewModel.toggleCloudSync(false)
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: User Profile Account Row
                if (googleAccount != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val photoUrl = googleAccount.photoUrl
                        if (photoUrl != null) {
                            coil.compose.AsyncImage(
                                model = photoUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val initial = googleAccount.displayName?.firstOrNull()?.toString()?.uppercase() ?: "G"
                                    Text(
                                        text = initial,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = googleAccount.displayName ?: "Người dùng Google",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = googleAccount.email ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 1st Red Box Position: Animated Illustration GIF
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = fileIconGifPainter,
                        contentDescription = "File Icon GIF Animation",
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth(0.6f),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Phát hiện bản sao lưu trên Drive!",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tài khoản Google của bạn có chứa dữ liệu đã được sao lưu trước đó. Bạn muốn xử lý thế nào với dữ liệu hiện tại trên thiết bị?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 3 Action Buttons (Arranged from left to right: Ghi đè, Hợp nhất, then Khôi phục full-width below)
                // Row 1: Ghi đè (Left) & Hợp nhất (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1 (Left): Ghi đè đám mây
                    OutlinedButton(
                        onClick = {
                            showCloudRestoreDialog = false
                            viewModel.toggleCloudSync(true)
                            com.app.service.CloudSyncWorker.setupPeriodicSync(context)
                            viewModel.showSuccessNotification("Đã bật đồng bộ (Ghi đè dữ liệu đám mây)")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = com.app.R.drawable.ic_press),
                                contentDescription = "Ghi đè",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Ghi đè",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Button 2 (Right): Hợp nhất dữ liệu (White background + Primary blue border)
                    OutlinedButton(
                        onClick = {
                            showCloudRestoreDialog = false
                            viewModel.mergeFromDrive(context)
                            viewModel.toggleCloudSync(true)
                            com.app.service.CloudSyncWorker.setupPeriodicSync(context)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = com.app.R.drawable.ic_merge),
                                contentDescription = "Hợp nhất",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Hợp nhất",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Full-Width Button 3: Khôi phục (Ghi đè máy)
                OutlinedButton(
                    onClick = {
                        showCloudRestoreDialog = false
                        viewModel.restoreFromDrive(context)
                        viewModel.toggleCloudSync(true)
                        com.app.service.CloudSyncWorker.setupPeriodicSync(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.app.R.drawable.ic_backup),
                            contentDescription = "Khôi phục",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Khôi phục (Ghi đè máy)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Secure PIN Setup Dialog
    if (showPinSetupDialog) {
        PinSetupDialog(
            onDismiss = { showPinSetupDialog = false },
            onSavePin = { pin ->
                viewModel.enablePin(pin)
                showPinSetupDialog = false
            }
        )
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var enteredText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("THIẾT LẬP MÃ PIN MỚI") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Vui lòng mã hóa mã PIN mới của bạn (đúng 4 ký tự số):", fontSize = 13.sp)
                OutlinedTextField(
                    value = enteredText,
                    onValueChange = {
                        if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                            enteredText = it
                            errorMessage = ""
                        }
                    },
                    label = { Text("Mã PIN (4 chữ số)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("setup_pin_text_input")
                )

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (enteredText.length == 4) {
                        onSavePin(enteredText)
                    } else {
                        errorMessage = "Mã PIN phải chứa đúng 4 chữ số!"
                    }
                },
                modifier = Modifier.testTag("confirm_save_pin_btn")
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

// ==========================================
// 1. WALLET MANAGEMENT DIALOG
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagementDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val wallets by viewModel.allWallets.collectAsState()
    
    // New Wallet Form States
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var walletType by remember { mutableStateOf("CASH") } // CASH, BANK, WALLET, SAVINGS
    var initialBalanceStr by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#2196F3") }
    var selectedIcon by remember { mutableStateOf("AccountBalanceWallet") }
    var isCustomColorActive by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#9C27B0") }
    var walletToDelete by remember { mutableStateOf<com.app.data.Wallet?>(null) }

    if (walletToDelete != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { walletToDelete = null },
            title = { Text("Xác nhận xóa ví?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa ví '${walletToDelete?.name}'? Hành động này cũng sẽ ảnh hưởng đến các dữ liệu liên quan và không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        walletToDelete?.let { wallet ->
                            viewModel.deleteWallet(wallet)
                            viewModel.showSuccessNotification("Xóa ví thành công")
                        }
                        walletToDelete = null
                    }
                ) {
                    Text("XÓA", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToDelete = null }) {
                    Text("HỦY")
                }
            }
        )
    }
    
    val typeDisplayName = mapOf("CASH" to "Tiền mặt", "BANK" to "Ngân hàng", "WALLET" to "Ví điện tử", "SAVINGS" to "Tích lũy", "CREDIT" to "Thẻ tín dụng")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .testTag("wallet_management_dialog"),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("QUẢN LÝ VÍ & TÀI KHOẢN", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { showAddForm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add_wallet_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thêm ví tài khoản mới", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("Danh sách ví (Kéo ☰ để sắp xếp thứ tự ưu tiên)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SortableWalletList(
                    wallets = wallets,
                    viewModel = viewModel,
                    typeDisplayName = typeDisplayName,
                    onDeleteRequest = { walletToDelete = it }
                )
            }
        },
        confirmButton = {}
    )

    if (showAddForm) {
        com.app.ui.components.AddWalletSheet(
            onDismiss = { showAddForm = false },
            onAddWallet = { walletName, type, startingBalance, color, icon ->
                viewModel.addWallet(walletName, type, startingBalance, color, icon)
                viewModel.showSuccessNotification("Thêm ví/tài khoản thành công!")
                showAddForm = false
            }
        )
    }
}

@Composable
fun SortableWalletList(
    wallets: List<Wallet>,
    viewModel: FinanceViewModel,
    typeDisplayName: Map<String, String>,
    onDeleteRequest: (Wallet) -> Unit
) {
    val listState = remember(wallets) { mutableStateListOf<Wallet>().apply { addAll(wallets) } }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var driftY by remember { mutableStateOf(0f) }

    val onDragReleased = {
        viewModel.updateWalletsOrder(listState.toList())
        draggedIndex = null
        driftY = 0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listState.forEachIndexed { index, wt ->
            val isDragged = draggedIndex == index
            val verticalOffset = if (isDragged) driftY else 0f
            val zIndexValue = if (isDragged) 10f else 1f
            val scaleValue = if (isDragged) 1.04f else 1f

            val colorValue = try {
                FormatHelper.parseColor(wt.colorHex)
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(zIndexValue)
                    .graphicsLayer {
                        translationY = verticalOffset
                        scaleX = scaleValue
                        scaleY = scaleValue
                    }
                    .background(Color.Transparent)
                    .border(
                        width = if (isDragged) 2.dp else 1.dp,
                        color = if (isDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f) 
                                     else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colorValue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconMapper.getIconByName(wt.iconName),
                                contentDescription = wt.name,
                                tint = colorValue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = wt.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colorValue.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = typeDisplayName[wt.type] ?: wt.type,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = colorValue
                                    )
                                }
                            }
                            
                            Text(
                                text = FormatHelper.formatVND(wt.balance),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (listState.size > 1) {
                            IconButton(
                                onClick = { onDeleteRequest(wt) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Kéo để sắp xếp",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(36.dp)
                                .padding(4.dp)
                                .pointerInput(index) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedIndex = index
                                            driftY = 0f
                                        },
                                        onDragEnd = { onDragReleased() },
                                        onDragCancel = { onDragReleased() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            driftY += dragAmount.y
                                            val itemHeightPx = 76.dp.toPx()
                                            val targetIdx = draggedIndex
                                            if (targetIdx != null) {
                                                if (driftY > itemHeightPx * 0.8f && targetIdx < listState.lastIndex) {
                                                    val next = listState[targetIdx + 1]
                                                    listState[targetIdx + 1] = listState[targetIdx]
                                                    listState[targetIdx] = next
                                                    draggedIndex = targetIdx + 1
                                                    driftY -= itemHeightPx
                                                } else if (driftY < -itemHeightPx * 0.8f && targetIdx > 0) {
                                                    val prev = listState[targetIdx - 1]
                                                    listState[targetIdx - 1] = listState[targetIdx]
                                                    listState[targetIdx] = prev
                                                    draggedIndex = targetIdx - 1
                                                    driftY += itemHeightPx
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. CATEGORY MANAGEMENT DIALOG
// ==========================================
@Composable
fun CategoryManagementDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val categoriesList by viewModel.categoriesList.collectAsState()
    
    // New Category Form States
    var selectedTypeTab by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var parentName by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf("EXPENSE") } // EXPENSE, INCOME, BOTH
    var selectedColor by remember { mutableStateOf("#4CAF50") }
    var selectedIcon by remember { mutableStateOf("ShoppingCart") }
    var isCustomColorActive by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#9C27B0") }
    var categoryToDelete by remember { mutableStateOf<FinanceCategory?>(null) }
    var categoryToEdit by remember { mutableStateOf<FinanceCategory?>(null) }
    
    LaunchedEffect(categoryToEdit) {
        val editCat = categoryToEdit
        if (editCat != null) {
            name = editCat.name
            parentName = editCat.parentName
            type = editCat.type
            selectedIcon = editCat.iconName
            selectedColor = editCat.colorHex
            
            // Check if it's custom
            val colorPalette = listOf(
                "#F44336", "#E91E63", "#9C27B0", "#673AB7",
                "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
                "#009688", "#4CAF50", "#8BC34A", "#FFEB3B",
                "#FF9800", "#795548", "#607D8B", "#455A64"
            )
            if (!colorPalette.contains(editCat.colorHex)) {
                isCustomColorActive = true
                customColorHex = editCat.colorHex
            } else {
                isCustomColorActive = false
            }
            showAddForm = true
        }
    }
    
    val colorPalette = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#FFEB3B",
        "#FF9800", "#795548", "#607D8B", "#455A64"
    )
    val iconPalette = listOf(
        "Restaurant", "DirectionsCar", "ShoppingBag", "Receipt", 
        "SportsEsports", "School", "LocalHospital", "Home", 
        "Work", "CardGiftcard", "Storefront", "Payments", 
        "AccountBalance", "AccountBalanceWallet", "Savings", 
        "TrendingUp", "TrendingDown", "Lock", "Settings",
        "Coffee", "LocalBar", "Flight", "Checkroom", "FitnessCenter",
        "Pets", "ChildCare", "FaceRetouchingNatural", "Spa", "Movie",
        "Theaters", "LibraryMusic", "Headphones", "VideogameAsset",
        "LocalPizza", "LocalCafe", "LocalDining", "Brush", "Palette",
        "Computer", "PhoneIphone", "CameraAlt", "Map", "CrueltyFree",
        "PedalBike", "AutoAwesome", "Celebration", "Cake", "EmojiEmotions",
        "Favorite", "Mood", "SelfImprovement", "EmojiObjects", "RocketLaunch"
    )

    // Delete Confirmation Dialog
    if (categoryToDelete != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Xác nhận xóa?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa danh mục '${categoryToDelete?.name}'? Hành động này không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete?.let { cat ->
                            viewModel.deleteCategory(cat)
                            viewModel.showSuccessNotification("Xóa danh mục thành công")
                        }
                        categoryToDelete = null
                    }
                ) {
                    Text("XÓA", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("HỦY")
                }
            }
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { 
            if (showAddForm) {
                showAddForm = false
                categoryToEdit = null
            } else {
                onDismiss()
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(16.dp)
                .testTag("category_management_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header (Pinned)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showAddForm) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = { 
                                showAddForm = false 
                                categoryToEdit = null
                            }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                            }
                            Text(if(categoryToEdit == null) "THÊM DANH MỤC MỚI" else "SỬA DANH MỤC", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text("QUẢN LÝ DANH MỤC LƯỚI", fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    IconButton(onClick = { 
                        if (showAddForm) {
                            showAddForm = false 
                            categoryToEdit = null
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                if (!showAddForm) {
                    // Modern Tab selection segment
                    TabRow(
                        selectedTabIndex = if (selectedTypeTab == "EXPENSE") 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.then(
                                    with(TabRowDefaults) {
                                        Modifier.tabIndicatorOffset(tabPositions[if (selectedTypeTab == "EXPENSE") 0 else 1])
                                    }
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTypeTab == "EXPENSE",
                            onClick = { selectedTypeTab = "EXPENSE"; type = "EXPENSE" },
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Text("KHOẢN CHI", fontWeight = FontWeight.Black, fontSize = 12.sp) 
                                }
                            }
                        )
                        Tab(
                            selected = selectedTypeTab == "INCOME",
                            onClick = { selectedTypeTab = "INCOME"; type = "INCOME" },
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("KHOẢN THU", fontWeight = FontWeight.Black, fontSize = 12.sp) 
                                }
                            }
                        )
                    }

                    Button(
                        onClick = { 
                            categoryToEdit = null
                            name = ""
                            parentName = null
                            type = selectedTypeTab
                            isCustomColorActive = false
                            selectedColor = colorPalette[0]
                            showAddForm = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("add_category_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tạo danh mục mới", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Hạng mục hiển thị (Sắp xếp theo thứ tự ưu tiên)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val currentFilterList = categoriesList.filter { it.type == selectedTypeTab || it.type == "BOTH" }
                    
                    if (currentFilterList.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Chưa có danh mục nào được lưu.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Hãy chạm nút phía trên để thêm mới!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    } else {
                        SortableCategoryList(
                            categories = currentFilterList,
                            viewModel = viewModel,
                            typeTab = selectedTypeTab,
                            onAddSubcategory = { parentNameValue ->
                                categoryToEdit = null
                                name = ""
                                isCustomColorActive = false
                                selectedColor = colorPalette[0]
                                type = selectedTypeTab
                                parentName = parentNameValue
                                showAddForm = true
                            },
                            onDeleteRequest = { cat ->
                                categoryToDelete = cat
                            },
                            onEditRequest = { cat ->
                                categoryToEdit = cat
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Tên danh mục (ví dụ: Mua sắm, Lương...)") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("category_name_input")
                        )

                        var categoryDropdownExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = parentName ?: "Không thuộc mục cha (Hạng mục chính)",
                                onValueChange = {},
                                readOnly = true,
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Danh mục trực thuộc") },
                                trailingIcon = {
                                    IconButton(onClick = { categoryDropdownExpanded = !categoryDropdownExpanded }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { categoryDropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Không thuộc mục cha (Hạng mục chính)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        parentName = null
                                        categoryDropdownExpanded = false
                                    }
                                )
                                val parentCandidates = categoriesList.filter { (it.type == type || it.type == "BOTH") && it.parentName == null }
                                parentCandidates.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                val colorValue = try { FormatHelper.parseColor(p.colorHex) } catch(e:Exception){ Color.Gray }
                                                Icon(IconMapper.getIconByName(p.iconName), contentDescription = p.name, tint = colorValue, modifier = Modifier.size(20.dp))
                                                Text(p.name, fontSize = 14.sp)
                                            }
                                        },
                                        onClick = {
                                            parentName = p.name
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Mã màu đại diện", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Row 1: First 8 colors
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        colorPalette.take(8).forEach { colorStr ->
                                            val isSelected = !isCustomColorActive && selectedColor == colorStr
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(FormatHelper.parseColor(colorStr))
                                                    .clickable { 
                                                        isCustomColorActive = false
                                                        selectedColor = colorStr
                                                    }
                                                    .border(
                                                        BorderStroke(
                                                            width = if (isSelected) 3.dp else 0.dp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Row 2: Next 7 colors + Custom color circle (8th item)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        colorPalette.drop(8).take(7).forEach { colorStr ->
                                            val isSelected = !isCustomColorActive && selectedColor == colorStr
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(FormatHelper.parseColor(colorStr))
                                                    .clickable { 
                                                        isCustomColorActive = false
                                                        selectedColor = colorStr
                                                    }
                                                    .border(
                                                        BorderStroke(
                                                            width = if (isSelected) 3.dp else 0.dp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        ),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val currentCustColor = try {
                                            FormatHelper.parseColor(customColorHex)
                                        } catch (e: Exception) {
                                            Color.Gray
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(currentCustColor)
                                                .clickable { 
                                                    isCustomColorActive = true
                                                    selectedColor = customColorHex
                                                }
                                                .border(
                                                    BorderStroke(
                                                        width = if (isCustomColorActive) 3.dp else 0.dp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Palette,
                                                contentDescription = "Custom color",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (isCustomColorActive) {
                                        com.app.ui.components.ColorSliderPicker(
                                            initialColorHex = selectedColor,
                                            onColorChanged = { newHex ->
                                                selectedColor = newHex
                                                customColorHex = newHex
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Biểu tượng hiển thị", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    iconPalette.chunked(7).forEach { rowIcons ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
                                        ) {
                                            rowIcons.forEach { iconName ->
                                                val isSelected = selectedIcon == iconName
                                                IconButton(
                                                    onClick = { selectedIcon = iconName },
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary 
                                                            else Color.Transparent,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                ) {
                                                    Icon(
                                                        imageVector = IconMapper.getIconByName(iconName),
                                                        contentDescription = iconName,
                                                        tint = if (isSelected) Color.White 
                                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
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

        // Footer (Pinned at bottom)
                if (showAddForm) {
                    val localContext = androidx.compose.ui.platform.LocalContext.current
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                if (categoryToEdit == null) {
                                    viewModel.addCategory(name, selectedIcon, selectedColor, type, parentName)
                                    viewModel.showSuccessNotification("Thêm danh mục thành công!")
                                } else {
                                    viewModel.updateCategory(categoryToEdit!!, name, selectedIcon, selectedColor, type, parentName)
                                    viewModel.showSuccessNotification("Cập nhật danh mục thành công!")
                                }
                                categoryToEdit = null
                                name = ""
                                parentName = null
                                showAddForm = false
                            } else {
                                viewModel.showWarningNotification("Vui lòng nhập tên danh mục!")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_category_confirm"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lưu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun SortableCategoryList(
    categories: List<FinanceCategory>,
    viewModel: FinanceViewModel,
    typeTab: String,
    onAddSubcategory: (parentName: String) -> Unit,
    onDeleteRequest: (FinanceCategory) -> Unit,
    onEditRequest: (FinanceCategory) -> Unit
) {
    val roots = remember(categories) { categories.filter { it.parentName == null } }
    val listState = remember(roots) { mutableStateListOf<FinanceCategory>().apply { addAll(roots) } }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var driftY by remember { mutableStateOf(0f) }

    val onDragReleased = {
        viewModel.updateCategoriesOrder(listState.toList(), typeTab)
        draggedIndex = null
        driftY = 0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listState.forEachIndexed { index, cat ->
            val isDragged = draggedIndex == index
            val verticalOffset = if (isDragged) driftY else 0f
            val zIndexValue = if (isDragged) 10f else 1f
            val scaleValue = if (isDragged) 1.04f else 1f

            val colorValue = try {
                FormatHelper.parseColor(cat.colorHex)
            } catch (e: Exception) {
                MaterialTheme.colorScheme.primary
            }

            var isExpanded by remember { mutableStateOf(false) }
            val subcats = categories.filter { it.parentName == cat.name }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(zIndexValue)
                    .graphicsLayer {
                        translationY = verticalOffset
                        scaleX = scaleValue
                        scaleY = scaleValue
                    }
                    .background(Color.Transparent)
                    .border(
                        width = if (isDragged) 2.dp else 1.dp,
                        color = if (isDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f) 
                                         else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Kéo để sắp xếp",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(4.dp)
                                        .pointerInput(index) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedIndex = index
                                                    driftY = 0f
                                                },
                                                onDragEnd = { onDragReleased() },
                                                onDragCancel = { onDragReleased() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    driftY += dragAmount.y
                                                    val itemHeightPx = 56.dp.toPx()
                                                    val targetIdx = draggedIndex
                                                    if (targetIdx != null) {
                                                        if (driftY > itemHeightPx * 0.8f && targetIdx < listState.lastIndex) {
                                                            val next = listState[targetIdx + 1]
                                                            listState[targetIdx + 1] = listState[targetIdx]
                                                            listState[targetIdx] = next
                                                            draggedIndex = targetIdx + 1
                                                            driftY -= itemHeightPx
                                                        } else if (driftY < -itemHeightPx * 0.8f && targetIdx > 0) {
                                                            val prev = listState[targetIdx - 1]
                                                            listState[targetIdx - 1] = listState[targetIdx]
                                                            listState[targetIdx] = prev
                                                            draggedIndex = targetIdx - 1
                                                            driftY += itemHeightPx
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                )

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(colorValue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIconByName(cat.iconName),
                                        contentDescription = cat.name,
                                        tint = colorValue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = cat.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (subcats.isNotEmpty()) {
                                        Text(
                                            text = "${subcats.size} mục con",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Mở rộng",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )

                                if (listState.size > 1) {
                                    IconButton(
                                        onClick = { onEditRequest(cat) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Sửa danh mục",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteRequest(cat) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa danh mục",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Subcategories
                        if (isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(start = 24.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                subcats.forEach { subCat ->
                                    val subColor = try { FormatHelper.parseColor(subCat.colorHex) } catch(e: Exception) { Color.Gray }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(subColor.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = IconMapper.getIconByName(subCat.iconName),
                                                    contentDescription = subCat.name,
                                                    tint = subColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = subCat.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onEditRequest(subCat) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit subcategory",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteRequest(subCat) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Delete subcategory",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Dotted add subcategory option card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onAddSubcategory(cat.name) }
                                        .drawBehind {
                                            drawRoundRect(
                                                color = colorValue.copy(alpha = 0.6f),
                                                style = Stroke(
                                                    width = 1.5.dp.toPx(),
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                                ),
                                                cornerRadius = CornerRadius(10.dp.toPx())
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add subcategory",
                                            tint = colorValue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Thêm mục con cho ${cat.name}",
                                            color = colorValue,
                                            fontSize = 12.sp,
                                             fontWeight = FontWeight.Bold
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
 
 // ==========================================
 // 3. SAVINGS MANAGEMENT DIALOG
 // ==========================================
 @Composable
 fun SavingsManagementDialog(
     viewModel: FinanceViewModel,
     onDismiss: () -> Unit
 ) {
     val focusManager = LocalFocusManager.current
     val context = LocalContext.current
     val dailyWallets by viewModel.dailyWallets.collectAsState()
     val savingsWallets by viewModel.savingsWallets.collectAsState()
     val savingsTransactions by viewModel.savingsTransactions.collectAsState()
     
     // Quick Add Savings Wallet State
     var showQuickAddWallet by remember { mutableStateOf(false) }
     var newSavingsWalletName by remember { mutableStateOf("") }
     var newSavingsWalletGoalStr by remember { mutableStateOf("") }
     
     // Transaction Panel State
     var selectedWalletId by remember { mutableStateOf<Int?>(null) }
     var isDeposit by remember { mutableStateOf(true) } // true for Deposit (Gửi), false for withdraw (Rút)
     var amountStr by remember { mutableStateOf("") }
     var note by remember { mutableStateOf("") }
     var selectedDailyWalletId by remember { mutableStateOf<Int?>(null) } // Everyday transaction source / target
     
     var savingsWalletToDelete by remember { mutableStateOf<com.app.data.Wallet?>(null) }

     if (savingsWalletToDelete != null) {
         AlertDialog(
             onDismissRequest = { savingsWalletToDelete = null },
             title = { Text("Xác nhận xóa hũ tiết kiệm?", fontWeight = FontWeight.Bold) },
             text = { Text("Bạn có chắc chắn muốn xóa hũ tiết kiệm '${savingsWalletToDelete?.name}'? Hành động này cũng sẽ xóa lịch sử tích lũy liên quan và không thể phục hồi.") },
             confirmButton = {
                 TextButton(
                     onClick = {
                         savingsWalletToDelete?.let { wallet ->
                             viewModel.deleteWallet(wallet)
                             viewModel.showSuccessNotification("Xóa hũ tiết kiệm thành công")
                         }
                         savingsWalletToDelete = null
                     }
                 ) {
                     Text("XÓA", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                 }
             },
             dismissButton = {
                 TextButton(onClick = { savingsWalletToDelete = null }) {
                     Text("HỦY")
                 }
             }
         )
     }
    
    val totalSavings = remember(savingsWallets) { savingsWallets.sumOf { it.balance } }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
            .testTag("savings_management_dialog"),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("QUẢN LÝ SỔ & HŨ TIẾT KIỆM", fontSize = 18.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SAVINGS KPI TOTAL ACCUMULATOR
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Tổng quỹ tiết kiệm", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = FormatHelper.formatVND(totalSavings),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // --- SAVINGS WALLETS COLUMN ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Danh sách", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { showQuickAddWallet = !showQuickAddWallet }) {
                        Icon(imageVector = if (showQuickAddWallet) Icons.Default.Remove else Icons.Default.AddCircle, contentDescription = "Toggle add", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (showQuickAddWallet) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("KHỞI TẠO HŨ TIẾT KIỆM MỚI", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = newSavingsWalletName,
                                onValueChange = { newSavingsWalletName = it },
                                label = { Text("Tên hũ tích lũy") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            com.app.ui.components.CustomMoneyInputField(
                                value = newSavingsWalletGoalStr,
                                onValueChange = { newSavingsWalletGoalStr = it },
                                label = "Số dư tích lũy ban đầu (đ)",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (newSavingsWalletName.isNotBlank()) {
                                        val initialBalance = newSavingsWalletGoalStr.toDoubleOrNull() ?: 0.0
                                        viewModel.addWallet(
                                            name = newSavingsWalletName,
                                            type = "SAVINGS",
                                            initialBalance = initialBalance,
                                            colorHex = "#9C27B0", // Savings Purple standard
                                            iconName = "Savings"
                                        )
                                        viewModel.showSuccessNotification("Khởi tạo hũ tích lũy thành công!")
                                        newSavingsWalletName = ""
                                        newSavingsWalletGoalStr = ""
                                        showQuickAddWallet = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Khởi tạo hũ")
                            }
                        }
                    }
                }

                if (savingsWallets.isEmpty()) {
                    Text("Chưa có hũ tiết kiệm nào. Vui lòng bấm dấu (+) bên trên để khởi tạo hũ!", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    savingsWallets.forEach { wt ->
                        ListItem(
                            headlineContent = { Text(wt.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Hũ Tích Lũy • ${FormatHelper.formatVND(wt.balance)}") },
                            leadingContent = {
                                Icon(imageVector = Icons.Default.Savings, contentDescription = "Savings", tint = FormatHelper.parseColor(wt.colorHex))
                            },
                            trailingContent = {
                                IconButton(onClick = { savingsWalletToDelete = wt }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // --- SAVINGS TRANSACTION ACTION FORM ---
                if (savingsWallets.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("THỰC HIỆN GIAO DỊCH TIẾT KIỆM", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            
                            // Gửi hoặc Rút Tab rows
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { isDeposit = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDeposit) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isDeposit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Nạp", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Button(
                                    onClick = { isDeposit = false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isDeposit) Color(0xFFF44336) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (!isDeposit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Rút", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }

                            // Choose Savings target wallet
                            Column {
                                Text("Lựa Chọn Hũ Tiết Kiệm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (selectedWalletId == null && savingsWallets.isNotEmpty()) {
                                        selectedWalletId = savingsWallets.first().id
                                    }
                                    
                                    savingsWallets.forEach { wt ->
                                        FilterChip(
                                            selected = selectedWalletId == wt.id,
                                            onClick = { selectedWalletId = wt.id },
                                            label = { Text(wt.name) }
                                        )
                                    }
                                }
                            }

                            // Amount input
                            CustomMoneyInputField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                label = "Số tiền",
                                modifier = Modifier.fillMaxWidth().testTag("savings_amount_input")
                            )

                            // Note input
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Nội dung ghi chú") },
                                modifier = Modifier.fillMaxWidth().testTag("savings_note_input")
                            )

                            // Source Wallet option: Trích xuất từ ví hằng ngày hay không?
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isDeposit) "Trích xuất từ ví thường:" else "Chuyển tiền về ví thường:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val checkedVal = selectedDailyWalletId != null
                                    Switch(
                                        checked = checkedVal,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked && dailyWallets.isNotEmpty()) {
                                                selectedDailyWalletId = dailyWallets.first().id
                                            } else {
                                                selectedDailyWalletId = null
                                            }
                                        }
                                    )
                                }
                                
                                if (selectedDailyWalletId != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        dailyWallets.forEach { wt ->
                                            FilterChip(
                                                selected = selectedDailyWalletId == wt.id,
                                                onClick = { selectedDailyWalletId = wt.id },
                                                label = { Text("${wt.name} (${FormatHelper.formatVND(wt.balance)})") }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                                    val tgtWalletId = selectedWalletId
                                    if (amount > 0.0 && tgtWalletId != null) {
                                        val now = System.currentTimeMillis()
                                        
                                        if (isDeposit) {
                                            // 1. Double Entry logic for DEPOSIT
                                            // Add Income transaction on the Savings Wallet
                                            viewModel.addTransaction(
                                                walletId = tgtWalletId,
                                                type = "INCOME",
                                                amount = amount,
                                                categoryName = "Tiết kiệm",
                                                note = note.ifEmpty { "Gửi tiền hũ tiết kiệm" },
                                                timestamp = now
                                            )
                                            
                                            // Subtract Expense on the Everyday Source Wallet (if selected)
                                            selectedDailyWalletId?.let { srcId ->
                                                viewModel.addTransaction(
                                                    walletId = srcId,
                                                    type = "EXPENSE",
                                                    amount = amount,
                                                    categoryName = "Tiết kiệm",
                                                    note = note.ifEmpty { "Nạp quỹ tiết kiệm" },
                                                    timestamp = now
                                                )
                                            }
                                        } else {
                                            // 2. Double Entry logic for WITHDRAW
                                            // Add Expense transaction on the Savings Wallet
                                            viewModel.addTransaction(
                                                walletId = tgtWalletId,
                                                type = "EXPENSE",
                                                amount = amount,
                                                categoryName = "Tiết kiệm",
                                                note = note.ifEmpty { "Rút tiền hũ tiết kiệm" },
                                                timestamp = now
                                            )
                                            
                                            // Add Income on the Everyday Dest Wallet (if selected)
                                            selectedDailyWalletId?.let { destId ->
                                                viewModel.addTransaction(
                                                    walletId = destId,
                                                    type = "INCOME",
                                                    amount = amount,
                                                    categoryName = "Tiết kiệm",
                                                    note = note.ifEmpty { "Nhận tiền từ hũ" },
                                                    timestamp = now
                                                )
                                            }
                                        }
                                        
                                        // Reset inputs
                                        amountStr = ""
                                        note = ""
                                        selectedDailyWalletId = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("add_savings_transaction_confirm"),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            ) {
                                Text(if (isDeposit) "Gửi Tiết Kiệm" else "Rút Tiết Kiệm")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // --- SAVINGS TRANSACTION TIMELINE HISTORY ---
                Text("LỊCH SỬ GIAO DỊCH TIẾT KIỆM", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                
                if (savingsTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Không có lịch sử biến động hũ tiết kiệm.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val groupedTxs = remember(savingsTransactions) {
                        savingsTransactions.groupBy { FormatHelper.formatDate(it.timestamp) }
                    }
                    groupedTxs.forEach { (dateStr, txList) ->
                        // Timeline milestone / date group header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dateStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        txList.forEach { tx ->
                            val isPositive = tx.type == "INCOME" || (tx.type == "ADJUSTMENT" && tx.note.contains("tăng"))
                            ListItem(
                                headlineContent = { 
                                    Text(
                                        text = tx.note, 
                                        fontWeight = FontWeight.Medium, 
                                        fontSize = 13.sp, 
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                supportingContent = {
                                    Text(
                                        text = "${tx.walletName} • ${SimpleDateFormat("HH:mm", java.util.Locale.Builder().setLanguage("vi").setRegion("VN").build()).format(tx.timestamp)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (isPositive) Icons.Default.Add else Icons.Default.Remove,
                                        contentDescription = tx.type,
                                        tint = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = "${if (isPositive) "+" else "-"}${FormatHelper.formatVND(tx.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("close_savings_management_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Đóng", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    )
}
