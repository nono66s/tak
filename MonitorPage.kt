package com.nono.sensor_log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.graphics.toColorInt

@Composable
fun MonitorPage(
    viewModel: MainViewModel
) {
    val sensorMap = viewModel.latestSensorValues
    val time = viewModel.lastUpdateTime.value
    val context = LocalContext.current
    
    var selectedSensor by remember { mutableStateOf<SensorInfo?>(value = null) }
    var tapCount by remember { mutableIntStateOf(0) }
    var isExpertMode by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { s -> SensorDataManager.importSettings(s.bufferedReader().readText()) } }
    }

    if (selectedSensor != null) {
        SensorDetailSubPage(
            info = selectedSensor!!, 
            viewModel = viewModel, 
            onBack = { selectedSensor = null }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable(
                        interactionSource = null,
                        indication = null
                    ) {
                        tapCount++
                        if (tapCount >= 5) {
                            isExpertMode = !isExpertMode
                            tapCount = 0
                            android.widget.Toast.makeText(context, if (isExpertMode) "已開啟進階設定" else "已隱藏進階設定", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(text = "感測器即時監控", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(text = "最後更新: $time", style = MaterialTheme.typography.labelSmall, color = ComposeColor.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text("核心監控", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                
                val sortedCoreIds = SensorDataManager.coreSensorOrder.sortedBy { id ->
                    val matchingKey = SensorDataManager.sensorDefinitions.keys
                        .asSequence()
                        .filter { id.contains(it, ignoreCase = true) }
                        .maxByOrNull { it.length } ?: ""
                    SensorDataManager.sensorDefinitions.keys.toList().indexOf(matchingKey)
                }

                items(sortedCoreIds, key = { "core_$it" }) { id ->
                    val matchingKey = SensorDataManager.sensorDefinitions.keys
                        .asSequence()
                        .filter { id.contains(it, ignoreCase = true) }
                        .maxByOrNull { it.length }

                    val def = (if (matchingKey != null) {
                        SensorDataManager.sensorDefinitions[matchingKey]?.copy(id = id)
                    } else {
                        SensorInfo(id, id, "", "📊", "自動偵測數據")
                    }) ?: SensorInfo(id, id, "", "📊", "自動偵測數據")

                    SensorCard(
                        info = def,
                        value = sensorMap[id] ?: "暫無數據",
                        isUploadEnabled = SensorDataManager.sensorUploadToggles[id] ?: true,
                        showUploadSwitch = isExpertMode,
                        onToggleUpload = { SensorDataManager.setSensorUpload(id, it) },
                        onClick = { selectedSensor = def }
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("其他偵測感測器", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (isExpertMode) {
                            Row {
                                IconButton(onClick = { SensorDataManager.exportSettings(context) }) { Text("📤", fontSize = 16.sp) }
                                IconButton(onClick = { importLauncher.launch("application/json") }) { Text("📥", fontSize = 16.sp) }
                            }
                        }
                    }
                }

                items(SensorDataManager.dynamicSensorOrder, key = { "dyn_$it" }) { id ->
                    val matchingKey = SensorDataManager.sensorDefinitions.keys
                        .asSequence()
                        .filter { id.contains(it, ignoreCase = true) }
                        .maxByOrNull { it.length }

                    val def = (if (matchingKey != null) {
                        SensorDataManager.sensorDefinitions[matchingKey]?.copy(id = id)
                    } else {
                        SensorInfo(id, id.split(".").last(), "", "📊", "自動偵測數據")
                    }) ?: SensorInfo(id, id.split(".").last(), "", "📊", "自動偵測數據")

                    MiniSensorCard(
                        name = id,
                        value = sensorMap[id] ?: "0",
                        isUploadEnabled = SensorDataManager.sensorUploadToggles[id] ?: true,
                        showUploadSwitch = isExpertMode,
                        onToggleUpload = { SensorDataManager.setSensorUpload(id, it) },
                        onClick = { selectedSensor = def }
                    )
                }
                
                if (isExpertMode) {
                    item { Text("忽略/次要區塊", style = MaterialTheme.typography.labelLarge, color = ComposeColor.Gray, modifier = Modifier.padding(top = 12.dp)) }

                    items(SensorDataManager.ignoredSensorOrder, key = { "ignored_$it" }) { id ->
                        val matchingKey = SensorDataManager.sensorDefinitions.keys
                            .asSequence()
                            .filter { id.contains(it, ignoreCase = true) }
                            .maxByOrNull { it.length }

                        val def = (if (matchingKey != null) {
                            SensorDataManager.sensorDefinitions[matchingKey]?.copy(id = id)
                        } else {
                            SensorInfo(id, id, "", "📊", "忽略項目")
                        }) ?: SensorInfo(id, id, "", "📊", "忽略項目")

                        SensorCard(
                            info = def,
                            value = sensorMap[id] ?: "暫無數據",
                            isUploadEnabled = SensorDataManager.sensorUploadToggles[id] ?: true,
                            showUploadSwitch = true, 
                            onToggleUpload = { SensorDataManager.setSensorUpload(id, it) },
                            onClick = { selectedSensor = def }
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailSubPage(info: SensorInfo, viewModel: MainViewModel, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val actualKey = info.id
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val calendar = Calendar.getInstance()
    val currentHour = remember { calendar[Calendar.HOUR_OF_DAY] }
    
    var viewingDate by remember { mutableStateOf(todayStr) }
    var viewingHour by remember { mutableStateOf<Int?>(currentHour) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var scrubPoint by remember { mutableStateOf<Pair<Long, List<Float>>?>(null) }
    var show28DayGrid by remember { mutableStateOf(false) }

    var isMapExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!isMapExpanded) {
                    TopAppBar(
                        title = { Text(info.name, fontWeight = FontWeight.Bold) },
                        navigationIcon = { IconButton(onClick = onBack) { Text("⬅️") } },
                        actions = {
                            TextButton(onClick = { viewModel.mergeAllData() }) {
                                Text("歸檔本日", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(if (isMapExpanded) PaddingValues(0.dp) else innerPadding).fillMaxSize()) {
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("分析與紀錄", modifier = Modifier.padding(12.dp)) }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("感測資訊", modifier = Modifier.padding(12.dp)) }
                }
                when (selectedTab) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 1. 24H 活躍時間軸 (固定在頂部)
                            HourActivityBar(viewingDate, viewingHour, viewModel.dataAvailability) {
                                viewingHour = if (viewingHour == it) null else it 
                            }

                            // 2. 28日活躍圖切換
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                TextButton(onClick = { show28DayGrid = !show28DayGrid }) {
                                    Text(if (show28DayGrid) "🔼 隱藏 28日活躍圖" else "📅 顯示 28日活躍圖", fontSize = 12.sp)
                                }
                                if (show28DayGrid) {
                                    DateContributionGrid(viewingDate, viewModel.dataAvailability) { viewingDate = it }
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                if (!isMapExpanded) {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                                        // 3. 圖表 (與時間軸連動)
                                        item {
                                            Card(
                                                modifier = Modifier.fillMaxWidth().height(320.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                val historyPoints = remember(viewingDate, viewingHour, viewModel.sensorHistory[actualKey], viewModel.logGroups.size) {
                                                    val filteredLog = viewModel.logGroups.filter { 
                                                        it.type == actualKey && 
                                                        it.timestamp.startsWith(viewingDate) &&
                                                        (viewingHour == null || it.timestamp.split(" ")[1].startsWith(viewingHour!!.toString().padStart(2, '0')))
                                                    }.map { 
                                                        val ts = try { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(it.timestamp)?.time ?: 0L } catch (_: Exception) { 0L }
                                                        SensorDataManager.HistoricalEntry(ts, it.content.split("|").last())
                                                    }
                                                    if (viewingDate == todayStr && (viewingHour == null || viewingHour == currentHour)) {
                                                        val live = viewModel.sensorHistory[actualKey] ?: emptyList()
                                                        (filteredLog + live).distinctBy { it.timestamp }.sortedBy { it.timestamp }
                                                    } else filteredLog.sortedBy { it.timestamp }
                                                }
                                                
                                                Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                                    if (historyPoints.isNotEmpty() || viewingHour != null) {
                                                        if (actualKey.contains("Location", true)) {
                                                            LocationTrajectoryMap(
                                                                history = historyPoints,
                                                                isExpanded = false,
                                                                onToggleExpand = { isMapExpanded = true }
                                                            )
                                                        } else {
                                                            InteractiveSensorChart(
                                                                sensorId = actualKey,
                                                                history = historyPoints,
                                                                viewingHour = viewingHour,
                                                                scale = scale,
                                                                offset = offset,
                                                                onTransform = { s, o -> scale = s; offset = o },
                                                                onScrub = { scrubPoint = it }
                                                            )
                                                        }
                                                        if (historyPoints.isEmpty() && viewingHour != null) {
                                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                Text(text = "🚫 $viewingHour:00 無數據紀錄", color = ComposeColor.Gray, style = MaterialTheme.typography.bodySmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 4. 當前讀數 (小卡片，在圖表下)
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(info.icon, fontSize = 18.sp)
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        if (scrubPoint != null) {
                                                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scrubPoint!!.first))
                                                            Text("查閱點: $timeStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                            Text(text = "${scrubPoint!!.second.joinToString(", ") { String.format(Locale.US, "%.1f", it) }} ${info.unit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        } else {
                                                            Text("即時最新讀數", style = MaterialTheme.typography.labelSmall, color = ComposeColor.Gray)
                                                            Text(text = "${viewModel.latestSensorValues[actualKey] ?: "---"} ${info.unit}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 5. 歸檔清單
                                        item {
                                            Spacer(modifier = Modifier.height(24.dp))
                                            HorizontalDivider(thickness = 0.5.dp, color = ComposeColor.Gray.copy(alpha = 0.2f))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("歸檔紀錄與片段", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        val filteredMerged = viewModel.mergedReports.filter { it.sensorType == info.id && it.date == viewingDate && (viewingHour == null || it.startTime.startsWith(viewingHour.toString().padStart(2, '0'))) }.sortedByDescending { it.level }
                                        if (filteredMerged.isEmpty()) {
                                            item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("此小時尚未封裝", color = ComposeColor.Gray, fontSize = 12.sp) } }
                                        } else {
                                            items(filteredMerged) { report ->
                                                var previewText by remember { mutableStateOf<String?>(null) }
                                                MergedReportItem(report = report, onPreview = { previewText = SensorUtils.createMergedReportJson(report).toString(4) }, onDownload = {
                                                    val zipFile = File(SensorDataManager.getExportZipFolder(), "audit_${report.sensorType.replace(" ", "_")}_${report.date}.zip")
                                                    if (zipFile.exists()) SensorUtils.shareFile(context, zipFile, "application/zip")
                                                })
                                                if (previewText != null) ArchivePreviewDialog(previewText!!) { previewText = null }
                                            }
                                        }
                                        item { Spacer(modifier = Modifier.height(80.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                            Text("感測器科學規格", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(24.dp))
                            InfoSpecCard("學術功能定義", info.description, "📖")
                            Spacer(modifier = Modifier.height(16.dp))
                            InfoSpecCard("硬體底層識別碼", actualKey, "🆔", isCode = true)
                            Spacer(modifier = Modifier.height(16.dp))
                            InfoSpecCard("標準物理單位", info.unit.ifBlank { "無單位 (Raw)" }, "📐")
                        }
                    }
                }
            }
        }

        // 全螢幕地圖覆蓋層 (以 Dialog 實作以徹底蓋住底層導覽列)
        if (isMapExpanded && actualKey.contains("Location", true)) {
            val historyPoints = remember(viewingDate, viewingHour, viewModel.sensorHistory[actualKey], viewModel.logGroups.size) {
                val filteredLog = viewModel.logGroups.filter { 
                    it.type == actualKey && 
                    it.timestamp.startsWith(viewingDate) &&
                    (viewingHour == null || it.timestamp.split(" ")[1].startsWith(viewingHour!!.toString().padStart(2, '0')))
                }.map { 
                    val ts = try { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(it.timestamp)?.time ?: 0L } catch (e: Exception) { 0L }
                    SensorDataManager.HistoricalEntry(ts, it.content.split("|").last())
                }
                if (viewingDate == todayStr && (viewingHour == null || viewingHour == currentHour)) {
                    val live = viewModel.sensorHistory[actualKey] ?: emptyList()
                    (filteredLog + live).distinctBy { it.timestamp }.sortedBy { it.timestamp }
                } else filteredLog.sortedBy { it.timestamp }
            }

            Dialog(
                onDismissRequest = { isMapExpanded = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(modifier = Modifier.fillMaxSize().background(ComposeColor.Black)) {
                    LocationTrajectoryMap(
                        history = historyPoints,
                        isExpanded = true,
                        onToggleExpand = { isMapExpanded = false }
                    )
                }
            }
        }
    }
}

/**
 * 專為 Location Sensor 設計的軌跡地圖 (OpenStreetMap 整合)
 */
@Composable
fun LocationTrajectoryMap(
    history: List<SensorDataManager.HistoricalEntry>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    // 預設台灣中心點 (避免世界地圖閃爍)
    val taiwanCenter = remember { GeoPoint(23.6, 121.0) }
    
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("osm_prefs", android.content.Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        // 使用更具體的 UserAgent 以加速 Tile 請求許可
        Configuration.getInstance().userAgentValue = "SensorLogTaiwan/1.0 (${context.packageName})"
    }

    val geoPoints = remember(history) {
        val raw = history.mapNotNull { entry ->
            val parts = entry.value.split(",").mapNotNull { it.trim().toDoubleOrNull() }
            if (parts.size >= 2) GeoPoint(parts[0], parts[1]) else null
        }
        if (raw.isEmpty()) return@remember emptyList()
        val smoothed = mutableListOf<GeoPoint>()
        smoothed.add(raw[0])
        val threshold = 0.00005 // 約 5 公尺
        for (i in 1 until raw.size) {
            val last = smoothed.last()
            val curr = raw[i]
            val dist = sqrt((curr.latitude - last.latitude).pow(2.0) + (curr.longitude - last.longitude).pow(2.0))
            if (dist > threshold) smoothed.add(curr)
        }
        smoothed
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    // 初始位置設為台灣
                    controller.setZoom(7.5)
                    controller.setCenter(taiwanCenter)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                
                // 1. 添加比例尺 (Scale Bar)
                val scaleBarOverlay = ScaleBarOverlay(mapView).apply {
                    setAlignBottom(true)
                    setAlignRight(false)
                    val dm = context.resources.displayMetrics
                    // 根據全螢幕模式調整 Y 偏移量 (向上移 5%)
                    val yOffset = if (isExpanded) 20 + (dm.heightPixels * 0.05).toInt() else 20
                    setScaleBarOffset(dm.widthPixels / 20, yOffset)
                    setTextSize(10f * dm.density)
                }
                mapView.overlays.add(scaleBarOverlay)

                if (geoPoints.isNotEmpty()) {
                    val polyline = Polyline(mapView).apply {
                        outlinePaint.color = "#00E676".toColorInt()
                        outlinePaint.strokeWidth = 10f
                        setPoints(geoPoints)
                    }
                    mapView.overlays.add(polyline)

                    val startMarker = Marker(mapView).apply {
                        position = geoPoints.first()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "起點"
                    }
                    mapView.overlays.add(startMarker)

                    val endMarker = Marker(mapView).apply {
                        position = geoPoints.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "當前位置"
                    }
                    mapView.overlays.add(endMarker)

                    // 僅在非全螢幕模式下自動追蹤/縮放
                    if (!isExpanded) {
                        val box = BoundingBox.fromGeoPoints(geoPoints)
                        mapView.post {
                            // 增加邊距讓軌跡更清晰，限制縮放在 12.5 (確保看到多個街廓)
                            mapView.zoomToBoundingBox(box, true, 200)
                            if (mapView.zoomLevelDouble > 12.5) {
                                mapView.controller.setZoom(12.5)
                            }
                        }
                    } else {
                        // 全螢幕模式初次進入，設定到「七個街廓」的視野 (Zoom 14.5)
                        if (mapView.zoomLevelDouble < 10.0) { 
                            mapView.controller.setZoom(14.5)
                            mapView.controller.animateTo(geoPoints.last())
                        }
                    }
                } else if (!isExpanded) {
                    // 無數據時回歸台灣中心
                    mapView.controller.animateTo(taiwanCenter)
                }
                mapView.invalidate()
            }
        )

        // 放大鏡按鈕
        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 12.dp, 
                    bottom = if (isExpanded) 12.dp + (LocalContext.current.resources.displayMetrics.heightPixels * 0.05f / LocalContext.current.resources.displayMetrics.density).dp else 12.dp
                )
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
        ) {
            Text("🔍", color = ComposeColor.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun InteractiveSensorChart(
    sensorId: String,
    history: List<SensorDataManager.HistoricalEntry>,
    scale: Float,
    offset: Float,
    viewingHour: Int? = null,
    onTransform: (Float, Float) -> Unit,
    onScrub: (Pair<Long, List<Float>>?) -> Unit
) {
    val isHeartRate = sensorId.contains("Heart", true) || sensorId.contains("HR", true)
    val multiPoints = history.map { entry -> entry.timestamp to entry.value.split(",").mapNotNull { it.trim().toFloatOrNull() } }
    
    // 基準時間範圍：鎖定在選定小時的 00:00~59:59
    val (startTime, endTime) = if (viewingHour != null) {
        val cal = Calendar.getInstance()
        val startTs = cal.apply { set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); set(Calendar.HOUR_OF_DAY, viewingHour) }.timeInMillis
        val endTs = startTs + 3600000L
        startTs to endTs
    } else {
        if (multiPoints.isEmpty()) (System.currentTimeMillis() - 3600000L) to System.currentTimeMillis()
        else (multiPoints.first().first to multiPoints.last().first)
    }

    val timeWindow = (endTime - startTime).coerceAtLeast(1L).toFloat()
    val allValues = multiPoints.flatMap { it.second }
    val minVal = if (isHeartRate) 0f else (allValues.minOrNull() ?: 0f)
    val maxVal = if (isHeartRate) 250f else (allValues.maxOrNull() ?: 1f)
    val valueRange = if (maxVal == minVal) 1f else (maxVal - minVal) * 1.2f
    val midVal = (maxVal + minVal) / 2f

    Canvas(
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 100f)
                    val width = size.width; val leftPadding = 55.dp.toPx()
                    val cWidth = width - leftPadding - 15.dp.toPx()
                    val touchX = centroid.x - leftPadding
                    val relativeDataX = (touchX / scale - offset) / cWidth
                    val newOffset = (touchX / newScale) - (relativeDataX * cWidth) + (pan.x / newScale)
                    onTransform(newScale, newOffset.coerceIn(-(cWidth - cWidth / newScale), 0f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { }, onDragEnd = { onScrub(null) }, onDragCancel = { onScrub(null) }, onDrag = { change, _ ->
                    val width = size.width; val leftPadding = 55.dp.toPx(); val cWidth = width - leftPadding - 15.dp.toPx()
                    val touchX = change.position.x - leftPadding
                    val relativeX = (touchX / scale) - offset
                    val targetTs = startTime + (relativeX / cWidth * timeWindow).toLong()
                    val closest = multiPoints.minByOrNull { abs(it.first - targetTs) }
                    onScrub(closest)
                }) }
    ) {
        val width = size.width; val height = size.height
        val leftPadding = 55.dp.toPx(); val bottomPadding = 35.dp.toPx()
        val topPadding = 15.dp.toPx(); val rightPadding = 15.dp.toPx()
        val cWidth = width - leftPadding - rightPadding; val cHeight = height - bottomPadding - topPadding

        val textPaint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 10.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT }
        listOf(minVal, midVal, maxVal).forEach { valV ->
            val y = topPadding + cHeight - ((valV - minVal) / valueRange * cHeight)
            drawLine(ComposeColor.Gray.copy(0.2f), Offset(leftPadding, y), Offset(width - rightPadding, y), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", valV), leftPadding - 8.dp.toPx(), y + 4.dp.toPx(), textPaint)
        }

        clipRect(left = leftPadding, top = 0f, right = width - rightPadding, bottom = height - bottomPadding + 5.dp.toPx()) {
            val timePaint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 9.sp.toPx() ; textAlign = android.graphics.Paint.Align.CENTER }
            val stepMin = if (scale >= 10f) 1 else if (scale >= 5f) 2 else if (scale >= 2f) 5 else 10
            for (m in 0..60 step stepMin) {
                val ts = startTime + (m * 60 * 1000L)
                val x = ((ts - startTime).toFloat() / timeWindow * cWidth * scale) + (offset * scale) + leftPadding
                if (x in leftPadding..width) {
                    val label = SimpleDateFormat("mm:ss", Locale.getDefault()).format(Date(ts))
                    drawContext.canvas.nativeCanvas.drawText(label, x, height - 12.dp.toPx(), timePaint)
                    drawLine(ComposeColor.Gray.copy(0.15f), Offset(x, topPadding), Offset(x, topPadding + cHeight))
                }
            }

            val colors = listOf(ComposeColor.Red, ComposeColor.Green, ComposeColor.Blue, ComposeColor.Yellow)
            val numAxes = multiPoints.firstOrNull()?.second?.size ?: 0
            for (axis in 0 until numAxes) {
                val path = Path(); var first = true
                multiPoints.forEach { (ts, vals) ->
                    if (axis < vals.size) {
                        val x = ((ts - startTime).toFloat() / timeWindow * cWidth * scale) + (offset * scale) + leftPadding
                        val y = topPadding + cHeight - ((vals[axis] - minVal) / valueRange * cHeight)
                        if (x in (leftPadding - 100f)..(width + 100f)) {
                            if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                        }
                    }
                }
                drawPath(path, colors[axis % colors.size], style = Stroke(1.8.dp.toPx()))
            }

            // 灰斷點
            for (i in 0 until multiPoints.size - 1) {
                if (multiPoints[i+1].first - multiPoints[i].first > 5000) {
                    val x1 = ((multiPoints[i].first - startTime).toFloat() / timeWindow * cWidth * scale) + (offset * scale) + leftPadding
                    val x2 = ((multiPoints[i+1].first - startTime).toFloat() / timeWindow * cWidth * scale) + (offset * scale) + leftPadding
                    drawRect(ComposeColor.Gray.copy(0.15f), Offset(x1, topPadding), Size(x2 - x1, cHeight))
                }
            }
        }

        drawLine(ComposeColor.Gray, Offset(leftPadding, topPadding), Offset(leftPadding, topPadding + cHeight), 2f)
        drawLine(ComposeColor.Gray, Offset(leftPadding, topPadding + cHeight), Offset(width - rightPadding, topPadding + cHeight), 2f)
    }
}

@Composable
fun SensorCard(info: SensorInfo, value: String, isUploadEnabled: Boolean, showUploadSwitch: Boolean, onToggleUpload: (Boolean) -> Unit, onClick: () -> Unit) {
    val isWaiting = value == "暫無數據" || value == "0"
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = if (isWaiting) MaterialTheme.colorScheme.surfaceVariant.copy(0.3f) else MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(if (isWaiting) 0.dp else 4.dp), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = if (isWaiting) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(0.8f)) { Box(contentAlignment = Alignment.Center) { Text(info.icon, fontSize = 24.sp) } }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary.copy(0.7f))
                Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (isWaiting) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                if (!isWaiting) Text(info.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (showUploadSwitch) {
                VerticalDivider(modifier = Modifier.padding(horizontal = 8.dp).fillMaxHeight(), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("上傳", style = MaterialTheme.typography.labelSmall); Switch(checked = isUploadEnabled, onCheckedChange = onToggleUpload, modifier = Modifier.scale(0.7f)) }
            } else { Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.outlineVariant) }
        }
    }
}

@Composable
fun MiniSensorCard(name: String, value: String, isUploadEnabled: Boolean, showUploadSwitch: Boolean, onToggleUpload: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name.split(".").last(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            if (showUploadSwitch) { Switch(checked = isUploadEnabled, onCheckedChange = onToggleUpload, modifier = Modifier.scale(0.6f)) }
        }
    }
}

@Composable
fun HourActivityBar(date: String, selectedHour: Int?, availability: Map<String, Set<Int>>, onHourSelected: (Int) -> Unit) {
    val hours = availability[date] ?: emptySet()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("24H 活躍時間軸 ($date)", style = MaterialTheme.typography.labelSmall, color = ComposeColor.Gray)
        Row(modifier = Modifier.fillMaxWidth().height(30.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (0..23).forEach { h -> Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (h == selectedHour) MaterialTheme.colorScheme.primary else if (hours.contains(h)) MaterialTheme.colorScheme.primary.copy(0.3f) else ComposeColor.Gray.copy(0.1f)).clickable { onHourSelected(h) }) }
        }
    }
}

@Composable
fun DateContributionGrid(selectedDate: String, availability: Map<String, Set<Int>>, onDateSelected: (String) -> Unit) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val days = (0..27).map { val calendar = Calendar.getInstance(); calendar.add(Calendar.DAY_OF_YEAR, -it); sdf.format(calendar.time) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("28天活躍圖", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(120.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            gridItems(days) { date ->
                val hourCount = (availability[date] ?: emptySet()).size
                Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(if (date == selectedDate) MaterialTheme.colorScheme.primary else if (hourCount > 0) ComposeColor(0xFF30A14E) else ComposeColor.Gray.copy(0.15f)).clickable { onDateSelected(date) })
            }
        }
    }
}

@Composable
fun ArchivePreviewDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }, text = { Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).background(ComposeColor(0xFF1E1E1E), RoundedCornerShape(8.dp)).verticalScroll(rememberScrollState()).padding(8.dp)) { Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ComposeColor(0xFFB5CEA8)) } })
}

@Composable
fun InfoSpecCard(title: String, content: String, icon: String, isCode: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = content, style = if (isCode) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default)
            }
        }
    }
}

@Composable
fun MergedReportItem(report: SensorDataManager.MergedReport, onPreview: () -> Unit, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${report.startTime} - ${report.endTime}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("樣本數: ${report.count}", fontSize = 10.sp, color = ComposeColor.Gray)
            }
            IconButton(onClick = onPreview) { Text("🔍", fontSize = 14.sp) }
            IconButton(onClick = onDownload) { Text("💾", fontSize = 14.sp) }
        }
    }
}
