package com.nono.sensor_log.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.nono.sensor_log.presentation.theme.Watch_sensor_logTheme
import com.nono.sensor_log.service.SensorService
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity: 負責顯示使用者介面、處理權限與身分宣告。
 * 核心傳輸邏輯已遷移至 SensorService。
 */
class MainActivity : ComponentActivity(), SensorEventListener {

    // --- 系統服務 ---
    private lateinit var sensorManager: SensorManager
    private var offBodySensor: Sensor? = null

    // --- UI 顯示狀態 (Compose State) ---
    private var lastActiveSensorName by mutableStateOf("無活動")
    private var isTransporting by mutableStateOf(false)
    private var isDeviceWorn by mutableStateOf(false)
    private val debugLogs = mutableStateListOf("等待連線...")
    private var currentScreen by mutableIntStateOf(0) // 0=主頁, 1=清單, 2=除錯

    // --- 訊息監聽器 (接收手機端指令) ---
    private val onMessageReceivedListener = MessageClient.OnMessageReceivedListener { event ->
        val msg = String(event.data, Charsets.UTF_8)
        runOnUiThread {
            addDebugLog("收到訊息: $msg")
        }
    }

    // ==========================================
    // 🧬 生命週期管理 (Lifecycle)
    // ==========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化系統服務
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        offBodySensor = sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

        // 啟動前檢查：請求權限並宣告身分
        checkAndRequestPermissions()
        sendIdentityToPhone()

        setContent {
            Watch_sensor_logTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                        when (screen) {
                            0 -> MainDashboard(
                                onNavigateToList = { currentScreen = 1 },
                                onNavigateToDebug = { currentScreen = 2 }
                            )
                            1 -> SensorListScreen(onBack = { currentScreen = 0 })
                            2 -> DebugToolsScreen(onBack = { currentScreen = 0 })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 註冊感測器以供介面更新 (lastActiveSensorName)
        registerSensorsForUI()
        Wearable.getMessageClient(this).addListener(onMessageReceivedListener)
        
        // 如果背景服務正在執行，同步 UI 的傳輸狀態
        if (SensorService.isRunning) {
            isTransporting = true
        }
    }

    override fun onPause() {
        super.onPause()
        // 介面不可見時停止介面層級的監聽，節省電力
        Wearable.getMessageClient(this).removeListener(onMessageReceivedListener)
        sensorManager.unregisterListener(this)
    }

    // ==========================================
    // 🎨 介面組件 (Composables)
    // ==========================================

    @Composable
    fun MainDashboard(onNavigateToList: () -> Unit, onNavigateToDebug: () -> Unit) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp)
        ) {
            // 佩戴狀態
            item {
                Text(
                    text = if (isDeviceWorn) "⌚ 已佩戴手錶" else "❌ 請戴上手錶",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDeviceWorn) Color.Green else Color.Red
                )
            }
            // 傳輸狀態
            item {
                Text(
                    text = if (isTransporting) "● 數據傳輸中" else "○ 已停止傳輸",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isTransporting) Color.Cyan else Color.LightGray
                )
            }
            // 活躍感測器顯示
            item {
                Text(
                    text = "感測器: $lastActiveSensorName",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(20.dp)
                )
            }
            item { Spacer(modifier = Modifier.height(10.dp)) }
            
            // 操作按鈕：啟動/停止背景服務
            item {
                Button(
                    onClick = {
                        if (!isTransporting) {
                            if (!isDeviceWorn) addDebugLog("警告: 未偵測到佩戴")
                            
                            // 啟動背景服務 (SensorService)
                            val intent = Intent(this@MainActivity, SensorService::class.java)
                            startForegroundService(intent)
                            
                            sendToPhone("LOG|WATCH_START|開始傳輸")
                        } else {
                            // 停止背景服務
                            stopService(Intent(this@MainActivity, SensorService::class.java))
                            sendToPhone("LOG|WATCH_STOP|停止傳輸")
                        }
                        isTransporting = !isTransporting
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTransporting) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    )
                ) {
                    Text(if (isTransporting) "停止發送" else "開始發送")
                }
            }
            
            // 導航按鈕
            item {
                FilledTonalButton(onClick = onNavigateToList, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text("感測器清單")
                }
            }
            item {
                FilledTonalButton(onClick = onNavigateToDebug, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text("通訊除錯")
                }
            }
        }
    }

    @Composable
    fun SensorListScreen(onBack: () -> Unit) {
        val allSensors = remember { sensorManager.getSensorList(Sensor.TYPE_ALL) }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 50.dp, bottom = 45.dp),
            autoCentering = AutoCenteringParams(itemIndex = 0)
        ) {
            item {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("← 返回", color = Color.LightGray, fontSize = 14.sp)
                }
            }
            item {
                Button(
                    onClick = { sendSensorListToPhone() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("同步清單至手機", fontSize = 12.sp)
                }
            }
            item {
                Text(
                    "感測器總數: ${allSensors.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Yellow,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            items(allSensors) { sensor ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Text(sensor.name, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("TYPE: ${sensor.stringType.split(".").last().uppercase()}", fontSize = 10.sp, color = Color(0xFF81D4FA))
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = Color.DarkGray)
                }
            }
        }
    }

    @Composable
    fun DebugToolsScreen(onBack: () -> Unit) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 50.dp, bottom = 45.dp),
            autoCentering = AutoCenteringParams(itemIndex = 0)
        ) {
            item {
                TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("← 返回", color = Color.LightGray, fontSize = 14.sp)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color.Yellow, shape = RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("手機指令紀錄", style = MaterialTheme.typography.labelMedium)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(debugLogs) { log ->
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                    .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(4.dp)).padding(6.dp)) {
                    Text(text = log, style = MaterialTheme.typography.labelSmall,
                        color = if (log.contains("失敗") || log.contains("❌")) Color.Red else Color.Yellow,
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            item {
                Button(onClick = { debugLogs.clear(); addDebugLog("紀錄已重置") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 24.dp, end = 24.dp)) {
                    Text("清空紀錄", fontSize = 12.sp)
                }
            }
        }
    }

    // ==========================================
    // 🛠️ 感測器邏輯 (Sensor Core)
    // ==========================================

    override fun onSensorChanged(event: SensorEvent) {
        // 處理佩戴偵測
        if (event.sensor.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            isDeviceWorn = (event.values[0].toInt() == 1)
            return
        }

        // 僅更新介面上的名稱，實際數據發送由 SensorService 負責
        lastActiveSensorName = event.sensor.name
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun registerSensorsForUI() {
        // 在介面開啟時，監聽所有感測器以呈現活動狀態
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        for (sensor in allSensors) {
            // UI 呈現使用較慢的頻率以省電
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        offBodySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    /**
     * 權限檢查與請求
     */
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS
        )
        // Android 13+ 健康數據權限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add("android.permission.health.READ_HEART_RATE")
            permissions.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
    }

    // ==========================================
    // 📡 數據通訊 (Communication)
    // ==========================================

    /**
     * 發送字串訊息到手機端
     */
    private fun sendToPhone(message: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(node.id, "/all_sensors_data", message.toByteArray())
            }
        }
    }

    /**
     * 宣告硬體身分資訊 (符合 v1.2 規範)
     */
    private fun sendIdentityToPhone() {
        val model = Build.MODEL
        val fingerprint = Build.FINGERPRINT
        val message = "INFO|WatchHardware|$model|$fingerprint"
        sendToPhone(message)
        addDebugLog("已同步設備身分至手機")
    }

    /**
     * 將感測器清單同步至手機
     */
    private fun sendSensorListToPhone() {
        val list = sensorManager.getSensorList(Sensor.TYPE_ALL).joinToString("\n") { it.name }
        sendToPhone("LOG|SENSOR_LIST|$list")
        addDebugLog("感測器清單同步成功")
    }

    /**
     * 內部輔助：新增除錯日誌
     */
    private fun addDebugLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugLogs.add(0, "[$time] $msg")
        if (debugLogs.size > 20) debugLogs.removeAt(20)
    }
}
