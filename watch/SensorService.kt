package com.nono.sensor_log.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable

/**
 * SensorService: 核心背景服務，負責感測器數據採集、節流與遠端傳輸。
 * 即使 App 介面關閉，此服務仍會持續運行。
 */
class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    
    // --- 數據狀態 ---
    private val sensorLastSendTimeMap = mutableMapOf<String, Long>()
    private val sensorAccuracyMap = mutableMapOf<String, Int>() // 記錄各感測器的最新精確度
    
    // 💡 修改發送頻率請改這裡：1000L 代表 1 秒發送一次 (1Hz)
    private val reportInterval = 500L
    
    // 定期補發身分宣告的間隔 (60秒)
    private val identityReportInterval = 60 * 1000L
    private var lastIdentitySendTime = 0L

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false // 提供給 MainActivity 同步 UI 狀態
    }

    // ==========================================
    // 🧬 服務生命週期 (Service Lifecycle)
    // ==========================================

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. 建立前台通知，確保服務不被系統殺掉
        val notification = createNotification()
        
        // 2. 啟動前台服務 (根據 Android 版本宣告對應類型)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }
        
        // 3. 開始採樣與標記狀態
        registerSensorsForCollection()
        isRunning = true
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ==========================================
    // 🛠️ 感測器採集邏輯 (Sensor Core)
    // ==========================================

    override fun onSensorChanged(event: SensorEvent) {
        val sensorName = event.sensor.name
        val currentTime = System.currentTimeMillis()
        val lastSendTime = sensorLastSendTimeMap[sensorName] ?: 0L
        
        // 🔒 數據過濾：若該感測器目前的精確度為 UNRELIABLE (0)，則不處理/不發送
        val currentAccuracy = sensorAccuracyMap[sensorName] ?: SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        if (currentAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }

        // 💡 軟體節流 (Throttling)：依據 reportInterval 過濾發送頻率
        if ((currentTime - lastSendTime) >= reportInterval) {
            val valuesString = event.values.joinToString(",") { "%.3f".format(it) }
            
            // 執行發送
            sendToPhone("SENSOR|$sensorName|$valuesString")
            sensorLastSendTimeMap[sensorName] = currentTime
            
            // 定期補發身分宣告，確保手機端快取有效
            if ((currentTime - lastIdentitySendTime) >= identityReportInterval) {
                sendIdentityToPhone()
                lastIdentitySendTime = currentTime
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 更新並記錄該感測器的最新精確度狀態
        sensor?.let {
            sensorAccuracyMap[it.name] = accuracy
        }
    }

    /**
     * 註冊所有可用的感測器
     */
    private fun registerSensorsForCollection() {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        for (sensor in allSensors) {
            // 背景數據採集使用較高的 UI 頻率 (約 20Hz)
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // ==========================================
    // 📡 通訊與宣告 (Communication)
    // ==========================================

    /**
     * 宣告硬體身分資訊 (符合 v1.2 規範)
     */
    private fun sendIdentityToPhone() {
        val model = Build.MODEL
        val fingerprint = Build.FINGERPRINT
        sendToPhone("INFO|WatchHardware|$model|$fingerprint")
    }

    /**
     * 底層發送邏輯：透過 MessageClient 尋找節點並傳輸
     */
    private fun sendToPhone(message: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(
                    node.id, 
                    "/all_sensors_data", 
                    message.toByteArray()
                )
            }
        }
    }

    // ==========================================
    // 🔔 通知管理 (Notification Helpers)
    // ==========================================

    private fun createNotification(): Notification {
        // 點擊通知可回到 MainActivity
        val notificationIntent = Intent(this, Class.forName("com.nono.sensor_log.presentation.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("感測器紀錄中")
            .setContentText("正在背景收集感測器數據...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}
