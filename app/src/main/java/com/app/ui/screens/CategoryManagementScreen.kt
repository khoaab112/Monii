package com.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.app.data.Categories
import com.app.data.FinanceCategory
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import com.app.ui.IconMapper
import com.app.ui.components.AppModalBottomSheet
import com.app.ui.components.AppNotificationDialog
import com.app.ui.components.DialogButtonConfig
import android.os.Build
import androidx.compose.foundation.Image
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale

// Custom Dashed Border Modifier for Add Sub-Category Button
fun Modifier.dashedBorder(
    strokeWidth: Dp = 1.5.dp,
    color: Color = Color(0xFFFFB74D),
    cornerRadius: Dp = 16.dp
) = this.drawWithContent {
    drawContent()
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
    )
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
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
            delayMillis = (index * 45).coerceAtMost(250),
            easing = LinearOutSlowInEasing
        ),
        label = "cat_stagger_alpha_$index"
    )

    val offsetYProgress by animateDpAsState(
        targetValue = if (cardVisible || alreadySeen) 0.dp else 24.dp,
        animationSpec = if (alreadySeen) snap() else tween(
            durationMillis = 400,
            delayMillis = (index * 45).coerceAtMost(250),
            easing = LinearOutSlowInEasing
        ),
        label = "cat_stagger_offset_$index"
    )

    return this.graphicsLayer {
        alpha = alphaProgress
        translationY = offsetYProgress.toPx()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val seenKeys = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } }
    )) { mutableStateListOf<String>() }

    val focusManager = LocalFocusManager.current
    val categoriesList by viewModel.categoriesList.collectAsState()
    
    // Tab State (EXPENSE or INCOME)
    var selectedTypeTab by remember { mutableStateOf("EXPENSE") }
    var isReorderMode by remember { mutableStateOf(false) }
    
    // State variables for details and bottom sheet
    var detailCategoryName by remember { mutableStateOf<String?>(null) }
    var showBottomSheetCategory by remember { mutableStateOf<FinanceCategory?>(null) }
    
    // Form States for Add/Edit Category Sub-Form
    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var parentName by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedColor by remember { mutableStateOf("#F44336") }
    var selectedIcon by remember { mutableStateOf("Restaurant") }
    var isCustomColorActive by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#9C27B0") }
    
    // Dialog States
    var categoryToDelete by remember { mutableStateOf<FinanceCategory?>(null) }
    var categoryToEdit by remember { mutableStateOf<FinanceCategory?>(null) }
    var showTreeDialog by remember { mutableStateOf(false) }
    var showSyncSystemCategoriesSheet by remember { mutableStateOf(false) }

    // Scroll state for list view
    val scrollState = rememberScrollState()

    // Colors & Icons
    val colorPalette = remember {
        listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
            "#009688", "#4CAF50", "#8BC34A", "#FFEB3B",
            "#FF9800", "#795548", "#607D8B", "#455A64"
        )
    }
    val iconPalette = remember {
        listOf(
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
    }

    // Populate form fields when editing
    LaunchedEffect(categoryToEdit) {
        val editCat = categoryToEdit
        if (editCat != null) {
            name = editCat.name
            parentName = editCat.parentName
            type = editCat.type
            selectedIcon = editCat.iconName
            selectedColor = editCat.colorHex
            isCustomColorActive = !colorPalette.contains(editCat.colorHex)
            if (isCustomColorActive) {
                customColorHex = editCat.colorHex
            }
            showAddForm = true
        }
    }

    // Delete Confirmation Dialog
    if (categoryToDelete != null) {
        AppNotificationDialog(
            showDialog = categoryToDelete != null,
            title = "Xác nhận xóa?",
            content = "Bạn có chắc chắn muốn xóa danh mục '${categoryToDelete?.name}'? Các giao dịch liên quan sẽ không bị mất dữ liệu.",
            cancelButton = DialogButtonConfig(
                text = "HỦY",
                action = { categoryToDelete = null }
            ),
            confirmButton = DialogButtonConfig(
                text = "XÓA",
                action = {
                    categoryToDelete?.let { cat ->
                        viewModel.deleteCategory(cat)
                        viewModel.showSuccessNotification("Xóa danh mục thành công")
                    }
                    categoryToDelete = null
                },
                containerColor = Color(0xFFF44336),
                contentColor = Color.White
            ),
            onDismissRequest = { categoryToDelete = null }
        )
    }

    if (showTreeDialog) {
        CategoryTreeDialog(
            categoriesList = categoriesList,
            onDismiss = { showTreeDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (showAddForm) {
                                showAddForm = false
                                categoryToEdit = null
                            } else if (detailCategoryName != null) {
                                detailCategoryName = null
                                isReorderMode = false
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Trở lại"
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showAddForm) {
                                if (categoryToEdit == null) {
                                    if (parentName != null) "Thêm mục con" else "Thêm danh mục mới"
                                } else "Sửa danh mục"
                            } else if (detailCategoryName != null) {
                                detailCategoryName!!
                            } else {
                                "Quản lý danh mục"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Action Icons on top right
                    if (!showAddForm) {
                        if (isReorderMode) {
                            TextButton(onClick = { isReorderMode = false }) {
                                Text("Xong", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        } else {
                            if (detailCategoryName != null) {
                                val parentCat = categoriesList.find { it.name.lowercase() == detailCategoryName?.lowercase() }
                                IconButton(onClick = {
                                    parentCat?.let { showBottomSheetCategory = it }
                                }) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Tùy chọn")
                                }
                            } else {
                                Row {
                                    IconButton(onClick = { showSyncSystemCategoriesSheet = true }) {
                                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Tải danh mục hệ thống")
                                    }
                                    IconButton(onClick = { showTreeDialog = true }) {
                                        Icon(imageVector = Icons.Default.AccountTree, contentDescription = "Sơ đồ")
                                    }
                                    IconButton(onClick = { isReorderMode = true }) {
                                        Icon(imageVector = Icons.Default.SwapVert, contentDescription = "Sắp xếp")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Floating action button on the main categories list screen
            if (!showAddForm && detailCategoryName == null && !isReorderMode) {
                val fabColor = if (selectedTypeTab == "INCOME") Color(0xFF2E7D32) else Color(0xFFE53935)
                FloatingActionButton(
                    onClick = {
                        categoryToEdit = null
                        name = ""
                        parentName = null
                        type = selectedTypeTab
                        isCustomColorActive = false
                        selectedColor = colorPalette[0]
                        showAddForm = true
                    },
                    containerColor = fabColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Thêm", modifier = Modifier.size(28.dp))
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFFBFBFB))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            if (!showAddForm) {
                if (detailCategoryName == null) {
                    // --- 1. Parent Categories List ---
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Tab switcher: green/red Pill tab switcher
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .staggeredEntrance(0, "cat_tab_switcher", seenKeys)
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F4)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isIncomeSelected = selectedTypeTab == "INCOME"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isIncomeSelected) Color(0xFF2E7D32)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedTypeTab = "INCOME"
                                            type = "INCOME"
                                            isReorderMode = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = null,
                                            tint = if (isIncomeSelected) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Khoản thu",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isIncomeSelected) Color.White else Color.Gray
                                        )
                                    }
                                }

                                val isExpenseSelected = selectedTypeTab == "EXPENSE"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isExpenseSelected) Color(0xFFE53935)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedTypeTab = "EXPENSE"
                                            type = "EXPENSE"
                                            isReorderMode = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Khoản chi",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isExpenseSelected) Color.White else Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter root categories
                        val roots = categoriesList.filter {
                            it.parentName == null && (it.type == selectedTypeTab || it.type == "BOTH")
                        }
                        val rootListState = remember(roots) { mutableStateListOf<FinanceCategory>().apply { addAll(roots) } }
                        LaunchedEffect(roots) {
                            rootListState.clear()
                            rootListState.addAll(roots)
                        }

                        var rootDraggedIndex by remember { mutableStateOf<Int?>(null) }
                        var rootDriftY by remember { mutableStateOf(0f) }

                        val onRootDragReleased = {
                            viewModel.updateCategoriesOrder(rootListState.toList(), selectedTypeTab)
                            rootDraggedIndex = null
                            rootDriftY = 0f
                        }

                        if (roots.isEmpty()) {
                            // --- Empty State ---
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    // Illustration representation
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .background(Color(0xFFF5F5F5), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB74D),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(Color(0xFFE53935), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Remove,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = if (selectedTypeTab == "INCOME") "Chưa có danh mục thu" else "Chưa có danh mục chi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Thêm danh mục đầu tiên để quản lý chi tiêu dễ dàng hơn",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.alpha(0.8f)
                                    )
                                }
                            }
                        } else {
                            // Standard Category List Column
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rootListState.forEachIndexed { index, cat ->
                                    val colorValue = remember(cat.colorHex) {
                                        try {
                                            FormatHelper.parseColor(cat.colorHex)
                                        } catch (e: Exception) {
                                            Color(0xFF4CAF50)
                                        }
                                    }
                                    val childrenCount = remember(cat.name, categoriesList) {
                                        categoriesList.count {
                                            it.parentName?.lowercase() == cat.name.lowercase()
                                        }
                                    }

                                    val isDragged = rootDraggedIndex == index
                                    val verticalOffset = if (isDragged) rootDriftY else 0f
                                    val zIndexValue = if (isDragged) 10f else 1f
                                    val scaleValue = if (isDragged) 1.04f else 1f

                                    val cardModifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index + 1, "cat_root_${cat.name}_$selectedTypeTab", seenKeys)
                                        .zIndex(zIndexValue)
                                        .graphicsLayer {
                                            translationY = verticalOffset
                                            scaleX = scaleValue
                                            scaleY = scaleValue
                                        }

                                    val dragModifier = if (isReorderMode) {
                                        cardModifier.pointerInput(index) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    rootDraggedIndex = index
                                                    rootDriftY = 0f
                                                },
                                                onDragEnd = { onRootDragReleased() },
                                                onDragCancel = { onRootDragReleased() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val targetIdx = rootDraggedIndex
                                                    if (targetIdx != null) {
                                                        val itemHeightPx = 68.dp.toPx()
                                                        val minAllowedDrift = if (targetIdx > 0) -itemHeightPx * 1.1f else 0f
                                                        val maxAllowedDrift = if (targetIdx < rootListState.lastIndex) itemHeightPx * 1.1f else 0f
                                                        rootDriftY = (rootDriftY + dragAmount.y).coerceIn(minAllowedDrift, maxAllowedDrift)

                                                        if (rootDriftY > itemHeightPx * 0.7f && targetIdx < rootListState.lastIndex) {
                                                            val next = rootListState[targetIdx + 1]
                                                            rootListState[targetIdx + 1] = rootListState[targetIdx]
                                                            rootListState[targetIdx] = next
                                                            rootDraggedIndex = targetIdx + 1
                                                            rootDriftY -= itemHeightPx
                                                        } else if (rootDriftY < -itemHeightPx * 0.7f && targetIdx > 0) {
                                                            val prev = rootListState[targetIdx - 1]
                                                            rootListState[targetIdx - 1] = rootListState[targetIdx]
                                                            rootListState[targetIdx] = prev
                                                            rootDraggedIndex = targetIdx - 1
                                                            rootDriftY += itemHeightPx
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        cardModifier.clickable { detailCategoryName = cat.name }
                                    }

                                    Card(
                                        modifier = dragModifier,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragged) 4.dp else 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Icon with colored circle/box background tint
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(colorValue.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = IconMapper.getIconByName(cat.iconName),
                                                        contentDescription = cat.name,
                                                        tint = colorValue,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = cat.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "$childrenCount mục con",
                                                        fontSize = 12.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }

                                            if (isReorderMode) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Sắp xếp",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                            } else {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    IconButton(onClick = { detailCategoryName = cat.name }) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowRight,
                                                            contentDescription = "Xem chi tiết",
                                                            tint = Color.Gray
                                                        )
                                                    }
                                                    IconButton(onClick = { showBottomSheetCategory = cat }) {
                                                        Icon(
                                                            imageVector = Icons.Default.MoreVert,
                                                            contentDescription = "Thêm/Sửa/Xóa",
                                                            tint = Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(80.dp)) // Avoid overlaps with FAB
                            }
                        }
                    }
                } else {
                    // --- 2. Category Details Sub-Categories List View (Image 3) ---
                    val parentCat = categoriesList.find { it.name.lowercase() == detailCategoryName?.lowercase() }
                    val parentColor = parentCat?.let {
                        try { FormatHelper.parseColor(it.colorHex) } catch (e: Exception) { Color(0xFF2E7D32) }
                    } ?: Color(0xFF2E7D32)

                    val subcats = categoriesList.filter {
                        it.parentName?.lowercase() == detailCategoryName?.lowercase()
                    }
                    val subListState = remember(subcats) { mutableStateListOf<FinanceCategory>().apply { addAll(subcats) } }

                    LaunchedEffect(subcats) {
                        subListState.clear()
                        subListState.addAll(subcats)
                    }

                    var draggedIndex by remember { mutableStateOf<Int?>(null) }
                    var driftY by remember { mutableStateOf(0f) }

                    val onDragReleased = {
                        viewModel.updateCategoriesOrder(subListState.toList(), selectedTypeTab)
                        draggedIndex = null
                        driftY = 0f
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            subListState.forEachIndexed { index, subcat ->
                                val isDragged = draggedIndex == index
                                val verticalOffset = if (isDragged) driftY else 0f
                                val zIndexValue = if (isDragged) 10f else 1f
                                val scaleValue = if (isDragged) 1.04f else 1f

                                val cardModifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index, "cat_sub_${subcat.name}_$detailCategoryName", seenKeys)
                                    .zIndex(zIndexValue)
                                    .graphicsLayer {
                                        translationY = verticalOffset
                                        scaleX = scaleValue
                                        scaleY = scaleValue
                                    }

                                val dragModifier = if (isReorderMode) {
                                    cardModifier.pointerInput(index) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedIndex = index
                                                driftY = 0f
                                            },
                                            onDragEnd = { onDragReleased() },
                                            onDragCancel = { onDragReleased() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val targetIdx = draggedIndex
                                                if (targetIdx != null) {
                                                    val itemHeightPx = 64.dp.toPx()
                                                    val minAllowedDrift = if (targetIdx > 0) -itemHeightPx * 1.1f else 0f
                                                    val maxAllowedDrift = if (targetIdx < subListState.lastIndex) itemHeightPx * 1.1f else 0f
                                                    driftY = (driftY + dragAmount.y).coerceIn(minAllowedDrift, maxAllowedDrift)

                                                    if (driftY > itemHeightPx * 0.7f && targetIdx < subListState.lastIndex) {
                                                        val next = subListState[targetIdx + 1]
                                                        subListState[targetIdx + 1] = subListState[targetIdx]
                                                        subListState[targetIdx] = next
                                                        draggedIndex = targetIdx + 1
                                                        driftY -= itemHeightPx
                                                    } else if (driftY < -itemHeightPx * 0.7f && targetIdx > 0) {
                                                        val prev = subListState[targetIdx - 1]
                                                        subListState[targetIdx - 1] = subListState[targetIdx]
                                                        subListState[targetIdx] = prev
                                                        draggedIndex = targetIdx - 1
                                                        driftY += itemHeightPx
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    cardModifier.clickable { categoryToEdit = subcat }
                                }

                                Card(
                                    modifier = dragModifier,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDragged) 4.dp else 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (isReorderMode) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Kéo thả",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(parentColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = IconMapper.getIconByName(subcat.iconName),
                                                    contentDescription = subcat.name,
                                                    tint = parentColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Text(
                                                text = subcat.name,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                        }

                                        if (!isReorderMode) {
                                            IconButton(onClick = { showBottomSheetCategory = subcat }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Lựa chọn",
                                                    tint = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Sub-category dashed box button "+ Thêm mục con"
                            if (!isReorderMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .dashedBorder(
                                            strokeWidth = 1.dp,
                                            color = Color.Gray.copy(alpha = 0.4f),
                                            cornerRadius = 12.dp
                                        )
                                        .clickable {
                                            categoryToEdit = null
                                            name = ""
                                            parentName = detailCategoryName
                                            selectedColor = parentCat?.colorHex ?: "#4CAF50"
                                            type = parentCat?.type ?: selectedTypeTab
                                            showAddForm = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Thêm mục con",
                                            tint = parentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Thêm mục con",
                                            color = parentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // --- 3. Add / Edit Sub-Form View (Image 4) ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (parentName != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Đang thêm mục con cho: '$parentName'", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên danh mục") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("category_name_input")
                    )

                    // Color Picker
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Màu đại diện", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                colorPalette.chunked(8).forEach { rowColors ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        rowColors.forEach { colorHex ->
                                            val isSelected = !isCustomColorActive && selectedColor == colorHex
                                            val parsedColor = try {
                                                FormatHelper.parseColor(colorHex)
                                            } catch (e: Exception) { Color.Gray }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(parsedColor)
                                                    .clickable {
                                                        isCustomColorActive = false
                                                        selectedColor = colorHex
                                                    }
                                                    .border(
                                                        width = if (isSelected) 3.dp else 0.dp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Màu tùy chỉnh:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                try { FormatHelper.parseColor(customColorHex) } catch (e: Exception) { Color.Magenta }
                                            )
                                            .clickable { isCustomColorActive = true }
                                            .border(
                                                width = if (isCustomColorActive) 3.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
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

                    // Icon Picker
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Biểu tượng hiển thị", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
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

                    // Save button matching Image 4 (Solid Green, "Lưu")
                    Button(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) {
                                viewModel.showWarningNotification("Vui lòng nhập tên danh mục!")
                            } else {
                                val isDuplicate = categoriesList.any {
                                    (categoryToEdit == null || it.name != categoryToEdit?.name) && it.name.trim().equals(trimmedName, ignoreCase = true)
                                }
                                if (isDuplicate) {
                                    viewModel.showWarningNotification("Tên danh mục '$trimmedName' đã tồn tại! Vui lòng chọn tên khác.")
                                } else {
                                    val finalColor = if (isCustomColorActive) customColorHex else selectedColor
                                    if (categoryToEdit == null) {
                                        viewModel.addCategory(trimmedName, selectedIcon, finalColor, type, parentName)
                                        viewModel.showSuccessNotification("Thêm danh mục thành công!")
                                    } else {
                                        viewModel.updateCategory(categoryToEdit!!, trimmedName, selectedIcon, finalColor, type, parentName)
                                        viewModel.showSuccessNotification("Cập nhật danh mục thành công!")
                                    }
                                    categoryToEdit = null
                                    name = ""
                                    parentName = null
                                    showAddForm = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_category_confirm"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Lưu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // --- Modal Bottom Sheet ---
    if (showBottomSheetCategory != null) {
        val cat = showBottomSheetCategory!!
        AppModalBottomSheet(
            onDismissRequest = { showBottomSheetCategory = null },
            title = cat.name,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Sửa danh mục
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            showBottomSheetCategory = null
                            categoryToEdit = cat
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sửa danh mục", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Parent-only features
                if (cat.parentName == null) {
                    // 2. Thêm mục con
                    if (detailCategoryName == null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showBottomSheetCategory = null
                                    categoryToEdit = null
                                    name = ""
                                    isCustomColorActive = false
                                    selectedColor = cat.colorHex
                                    type = cat.type
                                    parentName = cat.name
                                    showAddForm = true
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Thêm mục con", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 3. Sắp xếp mục con
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                showBottomSheetCategory = null
                                detailCategoryName = cat.name
                                isReorderMode = true
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Sắp xếp mục con", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 4. Xóa danh mục
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            showBottomSheetCategory = null
                            categoryToDelete = cat
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF44336))
                    Text("Xóa danh mục", color = Color(0xFFF44336), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ModalBottomSheet: Tải bổ sung danh mục mặc định từ hệ thống
    if (showSyncSystemCategoriesSheet) {
        val missingCategories = remember(categoriesList) {
            val existingNames = categoriesList.map { it.name.trim().lowercase() }.toSet()
            Categories.list.filter { sysCat ->
                sysCat.name.trim().lowercase() !in existingNames
            }
        }

        var selectedCategoryNames by remember(missingCategories) {
            mutableStateOf(missingCategories.map { it.name }.toSet())
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

        val lazyCatGifPainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(com.app.R.drawable.lazy_cat)
                .build(),
            imageLoader = gifImageLoader
        )

        AppModalBottomSheet(
            onDismissRequest = { showSyncSystemCategoriesSheet = false },
            title = "Bổ sung danh mục hệ thống",
            sheetState = sheetState,
            footer = if (missingCategories.isNotEmpty()) {
                {
                    Button(
                        onClick = {
                            val toAdd = missingCategories.filter { it.name in selectedCategoryNames }
                            if (toAdd.isNotEmpty()) {
                                viewModel.syncSystemCategories(toAdd)
                                viewModel.showSuccessNotification("Đã bổ sung ${toAdd.size} danh mục mới thành công!")
                            }
                            showSyncSystemCategoriesSheet = false
                        },
                        enabled = selectedCategoryNames.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_import_system_categories_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bổ sung ${selectedCategoryNames.size} danh mục đã chọn",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (missingCategories.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = lazyCatGifPainter,
                            contentDescription = "Lazy Cat GIF",
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "Bạn đã có đầy đủ tất cả danh mục của hệ thống!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Không có danh mục mới nào cần bổ sung.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "Dưới đây là các danh mục hệ thống chưa có trong tài khoản của bạn. Nhấp vào danh mục để chọn hoặc bỏ chọn:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Lưới hiển thị dạng 2 cột (Grid Layout)
                    val rows = remember(missingCategories) { missingCategories.chunked(2) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { cat ->
                                    val isSelected = cat.name in selectedCategoryNames
                                    val catColor = remember(cat.colorHex) {
                                        try { FormatHelper.parseColor(cat.colorHex) } catch (e: Exception) { Color(0xFF4CAF50) }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(2.dp, Color(0xFFFF3B30), RoundedCornerShape(14.dp))
                                                } else {
                                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                                }
                                            )
                                            .clickable {
                                                selectedCategoryNames = if (isSelected) {
                                                    selectedCategoryNames - cat.name
                                                } else {
                                                    selectedCategoryNames + cat.name
                                                }
                                            },
                                        color = if (isSelected) Color(0xFFFF3B30).copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(catColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = IconMapper.getIconByName(cat.iconName),
                                                    contentDescription = cat.name,
                                                    tint = catColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cat.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = if (cat.type == "INCOME") "Khoản thu" else if (cat.type == "EXPENSE") "Khoản chi" else "Dùng chung",
                                                    fontSize = 11.sp,
                                                    color = if (cat.type == "INCOME") Color(0xFF34C759) else Color(0xFFFF3B30)
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Đã chọn",
                                                    tint = Color(0xFFFF3B30),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (rowItems.size == 1) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTreeDialog(
    categoriesList: List<FinanceCategory>,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("EXPENSE") }
    val seenKeys = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } }
    )) { mutableStateListOf<String>() }
    
    val filteredCategories = categoriesList.filter { it.type == selectedTab || it.type == "BOTH" }
    val parentCats = filteredCategories.filter { it.parentName == null }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Sơ đồ danh mục",
        isScrollableContent = false,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            // Pill tab switcher for Expense & Income
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .staggeredEntrance(0, "tree_tab_switcher", seenKeys),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F4)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isExpenseSelected = selectedTab == "EXPENSE"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isExpenseSelected) Color(0xFFE53935)
                                else Color.Transparent
                            )
                            .clickable { selectedTab = "EXPENSE" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Khoản chi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isExpenseSelected) Color.White else Color.Gray
                        )
                    }

                    val isIncomeSelected = selectedTab == "INCOME"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isIncomeSelected) Color(0xFF2E7D32)
                                else Color.Transparent
                            )
                            .clickable { selectedTab = "INCOME" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Khoản thu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isIncomeSelected) Color.White else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tree Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (parentCats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == "EXPENSE") "Chưa có sơ đồ khoản chi nào" else "Chưa có sơ đồ khoản thu nào",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    parentCats.forEachIndexed { index, parentCat ->
                        val childCats = filteredCategories.filter { it.parentName?.lowercase() == parentCat.name.lowercase() }
                        CategoryTreeItem(
                            parentCat = parentCat,
                            childCats = childCats,
                            parentIndex = index,
                            selectedTab = selectedTab,
                            seenKeys = seenKeys
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CategoryTreeItem(
    parentCat: FinanceCategory,
    childCats: List<FinanceCategory>,
    parentIndex: Int,
    selectedTab: String,
    seenKeys: MutableList<String>
) {
    val parentColor = try { FormatHelper.parseColor(parentCat.colorHex) } catch(e: Exception) { Color.Gray }
    
    val parentKey = "tree_parent_${parentCat.name}_$selectedTab"
    val alreadySeenParent = remember(parentKey) { seenKeys.contains(parentKey) }
    var isParentVisible by rememberSaveable(parentKey) { mutableStateOf(alreadySeenParent) }

    LaunchedEffect(parentKey) {
        if (!alreadySeenParent) {
            isParentVisible = true
            seenKeys.add(parentKey)
        }
    }

    val parentAlpha by animateFloatAsState(
        targetValue = if (isParentVisible || alreadySeenParent) 1f else 0f,
        animationSpec = if (alreadySeenParent) snap() else tween(
            durationMillis = 500,
            delayMillis = (parentIndex * 90).coerceAtMost(350),
            easing = LinearOutSlowInEasing
        ),
        label = "tree_parent_alpha_$parentIndex"
    )

    val parentOffsetY by animateDpAsState(
        targetValue = if (isParentVisible || alreadySeenParent) 0.dp else 20.dp,
        animationSpec = if (alreadySeenParent) snap() else tween(
            durationMillis = 500,
            delayMillis = (parentIndex * 90).coerceAtMost(350),
            easing = LinearOutSlowInEasing
        ),
        label = "tree_parent_offset_$parentIndex"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = parentAlpha
                translationY = parentOffsetY.toPx()
            }
    ) {
        // Parent Node
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(parentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconMapper.getIconByName(parentCat.iconName), 
                    contentDescription = null,
                    tint = Color.White, 
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(parentCat.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        // Children Nodes
        if (childCats.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 15.dp) // 16dp center of 32dp circle minus 1dp line half-width
            ) {
                childCats.forEachIndexed { index, childCat ->
                    val isLast = index == childCats.size - 1
                    val childColor = try { FormatHelper.parseColor(childCat.colorHex) } catch(e: Exception) { Color.Gray }
                    
                    val childKey = "tree_child_${parentCat.name}_${childCat.name}_$selectedTab"
                    val alreadySeenChild = remember(childKey) { seenKeys.contains(childKey) }
                    var isChildVisible by rememberSaveable(childKey) { mutableStateOf(alreadySeenChild) }

                    LaunchedEffect(childKey) {
                        if (!alreadySeenChild) {
                            isChildVisible = true
                            seenKeys.add(childKey)
                        }
                    }

                    val parentBaseDelay = (parentIndex * 90).coerceAtMost(350) + 120
                    // Line drawing progress (0.0f -> 1.0f)
                    val lineProgress by animateFloatAsState(
                        targetValue = if (isChildVisible || alreadySeenChild) 1f else 0f,
                        animationSpec = if (alreadySeenChild) snap() else tween(
                            durationMillis = 420,
                            delayMillis = parentBaseDelay + (index * 130),
                            easing = FastOutSlowInEasing
                        ),
                        label = "tree_line_${parentCat.name}_$index"
                    )

                    // Child node appearance (pop in as line reaches)
                    val childAlpha by animateFloatAsState(
                        targetValue = if ((isChildVisible && lineProgress > 0.35f) || alreadySeenChild) 1f else 0f,
                        animationSpec = if (alreadySeenChild) snap() else tween(
                            durationMillis = 350,
                            easing = LinearOutSlowInEasing
                        ),
                        label = "tree_child_alpha_${parentCat.name}_$index"
                    )

                    val childScale by animateFloatAsState(
                        targetValue = if ((isChildVisible && lineProgress > 0.35f) || alreadySeenChild) 1f else 0.65f,
                        animationSpec = if (alreadySeenChild) snap() else tween(
                            durationMillis = 350,
                            easing = LinearOutSlowInEasing
                        ),
                        label = "tree_child_scale_${parentCat.name}_$index"
                    )

                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Animated Lines Canvas
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .width(30.dp)
                                .fillMaxHeight()
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            val lineColor = parentColor.copy(alpha = 0.55f)
                            
                            val startX = 1.dp.toPx()
                            val endX = size.width
                            val centerY = size.height / 2
                            val bottomY = if (isLast) centerY else size.height
                            
                            // Vertical Line (draws downwards)
                            val vertProgress = (lineProgress * 2f).coerceIn(0f, 1f)
                            if (vertProgress > 0f) {
                                drawLine(
                                    color = lineColor,
                                    start = androidx.compose.ui.geometry.Offset(startX, 0f),
                                    end = androidx.compose.ui.geometry.Offset(startX, bottomY * vertProgress),
                                    strokeWidth = strokeWidth
                                )
                            }
                            
                            // Horizontal Line (draws rightwards towards child node)
                            val horizProgress = ((lineProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            if (horizProgress > 0f) {
                                drawLine(
                                    color = lineColor,
                                    start = androidx.compose.ui.geometry.Offset(startX, centerY),
                                    end = androidx.compose.ui.geometry.Offset(startX + (endX - startX) * horizProgress, centerY),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }
                        
                        // Child Node with pop-in scale & alpha
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer {
                                alpha = childAlpha
                                scaleX = childScale
                                scaleY = childScale
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .size(28.dp)
                                    .background(childColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = IconMapper.getIconByName(childCat.iconName), 
                                    contentDescription = null,
                                    tint = Color.White, 
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(childCat.name, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
