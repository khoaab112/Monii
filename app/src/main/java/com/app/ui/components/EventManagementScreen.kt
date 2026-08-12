package com.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.app.data.Event
import com.app.ui.FinanceViewModel
import com.app.ui.FormatHelper
import java.util.*

@Composable
fun StripedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    stripeColor: Color = Color.White.copy(alpha = 0.3f)
) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 850, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "eventProgressAnim"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val progressWidth = width * animatedProgress

        // Draw track
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2)
        )

        // Draw progress
        if (animatedProgress > 0f) {
            drawRoundRect(
                color = color,
                size = size.copy(width = progressWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2)
            )

            // Draw stripes inside progress
            clipRect(right = progressWidth) {
                val stripeWidth = 10.dp.toPx()
                val gap = 10.dp.toPx()
                val numStripes = (progressWidth / (stripeWidth + gap)).toInt() + 2

                for (i in -1..numStripes) {
                    val startX = i * (stripeWidth + gap)
                    drawLine(
                        color = stripeColor,
                        start = Offset(startX, height),
                        end = Offset(startX + stripeWidth, 0f),
                        strokeWidth = stripeWidth,
                        cap = StrokeCap.Square
                    )
                }
            }
        }
    }
}

data class EventStatusStyle(
    val text: String,
    val dotColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val borderColor: Color? = null
)

fun getEventPriority(event: Event, totalSpent: Double, now: Long = System.currentTimeMillis()): Int {
    if (!event.isActive) return 6

    val isUpcoming = now < event.startDate
    val actualEndTime = event.endDate?.let { it + 86400000L - 1 }
    val isEnded = actualEndTime != null && now > actualEndTime
    val limit = event.limitAmount ?: 0.0
    val isEndingSoon = !isEnded && !isUpcoming && (
        (actualEndTime != null && (actualEndTime - now) <= 3 * 86400000L) ||
        (limit > 0 && (totalSpent / limit) >= 0.8)
    )
    val isStartingSoon = isUpcoming && (event.startDate - now <= 7 * 86400000L)

    return when {
        !isEnded && !isUpcoming && !isEndingSoon -> 1
        !isEnded && !isUpcoming && isEndingSoon -> 2
        isStartingSoon -> 3
        isUpcoming -> 4
        else -> 5
    }
}

fun getEventStatusStyle(event: Event, totalSpent: Double, now: Long = System.currentTimeMillis()): EventStatusStyle {
    if (!event.isActive) {
        return EventStatusStyle(
            text = "Dừng",
            dotColor = Color(0xFFFF1744),
            backgroundColor = Color.White,
            textColor = Color(0xFFFF1744),
            borderColor = Color(0xFFFF1744)
        )
    }

    val priority = getEventPriority(event, totalSpent, now)

    return when (priority) {
        1 -> EventStatusStyle(
            text = "Đang diễn ra",
            dotColor = Color(0xFF4CAF50),
            backgroundColor = Color(0xFFE8F5E9),
            textColor = Color(0xFF2E7D32)
        )
        2 -> EventStatusStyle(
            text = "Sắp kết thúc",
            dotColor = Color(0xFFFF9800),
            backgroundColor = Color(0xFFFFF3E0),
            textColor = Color(0xFFE65100)
        )
        3 -> EventStatusStyle(
            text = "Chuẩn bị bắt đầu",
            dotColor = Color(0xFF2196F3),
            backgroundColor = Color(0xFFE3F2FD),
            textColor = Color(0xFF1565C0)
        )
        4 -> EventStatusStyle(
            text = "Chưa đến hạn",
            dotColor = Color(0xFF9C27B0),
            backgroundColor = Color(0xFFF3E5F5),
            textColor = Color(0xFF7B1FA2)
        )
        else -> EventStatusStyle(
            text = "Đã kết thúc",
            dotColor = Color.White,
            backgroundColor = Color(0xFF757575),
            textColor = Color.White
        )
    }
}

@Composable
fun EventStatusChip(statusStyle: EventStatusStyle) {
    val modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(statusStyle.backgroundColor)
        .then(
            if (statusStyle.borderColor != null) {
                Modifier.border(1.dp, statusStyle.borderColor, RoundedCornerShape(50))
            } else Modifier
        )
        .padding(horizontal = 10.dp, vertical = 5.dp)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusStyle.dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusStyle.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusStyle.textColor
        )
    }
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
        label = "event_stagger_alpha_$index"
    )

    val offsetYProgress by animateDpAsState(
        targetValue = if (cardVisible || alreadySeen) 0.dp else 24.dp,
        animationSpec = if (alreadySeen) snap() else tween(
            durationMillis = 400,
            delayMillis = (index * 50).coerceAtMost(250),
            easing = LinearOutSlowInEasing
        ),
        label = "event_stagger_offset_$index"
    )

    return this.graphicsLayer {
        alpha = alphaProgress
        translationY = offsetYProgress.toPx()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventManagementScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val events by viewModel.allEvents.collectAsState()
    val transactions by viewModel.dailyTransactions.collectAsState()

    val seenKeys = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } }
    )) { mutableStateListOf<String>() }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<Event?>(null) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }
    var eventToView by remember { mutableStateOf<Event?>(null) }
    var showBottomSheetEvent by remember { mutableStateOf<Event?>(null) }

    var isReorderMode by remember { mutableStateOf(false) }
    var reorderListState by remember(events) { mutableStateOf(events.sortedBy { it.displayOrder }) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var driftY by remember { mutableFloatStateOf(0f) }
    fun onDragReleased() {
        draggedIndex = null
        driftY = 0f
    }

    LaunchedEffect(events) {
        reorderListState = events.sortedBy { it.displayOrder }
    }

    val sortedEvents = remember(events, transactions) {
        val now = System.currentTimeMillis()
        events.sortedWith(
            compareBy<Event> { event ->
                val eventTxs = transactions.filter { it.eventId == event.id }
                val totalSpent = eventTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                getEventPriority(event, totalSpent, now)
            }.thenBy { event ->
                event.displayOrder
            }.thenBy { event ->
                event.startDate
            }
        )
    }

    val context = LocalContext.current
    val gifImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    val calendarSvgPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(com.app.R.raw.calendar_rafiki)
            .build(),
        imageLoader = gifImageLoader
    )

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                tonalElevation = 1.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isReorderMode) "SẮP XẾP SỰ KIỆN" else "SỰ KIỆN",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isReorderMode) isReorderMode = false else onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (isReorderMode) {
                            TextButton(onClick = {
                                viewModel.reorderEvents(reorderListState)
                                isReorderMode = false
                            }) {
                                Text("Xong", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
                            }
                        } else if (events.isNotEmpty()) {
                            IconButton(onClick = { isReorderMode = true }) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Sắp xếp sự kiện",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isReorderMode) {
                FloatingActionButton(
                    onClick = { showAddEventDialog = true },
                    containerColor = Color(0xFF00E676),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm sự kiện", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isReorderMode) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = reorderListState,
                        key = { _, event -> event.id }
                    ) { index, event ->
                        val eventTransactions = remember(event.id, transactions) {
                            transactions.filter { it.eventId == event.id }
                        }
                        val totalSpent = remember(eventTransactions) {
                            eventTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                        }
                        val limit = event.limitAmount ?: 0.0
                        val spentPercentage = if (limit > 0) ((totalSpent / limit) * 100).coerceIn(0.0, 100.0) else 0.0
                        val eventColor = remember(event.colorHex) {
                            try { Color(android.graphics.Color.parseColor(event.colorHex)) } catch (e: Exception) { Color(0xFFFF9800) }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .staggeredEntrance(index + 1, "reorder_event_${event.id}", seenKeys)
                                .graphicsLayer {
                                    val isDragged = draggedIndex == index
                                    translationY = if (isDragged) driftY else 0f
                                    scaleX = if (isDragged) 1.03f else 1f
                                    scaleY = if (isDragged) 1.03f else 1f
                                    shadowElevation = if (isDragged) 16.dp.toPx() else 2.dp.toPx()
                                    shape = RoundedCornerShape(16.dp)
                                    clip = false
                                }
                                .zIndex(if (draggedIndex == index) 10f else 1f),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Vùng icon đổi thành vòng tròn phần trăm % tiền đã chi theo Ảnh
                                Box(
                                    modifier = Modifier.size(46.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val progressVal = (spentPercentage / 100.0).toFloat().coerceIn(0.05f, 1f)
                                    CircularProgressIndicator(
                                        progress = { progressVal },
                                        modifier = Modifier.fillMaxSize(),
                                        color = eventColor,
                                        trackColor = eventColor.copy(alpha = 0.2f),
                                        strokeWidth = 4.dp,
                                        strokeCap = StrokeCap.Round
                                    )
                                    Text(
                                        text = "${spentPercentage.toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = eventColor
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val startStr = FormatHelper.formatDate(event.startDate)
                                    val endStr = event.endDate?.let { FormatHelper.formatDate(it) } ?: "Không giới hạn"
                                    Text(
                                        text = "$startStr - $endStr",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                val mutableList = reorderListState.toMutableList()
                                                val item = mutableList.removeAt(index)
                                                mutableList.add(index - 1, item)
                                                reorderListState = mutableList
                                            }
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Lên", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (index < reorderListState.lastIndex) {
                                        IconButton(
                                            onClick = {
                                                val mutableList = reorderListState.toMutableList()
                                                val item = mutableList.removeAt(index)
                                                mutableList.add(index + 1, item)
                                                reorderListState = mutableList
                                            }
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Xuống", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 4.dp)
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
                                                        val targetIdx = draggedIndex
                                                        if (targetIdx != null) {
                                                            val itemHeightPx = 74.dp.toPx()
                                                            val minAllowedDrift = if (targetIdx > 0) -itemHeightPx * 1.2f else 0f
                                                            val maxAllowedDrift = if (targetIdx < reorderListState.lastIndex) itemHeightPx * 1.2f else 0f
                                                            driftY = (driftY + dragAmount.y).coerceIn(minAllowedDrift, maxAllowedDrift)

                                                            if (driftY > itemHeightPx * 0.5f && targetIdx < reorderListState.lastIndex) {
                                                                val mutableList = reorderListState.toMutableList()
                                                                val next = mutableList[targetIdx + 1]
                                                                mutableList[targetIdx + 1] = mutableList[targetIdx]
                                                                mutableList[targetIdx] = next
                                                                reorderListState = mutableList
                                                                draggedIndex = targetIdx + 1
                                                                driftY -= itemHeightPx
                                                            } else if (driftY < -itemHeightPx * 0.5f && targetIdx > 0) {
                                                                val mutableList = reorderListState.toMutableList()
                                                                val prev = mutableList[targetIdx - 1]
                                                                mutableList[targetIdx - 1] = mutableList[targetIdx]
                                                                mutableList[targetIdx] = prev
                                                                reorderListState = mutableList
                                                                draggedIndex = targetIdx - 1
                                                                driftY += itemHeightPx
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Kéo xếp",
                                            tint = if (draggedIndex == index) Color(0xFF5C54E5) else Color(0xFFB0BEC5),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = calendarSvgPainter,
                            contentDescription = "No events SVG",
                            modifier = Modifier.size(180.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Hãy thiết lập sự kiện",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = sortedEvents,
                        key = { _, event -> event.id },
                        contentType = { _, _ -> "event_card" }
                    ) { index, event ->
                        val eventTransactions = remember(event.id, transactions) {
                            transactions.filter { it.eventId == event.id }
                        }
                        val totalSpent = remember(eventTransactions) {
                            eventTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                        }
                        val statusStyle = remember(event, totalSpent) {
                            getEventStatusStyle(event, totalSpent)
                        }
                        val limit = event.limitAmount ?: 0.0
                        val eventColor = remember(event.colorHex) {
                            try { Color(android.graphics.Color.parseColor(event.colorHex)) } catch (e: Exception) { Color(0xFFFF9800) }
                        }
                        val now = System.currentTimeMillis()
                        val isPast = remember(event, totalSpent, now) {
                            getEventPriority(event, totalSpent, now) == 5
                        }

                        val cardKey = "event_card_${event.id}_${event.name}"
                        val alreadySeen = remember(cardKey) { seenKeys.contains(cardKey) }
                        var isCardVisible by rememberSaveable(cardKey) { mutableStateOf(alreadySeen) }

                        LaunchedEffect(cardKey) {
                            if (!alreadySeen) {
                                isCardVisible = true
                                seenKeys.add(cardKey)
                            }
                        }

                        val actualEndTime = event.endDate?.let { it + 86400000L - 1 }
                        val targetTimeProgress = if (actualEndTime == null) {
                            0.4f
                        } else if (now < event.startDate) {
                            0f
                        } else if (now >= actualEndTime) {
                            1f
                        } else {
                            val totalDuration = (actualEndTime - event.startDate).toFloat()
                            val elapsed = (now - event.startDate).toFloat()
                            if (totalDuration > 0) (elapsed / totalDuration).coerceIn(0f, 1f) else 0f
                        }

                        val targetPercent = if (limit > 0) ((totalSpent / limit) * 100).toInt().coerceIn(0, 100) else 0
                        val targetSafeProgress = if (limit > 0) (totalSpent / limit).toFloat().coerceIn(0f, 1f) else 0f

                        val animTimeProgress by animateFloatAsState(
                            targetValue = if (isCardVisible || alreadySeen) targetTimeProgress else 0f,
                            animationSpec = if (alreadySeen) snap() else tween(650, easing = LinearOutSlowInEasing),
                            label = "event_time_progress_$index"
                        )

                        val animSafeProgress by animateFloatAsState(
                            targetValue = if (isCardVisible || alreadySeen) targetSafeProgress else 0f,
                            animationSpec = if (alreadySeen) snap() else tween(650, easing = LinearOutSlowInEasing),
                            label = "event_safe_progress_$index"
                        )

                        val animPercent by animateIntAsState(
                            targetValue = if (isCardVisible || alreadySeen) targetPercent else 0,
                            animationSpec = if (alreadySeen) snap() else tween(650, easing = LinearOutSlowInEasing),
                            label = "event_percent_$index"
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .staggeredEntrance(index, cardKey, seenKeys)
                                .then(if (isPast) Modifier.alpha(0.55f) else Modifier),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                // Top Row: Status chip + 3 dots menu
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EventStatusChip(statusStyle = statusStyle)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .clickable { showBottomSheetEvent = event },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Event Name
                                Text(
                                    text = event.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = eventColor
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Group 1: Group thời gian + Line (Màu đồng nhất với bên ngoài, không dùng box màu xám)
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val startStr = FormatHelper.formatDate(event.startDate)
                                        val endStr = event.endDate?.let { FormatHelper.formatDate(it) } ?: "Vô thời hạn"
                                        Text(
                                            text = "$startStr - $endStr",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Pill hiển thị thời gian (Dùng eventColor)
                                        val remainingText = if (event.endDate == null) {
                                            "Vô thời hạn"
                                        } else if (now < event.startDate) {
                                            val diffMillis = (event.endDate + 86400000L - 1) - now
                                            val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                                            if (diffDays > 0) "Còn $diffDays ngày" else "Chưa diễn ra"
                                        } else {
                                            val actualEndTimeVal = event.endDate + 86400000L - 1
                                            val diffMillis = actualEndTimeVal - now
                                            val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)
                                            if (diffMillis < 0) {
                                                "Đã hết hạn"
                                            } else if (diffDays == 0L) {
                                                "Hôm nay hết hạn"
                                            } else {
                                                "Còn $diffDays ngày"
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = eventColor.copy(alpha = 0.12f),
                                            contentColor = eventColor
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DateRange,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = eventColor
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = remainingText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = eventColor
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Thanh line ngang: Thời gian hết hạn (Animated)
                                    StripedProgressIndicator(
                                        progress = animTimeProgress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = eventColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Group 2: Group Đã chi, Hạn mức, % Vòng tròn (Cân bằng tỉ lệ 50/50, chữ in đậm, màu theo eventColor)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Đã chi",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = FormatHelper.formatVND(totalSpent),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = eventColor
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(24.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Hạn mức",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (limit > 0) FormatHelper.formatVND(limit) else "Không có",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Box(
                                            modifier = Modifier.size(44.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { animSafeProgress },
                                                modifier = Modifier.fillMaxSize(),
                                                color = eventColor,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                strokeWidth = 4.dp
                                            )
                                            Text(
                                                text = "$animPercent%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = eventColor
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

    // Modal Bottom Sheet cho menu 3 chấm (Tuân thủ 100% quy tắc hệ thống)
    if (showBottomSheetEvent != null) {
        val event = showBottomSheetEvent!!
        val sheetTitleColor = try { Color(android.graphics.Color.parseColor(event.colorHex)) } catch (e: Exception) { Color(0xFFFF9800) }

        AppModalBottomSheet(
            onDismissRequest = { showBottomSheetEvent = null },
            title = event.name,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 0. Bật/Tắt kích hoạt sự kiện (Chỉnh sửa text thành Tắt sự kiện)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = event
                            showBottomSheetEvent = null
                            viewModel.updateEvent(target.copy(isActive = !target.isActive))
                            viewModel.showSuccessNotification(
                                if (target.isActive) "Đã tắt sự kiện '${target.name}'"
                                else "Đã kích hoạt sự kiện '${target.name}'"
                            )
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (event.isActive) Icons.Default.PauseCircle else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (event.isActive) Color(0xFFE65100) else Color(0xFF2E7D32),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (event.isActive) "Tắt sự kiện" else "Kích hoạt sự kiện",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (event.isActive) Color(0xFFE65100) else Color(0xFF2E7D32)
                    )
                }

                // 1. Xem chi tiết & danh sách giao dịch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = event
                            showBottomSheetEvent = null
                            eventToView = target
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = sheetTitleColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Xem chi tiết & danh sách giao dịch",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 2. Chỉnh sửa thông tin sự kiện
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = event
                            showBottomSheetEvent = null
                            eventToEdit = target
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Chỉnh sửa thông tin sự kiện",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 3. Xóa sự kiện
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val target = event
                            showBottomSheetEvent = null
                            eventToDelete = target
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Xóa sự kiện",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // Modal Chi tiết sự kiện (Tuân thủ Rule 3 với AppModalBottomSheet)
    if (eventToView != null) {
        val event = eventToView!!
        val eventTransactions = transactions.filter { it.eventId == event.id }
        val limit = event.limitAmount ?: 0.0
        val spent = eventTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val eventColor = try { Color(android.graphics.Color.parseColor(event.colorHex)) } catch (e: Exception) { Color(0xFFFF9800) }

        AppModalBottomSheet(
            onDismissRequest = { eventToView = null },
            title = event.name,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            footer = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { eventToView = null },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Đóng", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val target = event
                            eventToView = null
                            eventToEdit = target
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chỉnh sửa", fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Thời gian
                val startStr = FormatHelper.formatDate(event.startDate)
                val endStr = event.endDate?.let { FormatHelper.formatDate(it) } ?: "Không giới hạn"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = eventColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Thời gian: $startStr - $endStr",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Tiến độ chi tiêu
                if (limit > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tiến độ chi tiêu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "${FormatHelper.formatVND(spent)} / ${FormatHelper.formatVND(limit)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = eventColor
                            )
                        }
                        val progress = (spent / limit).toFloat().coerceIn(0f, 1f)
                        StripedProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = if (progress >= 0.9f) MaterialTheme.colorScheme.error else eventColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tổng đã chi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(FormatHelper.formatVND(spent), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = eventColor)
                    }
                }

                // Lịch sử giao dịch liên quan
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Lịch sử giao dịch (${eventTransactions.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (eventTransactions.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            eventTransactions.sortedByDescending { it.timestamp }.forEach { tx ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tx.categoryName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(FormatHelper.formatDate(tx.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(
                                        text = "${if (tx.type == "EXPENSE") "-" else "+"}${FormatHelper.formatVND(tx.amount)}",
                                        color = if (tx.type == "EXPENSE") MaterialTheme.colorScheme.error else Color(0xFF00E676),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    } else {
                        Text(
                            text = "Chưa có giao dịch nào liên kết với sự kiện này.",
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    if (showAddEventDialog || eventToEdit != null) {
        val editingEvent = eventToEdit
        var name by remember { mutableStateOf(editingEvent?.name ?: "") }
        var description by remember { mutableStateOf(editingEvent?.description ?: "") }
        var startDate by remember { mutableStateOf(editingEvent?.startDate ?: System.currentTimeMillis()) }
        var endDate by remember { mutableStateOf(editingEvent?.endDate) }
        var limitAmountStr by remember { mutableStateOf(editingEvent?.limitAmount?.let { String.format(java.util.Locale.US, "%,d", it.toLong()).replace(',', '.') } ?: "") }
        var selectedColor by remember { mutableStateOf(editingEvent?.colorHex ?: "#FF9800") }

        val colors = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4",
            "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107",
            "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#3949AB", "#D81B60"
        )

        var showStartDatePicker by remember { mutableStateOf(false) }
        var showEndDatePicker by remember { mutableStateOf(false) }

        val startDateState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        val endDateState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: System.currentTimeMillis())

        if (showStartDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showStartDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        startDateState.selectedDateMillis?.let { startDate = it }
                        showStartDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showStartDatePicker = false }) { Text("Hủy") }
                }
            ) {
                DatePicker(state = startDateState)
            }
        }

        if (showEndDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showEndDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        endDateState.selectedDateMillis?.let { endDate = it }
                        showEndDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndDatePicker = false }) { Text("Hủy") }
                }
            ) {
                DatePicker(state = endDateState)
            }
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var nameError by remember { mutableStateOf<String?>(null) }

        AppModalBottomSheet(
            onDismissRequest = {
                showAddEventDialog = false
                eventToEdit = null
            },
            title = if (editingEvent != null) "Sửa sự kiện" else "Thêm sự kiện mới",
            sheetState = sheetState,
            footer = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showAddEventDialog = false
                            eventToEdit = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Hủy",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = "Vui lòng nhập tên sự kiện!"
                                return@Button
                            }
                            if (endDate != null && endDate!! < startDate) {
                                viewModel.showWarningNotification("Ngày kết thúc phải sau ngày bắt đầu!")
                                return@Button
                            }
                            val limit = limitAmountStr.replace(".", "").toDoubleOrNull()

                            if (editingEvent != null) {
                                viewModel.updateEvent(editingEvent.copy(
                                    name = name,
                                    description = description,
                                    startDate = startDate,
                                    endDate = endDate,
                                    limitAmount = limit,
                                    colorHex = selectedColor
                                ))
                                viewModel.showSuccessNotification("Cập nhật sự kiện thành công!")
                            } else {
                                viewModel.addEvent(
                                    name = name,
                                    description = description,
                                    startDate = startDate,
                                    endDate = endDate,
                                    limitAmount = limit,
                                    colorHex = selectedColor
                                )
                                viewModel.showSuccessNotification("Thêm sự kiện mới thành công!")
                            }
                            showAddEventDialog = false
                            eventToEdit = null
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                            .testTag("confirm_create_event_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5C54E5),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (editingEvent != null) "Lưu thay đổi" else "Thêm sự kiện",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Tên sự kiện (*) Input with inline validation
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) nameError = null
                        },
                        placeholder = { Text("Tên sự kiện (*)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (nameError != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = if (nameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        isError = nameError != null,
                        supportingText = if (nameError != null) {
                            { Text(text = nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5C54E5),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            errorBorderColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("event_name_input")
                    )

                    // 2. Mô tả Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Mô tả", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5C54E5),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("event_desc_input")
                    )

                    // 3. Hạn mức chi tiêu Input
                    OutlinedTextField(
                        value = limitAmountStr,
                        onValueChange = { str ->
                            val raw = str.filter { c -> c.isDigit() }
                            if (raw.isNotEmpty()) {
                                try {
                                    val formatted = String.format(java.util.Locale.US, "%,d", raw.toLong()).replace(',', '.')
                                    limitAmountStr = formatted
                                } catch (e: Exception) { }
                            } else {
                                limitAmountStr = ""
                            }
                        },
                        placeholder = { Text("Hạn mức chi tiêu (VNĐ)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Máy tính",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF5C54E5),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("event_limit_input")
                    )

                    // 4. MÀU SẮC Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MÀU SẮC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            colors.forEach { hex ->
                                val color = FormatHelper.parseColor(hex)
                                val isSelected = selectedColor == hex
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.5.dp, Color(0xFF5C54E5), CircleShape)
                                            } else Modifier
                                        )
                                        .clickable { selectedColor = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Chọn màu",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. THỜI GIAN ÁP DỤNG Section (Start & End Date)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "THỜI GIAN ÁP DỤNG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Start date item (Floating label design)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showStartDatePicker = true }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.2.dp, Color(0xFF9E95F5))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = FormatHelper.formatDate(startDate),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = Color(0xFF9E95F5),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Floating Badge Label over top border
                                Row(
                                    modifier = Modifier
                                        .offset(x = 12.dp, y = 0.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Bắt đầu",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34C759)
                                    )
                                }
                            }

                            // 2. End date item (Floating label design)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showEndDatePicker = true }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.2.dp, Color(0xFF9E95F5))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (endDate != null) FormatHelper.formatDate(endDate!!) else "Chọn...",
                                            fontSize = 14.sp,
                                            fontWeight = if (endDate != null) FontWeight.Bold else FontWeight.Normal,
                                            color = if (endDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )

                                        if (endDate != null) {
                                            IconButton(
                                                onClick = { endDate = null },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Xóa",
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = Color(0xFF9E95F5),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Floating Badge Label over top border
                                Row(
                                    modifier = Modifier
                                        .offset(x = 12.dp, y = 0.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Color(0xFFFF3B30),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Kết thúc",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF3B30)
                                    )
                                }
                            }
                        }
                    }

            }
        }
    }

    if (eventToDelete != null) {
        val event = eventToDelete!!
        AppNotificationDialog(
            showDialog = true,
            title = "Xác nhận xóa sự kiện?",
            content = "Bạn có chắc chắn muốn xóa sự kiện '${event.name}'? Các giao dịch trong sự kiện sẽ KHÔNG bị xóa mà chỉ được gỡ liên kết khỏi sự kiện này.",
            cancelButton = DialogButtonConfig(
                text = "HỦY",
                action = { eventToDelete = null }
            ),
            confirmButton = DialogButtonConfig(
                text = "XÓA SỰ KIỆN",
                action = {
                    viewModel.deleteEvent(event)
                    viewModel.showSuccessNotification("Đã xóa sự kiện thành công!")
                    eventToDelete = null
                },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            ),
            onDismissRequest = { eventToDelete = null }
        )
    }
}
