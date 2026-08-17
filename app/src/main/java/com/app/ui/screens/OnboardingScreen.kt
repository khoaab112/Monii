package com.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Build
import androidx.compose.foundation.Image
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.R
import com.app.ui.FinanceViewModel
import com.app.ui.FinanceViewModel.GoogleOnboardingResult
import com.app.ui.components.AppNotificationDialog
import com.app.ui.components.DialogButtonConfig

@Composable
fun OnboardingScreen(
    viewModel: FinanceViewModel,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncStatus by viewModel.syncStatus.collectAsState()
    
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
    val welcomeGifPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(R.drawable.welcome)
            .build(),
        imageLoader = gifImageLoader
    )
    
    var isWaitingForSync by remember { mutableStateOf(false) }

    // System AppNotificationDialog state management
    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("Thông báo") }
    var dialogContent by remember { mutableStateOf("") }
    var dialogConfirmText by remember { mutableStateOf("Đóng") }
    var dialogOnConfirmAction by remember { mutableStateOf<() -> Unit>({}) }

    fun triggerNotificationDialog(
        title: String = "Thông báo",
        content: String,
        confirmText: String = "Đóng",
        onConfirm: () -> Unit
    ) {
        dialogTitle = title
        dialogContent = content
        dialogConfirmText = confirmText
        dialogOnConfirmAction = onConfirm
        showDialog = true
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                
                isWaitingForSync = true
                viewModel.checkAndPerformGoogleSignInOnboarding(context) { onboardingResult ->
                    isWaitingForSync = false
                    if (onboardingResult !is GoogleOnboardingResult.Error) {
                        viewModel.toggleCloudSync(true)
                    }
                    when (onboardingResult) {
                        is GoogleOnboardingResult.ExistingUserSuccess -> {
                            // CASE 2: Đã có thư mục + File hợp lệ
                            triggerNotificationDialog(
                                title = "Thông báo",
                                content = onboardingResult.message,
                                confirmText = "Đóng",
                                onConfirm = {
                                    showDialog = false
                                    onComplete()
                                }
                            )
                        }
                        is GoogleOnboardingResult.InvalidFileNewUser -> {
                            // CASE 1: Đã có thư mục nhưng không có file hoặc file lỗi định dạng
                            triggerNotificationDialog(
                                title = "Thông báo",
                                content = onboardingResult.message,
                                confirmText = "Đóng",
                                onConfirm = {
                                    showDialog = false
                                    onComplete()
                                }
                            )
                        }
                        is GoogleOnboardingResult.NoFolderNewUser -> {
                            // CASE 3: Không có thư mục -> Coi là New User
                            triggerNotificationDialog(
                                title = "Thông báo",
                                content = onboardingResult.message,
                                confirmText = "Đóng",
                                onConfirm = {
                                    showDialog = false
                                    onComplete()
                                }
                            )
                        }
                        is GoogleOnboardingResult.Error -> {
                            viewModel.showWarningNotification(onboardingResult.message)
                        }
                    }
                }
            } catch (e: Exception) {
                isWaitingForSync = false
                viewModel.showWarningNotification("Lỗi đăng nhập Google: ${e.message}")
            }
        } else {
            isWaitingForSync = false
            viewModel.showWarningNotification("Đăng nhập bị hủy.")
        }
    }

    val backupFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            isWaitingForSync = true
            viewModel.importLocalBackup(context, it) { success ->
                isWaitingForSync = false
                if (success) {
                    triggerNotificationDialog(
                        title = "Thông báo",
                        content = "Chào mừng bạn đã quay trở lại",
                        confirmText = "Đóng",
                        onConfirm = {
                            showDialog = false
                            onComplete()
                        }
                    )
                } else {
                    triggerNotificationDialog(
                        title = "Thông báo",
                        content = "Không tìm thấy tệp sao lưu hợp lệ. Đã khởi tạo dữ liệu mới.",
                        confirmText = "Đóng",
                        onConfirm = {
                            showDialog = false
                            onComplete()
                        }
                    )
                }
            }
        }
    }

    // Soft multi-color mesh gradient background matching reference image (Lavender top -> Soft Pink bottom)
    val meshGradientBackground = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF3EEFE), // Soft Lavender/Blue at top
            Color(0xFFFFF0F5)  // Soft Pink/Peach at bottom
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(meshGradientBackground),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Main Card matching reference image (Tall white card with rounded 36.dp corners)
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(36.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, Color(0xFFF0F0F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Flexible space at the top to center content
                    Spacer(modifier = Modifier.weight(1f))

                    // 1. Header Title "Chào Mừng"
                    Text(
                        text = "Chào Mừng",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp)) // Shrinked space

                    // 2. Cat GIF Animation
                    Image(
                        painter = welcomeGifPainter,
                        contentDescription = "Welcome Animation",
                        modifier = Modifier.size(135.dp)
                    )

                    // Fixed small space between GIF and Action Buttons
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // 1. Primary Filled Blue Button "Người dùng mới"
                    Button(
                        onClick = {
                            triggerNotificationDialog(
                                title = "Thông báo",
                                content = "Chào mừng bạn đến với Ứng dụng lịch sử chi tiêu",
                                confirmText = "Đóng",
                                onConfirm = {
                                    showDialog = false
                                    onComplete()
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonOutline,
                                contentDescription = "New User Icon",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Người dùng mới",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Divider Section "———— hoặc ————"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = Color(0xFFE5E7EB)
                        )
                        Text(
                            text = "hoặc",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = Color(0xFFE5E7EB)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // 2. Outlined Button "Đăng nhập bằng Google"
                    OutlinedButton(
                        onClick = {
                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                            )
                                .requestEmail()
                                .requestScopes(
                                    com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"),
                                    com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.readonly")
                                )
                                .build()
                            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                            signInLauncher.launch(client.signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F2937)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Đăng nhập bằng Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Outlined Button "Tải lên tệp backup (JSON)"
                    OutlinedButton(
                        onClick = {
                            backupFilePickerLauncher.launch("application/json")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F2937)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Backup JSON Icon",
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF4B5563)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Tải lên tệp backup (JSON)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }
                    
                    // Flexible space at the bottom to center content
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Loading Overlay when checking Google Drive or performing sync
            if (syncStatus == "SYNCING" && isWaitingForSync) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            com.app.ui.components.AppLoadingIndicator(size = 56.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đang kiểm tra dữ liệu đám mây...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

            // System Reusable AppNotificationDialog rendered at highest z-index
            AppNotificationDialog(
                showDialog = showDialog,
                title = dialogTitle,
                content = dialogContent,
                confirmButton = DialogButtonConfig(
                    text = dialogConfirmText,
                    action = dialogOnConfirmAction
                ),
                onDismissRequest = {
                    showDialog = false
                    dialogOnConfirmAction()
                }
            )
        }
    }
}
