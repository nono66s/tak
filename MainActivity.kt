package com.nono.sensor_log

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.nono.sensor_log.ui.theme.Phone_sensor_logTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var controlManager: RemoteControlManager

    companion object {
        var instance: MainActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        viewModel = MainViewModel(applicationContext)
        controlManager = RemoteControlManager(this)

        checkPermissionsAndStartService()

        setContent {
            Phone_sensor_logTheme {
                MainAppNavigation(viewModel = viewModel, onControl = controlManager)
            }
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                startSensorService()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkBackgroundLocationPermission()
                }
            } else {
                Toast.makeText(this, "需要定位權限以紀錄數據", Toast.LENGTH_LONG).show()
            }
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "請在設定中將定位權限改為「始終允許」，以確保螢幕關閉時仍能紀錄。", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorLogService::class.java)
        startForegroundService(intent)
    }

    @Composable
    fun MainAppNavigation(viewModel: MainViewModel, onControl: RemoteControlManager) {
        var selectedTab by remember { mutableIntStateOf(0) }
        val titles = listOf("監控", "配對", "連線", "除錯")

        Scaffold(
            bottomBar = {
                NavigationBar {
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(title) },
                            icon = {
                                Text(
                                    text = if (selectedTab == index) "●" else "○",
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> MonitorPage(
                        viewModel = viewModel
                    )
                    1 -> PairingPage()
                    2 -> SettingsPage(viewModel = viewModel, onControl = onControl)
                    3 -> Column(Modifier.fillMaxSize()) {
                        var isLogExpanded by remember { mutableStateOf(false) }
                        val scrollState = rememberScrollState()
                        
                        // 1. 控制工具區 (當展開時完全隱藏)
                        if (!isLogExpanded) {
                            Column(modifier = Modifier.weight(0.75f).verticalScroll(scrollState)) {
                                SamsungCalibrationTool()

                                PhoneDebugTool(
                                    onSendMessage = {
                                        onControl.sendCommand("/message_path", it)
                                        viewModel.addDebugLog("發送指令: $it")
                                    },
                                    onSendRandom = {
                                        val randomValue = (1000..9999).random()
                                        onControl.sendCommand("/test_path", randomValue.toString())
                                        viewModel.addDebugLog("發送隨機數據: $randomValue")
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 1.dp)
                        }

                        // 2. 指令歷史標題與展開按鈕
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "通訊指令歷史", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    fontWeight = FontWeight.Bold, 
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { isLogExpanded = !isLogExpanded },
                                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                ) {
                                    Text(if (isLogExpanded) "🔽" else "🔼", fontSize = 12.sp)
                                }
                            }
                            TextButton(onClick = { viewModel.clearDebugLogs() }) {
                                Text("清除歷史", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        // 3. 日誌控制台 (動態調整權重：預設 25% 或展開 100%)
                        LogConsoleView(
                            logs = viewModel.debugLogs, 
                            modifier = Modifier
                                .weight(if (isLogExpanded) 1.0f else 0.25f)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(if (isLogExpanded) 0.dp else 8.dp))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}

@Composable
fun SamsungCalibrationTool() {
    var calValue by remember { mutableStateOf(SensorDataManager.samsungRotationCalibration.value.toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "📐 Samsung 感測器校準", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(text = "校準量將追加為 Rotation Vector 的最後一個值", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = calValue,
                    onValueChange = { calValue = it },
                    label = { Text("校準補償值", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        val newValue = calValue.toFloatOrNull() ?: 0.0f
                        SensorDataManager.samsungRotationCalibration.value = newValue
                        SensorDataManager.addDebugLog("系統: 已將校準量設為 $newValue")
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("設定", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LogConsoleView(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    // 修改：背景改用 surfaceVariant 並加入透明度，讓它更融入主題
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).padding(12.dp)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                Text(
                    text = log,
                    color = when {
                        log.contains("❌") -> Color(0xFFF23F42) // Discord Red
                        log.contains("發送") -> MaterialTheme.colorScheme.primary
                        log.contains("✅") -> Color(0xFF23A559) // Discord Green
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

class MainViewModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("sensor_log_prefs", Context.MODE_PRIVATE)

    init {
        SensorDataManager.init(context)
    }

    val latestSensorValues = SensorDataManager.latestSensorValues
    val sensorHistory = SensorDataManager.sensorHistory
    val lastUpdateTime = SensorDataManager.lastUpdateTime
    val consoleLogs = SensorDataManager.consoleLogs
    val debugLogs = SensorDataManager.debugLogs
    val logGroups = SensorDataManager.logGroups

    val serverUrl = SensorDataManager.serverUrl
    val urlHistory = SensorDataManager.urlHistory

    val showSensorLog = SensorDataManager.showSensorLog
    val isAutoUpload = SensorDataManager.isAutoUpload

    fun addDebugLog(message: String) = SensorDataManager.addDebugLog(message)

    fun saveUrl(newUrl: String) {
        if (newUrl.isBlank()) return
        serverUrl.value = newUrl
        if (!urlHistory.contains(newUrl)) urlHistory.add(newUrl)
        prefs.edit {
            putString("current_url", newUrl)
            putStringSet("url_history_set", urlHistory.toSet())
        }
        SensorDataManager.addConsoleLog("伺服器變更: $newUrl")
    }

    fun deleteUrl(target: String) {
        urlHistory.remove(target)
        prefs.edit { putStringSet("url_history_set", urlHistory.toSet()) }
    }

    fun clearDebugLogs() {
        debugLogs.clear()
        addDebugLog("系統: 指令紀錄已重置")
    }

    fun mergeAllData() = SensorDataManager.mergeAllData()
    val mergedReports = SensorDataManager.mergedReports
    val dataAvailability = SensorDataManager.dataAvailability
}
