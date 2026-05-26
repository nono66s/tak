package com.nono.sensor_log

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

data class SensorInfo(
    val id: String,
    val name: String,
    val unit: String,
    val icon: String,
    val description: String,
)

object SensorDataManager {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)
    
    var liveSensorData = mutableStateOf(value = "等待數據...")
    var lastUpdateTime = mutableStateOf(value = "-")
    val latestSensorValues = androidx.compose.runtime.mutableStateMapOf<String, String>()
    val sensorHistory = androidx.compose.runtime.mutableStateMapOf<String, MutableList<HistoricalEntry>>()
    data class HistoricalEntry(val timestamp: Long, val value: String)

    val consoleLogs = mutableStateListOf<String>()
    val debugLogs = mutableStateListOf<String>()
    
    // --- 溯源與會話資訊 ---
    var phoneNickname = mutableStateOf(value = "手機A")
    var watchNickname = mutableStateOf(value = "手錶B")
    
    var phoneId: String = "Unknown"
    var phoneAppInstanceId: String = ""
    var phoneName: String = "Android Device"
    var phoneModel: String = Build.MODEL
    var phoneFingerprint: String = Build.FINGERPRINT

    var pairedWatchId = mutableStateOf(value = "")
    var pairedWatchModel = mutableStateOf(value = "未配對")
    var pairedWatchFingerprint = mutableStateOf(value = "Unknown")
    
    var currentSessionId = mutableStateOf(value = UUID.randomUUID().toString())
    private val messageCounter = AtomicLong(0)

    fun startNewSession() {
        currentSessionId.value = UUID.randomUUID().toString()
        messageCounter.set(0)
        addConsoleLog("🚀 已開啟新 Session: ${currentSessionId.value.take(8)}...")
        addDebugLog("系統: 已手動重置 Session 與 MessageID")
    }

    private val watchModelCache = mutableMapOf<String, String>()
    private val watchFingerprintCache = mutableMapOf<String, String>()

    data class LogSession(
        val id: String, 
        val timestamp: String, 
        val content: String, 
        val type: String,
        val watchId: String,
        val watchModel: String,
        val watchNickname: String,
        val phoneId: String,
        val phoneModel: String,
        val phoneNickname: String
    )
    val logGroups = mutableStateListOf<LogSession>()
    val mergedReports = mutableStateListOf<MergedReport>()
    data class MergedReport(val date: String, val startTime: String, val endTime: String, val sensorType: String, val count: Int, val rawData: List<String>, val level: Int = 0)
    val dataAvailability = androidx.compose.runtime.mutableStateMapOf<String, MutableSet<Int>>()

    private const val PREFS_NAME = "sensor_log_prefs"
    var serverUrl = mutableStateOf(value = "https://webhook.site/ed90d185-0fee-4240-904a-cc0a181c9137")
    val urlHistory = mutableStateListOf<String>()
    var showSensorLog = mutableStateOf(value = false)
    var isAutoUpload = mutableStateOf(value = false)
    var isSaveLocal = mutableStateOf(value = true)
    var isDarkMode = mutableStateOf(value = false)

    val sensorUploadToggles = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    
    // --- 校準量設定 ---
    var samsungRotationCalibration = mutableStateOf(0.0f)
    val coreSensorOrder = mutableStateListOf<String>()
    val dynamicSensorOrder = mutableStateListOf<String>()
    val ignoredSensorOrder = mutableStateListOf<String>()

    val sensorDefinitions = mapOf(
        "Batch" to SensorInfo("Batch", "批量心率", "bpm", "💓", "匯集一段時間內的心率樣本統一發送，用於分析靜息心率趨勢並大幅降低電量消耗。"),
        "HR None Wakeup" to SensorInfo("HR None Wakeup", "標準心率 (背景)", "bpm", "💟", "低功耗後台心率監測，確保在設備進入休眠狀態時仍能持續獲取生理體徵數據。"),
        "Location" to SensorInfo("Location", "手機 GPS", "lat, lon", "📍", "結合 GPS/GLONASS 衛星訊號獲取經緯度與速度，用於地理空間路徑分析。"),
        "Barometer" to SensorInfo("Barometer", "氣壓計", "hPa", "☁️", "測量絕對大氣壓力，用於計算氣壓高度（Altitude）以及偵測室內樓層爬升變化。"),
        "Accelerometer" to SensorInfo("Accelerometer", "加速度計", "m/s²", "🚀", "感應三軸線性加速度與重力分量，是偵測步態、跌倒及設備震動頻譜的核心數據。"),
        "Gyroscope" to SensorInfo("Gyroscope", "陀螺儀", "rad/s", "🔄", "測量設備繞三軸轉動的角速度，結合加速度計用於精確計算設備的姿態角與旋轉路徑。"),
        "Gravity" to SensorInfo("Gravity", "重力感測器", "m/s²", "🌍", "從加速度計中分離出的重力向量，指向地心方向，用於確定設備在三軸空間中的絕對取向。"),
        "Magnetometer" to SensorInfo("Magnetometer", "磁力計", "μT", "🧲", "偵測環境磁場強度，用於電子羅盤定向、偵測周圍金屬干擾或地磁異常。"),
        "Light" to SensorInfo("Light", "環境光感測", "lux", "☀️", "測量環境照度，用於分析使用者所處環境光線強度，或判斷室內外場景切換。"),
        "Linear Acceleration" to SensorInfo("Linear Acceleration", "線性加速度", "m/s²", "🏎️", "扣除重力影響後的純運動加速度，用於精確量化人體運動的動力輸出與突發位移。"),
        "Samsung Rotation Vector" to SensorInfo("Samsung Rotation Vector", "Samsung 旋轉向量", "", "🧭", "基於四元數（Quaternion）融合算法，提供無漂移的 3D 空間旋轉與姿態資訊。"),
        "Heart Rate" to SensorInfo("Heart Rate", "標準心率", "bpm", "💟", "標準光學心率感測，輸出每分鐘心跳數（BPM），反映循環系統即時生理負荷。"),
        "Samsung Offbody Detector" to SensorInfo("Samsung Offbody Detector", "配戴偵測 (標準)", "", "⌚", "利用紅外線或電容感應，即時判定設備是否與皮膚接觸，確保數據採集於有效佩戴狀態。"),
        "Samsung LowPower Offbody Detector" to SensorInfo("Samsung LowPower Offbody Detector", "配戴偵測 (低功耗)", "", "⌚", "以最低功耗頻率運行的佩戴檢測，用於系統自動暫停/恢復高耗電採樣任務。"),
        "Magnetometer Uncalibrated" to SensorInfo("Magnetometer Uncalibrated", "磁力計 (未校準)", "μT", "🧲", "輸出未經過環境偏置補償的原始磁場數據，供進階地磁建模與校準演算法使用。"),
        "Gyroscope Uncalibrated" to SensorInfo("Gyroscope Uncalibrated", "陀螺儀 (未校準)", "rad/s", "🔄", "未經零點漂移修正的原始角速度數據，適合需要極致低延遲處理的運動控制應用。"),
        "Game Rotation Vector" to SensorInfo("Game Rotation Vector", "遊戲旋轉向量", "", "🎮", "基於陀螺儀與加速度計融合，不使用磁力計，可避免室內金屬環境引起的磁場干擾偏轉。"),
        "GeoMag Rotation Vector" to SensorInfo("GeoMag Rotation Vector", "地磁旋轉向量", "", "🧭", "基於地磁與加速度計融合的旋轉向量，適合低速移動下的靜態指向與導航輔助。"),
        "HR Raw" to SensorInfo("HR Raw", "即時心率 (Raw)", "bpm", "❤️", "原始光學體積變化描記圖（PPG）訊號，包含心血管搏動引起的反射光強度特徵。"),
    )

    private var isInitialized = false

    @SuppressLint("HardwareIds")
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        phoneId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "UnknownID"
        phoneName = try { Settings.Global.getString(appContext.contentResolver, "device_name") ?: Settings.Secure.getString(appContext.contentResolver, "bluetooth_name") ?: Build.MODEL } catch (_: Exception) { Build.MODEL }
        loadSettingsFromPrefs()
        isInitialized = true
    }

    private fun loadSettingsFromPrefs() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        serverUrl.value = prefs.getString("current_url", serverUrl.value) ?: serverUrl.value
        isDarkMode.value = prefs.getBoolean("is_dark_mode", false)
        phoneAppInstanceId = prefs.getString("app_instance_id", "") ?: ""
        if (phoneAppInstanceId.isEmpty()) { phoneAppInstanceId = UUID.randomUUID().toString(); prefs.edit().putString("app_instance_id", phoneAppInstanceId).apply() }
        phoneNickname.value = prefs.getString("phone_nickname", "手機A") ?: "手機A"
        watchNickname.value = prefs.getString("watch_nickname", "手錶B") ?: "手錶B"
        pairedWatchId.value = prefs.getString("paired_watch_id", "") ?: ""
        pairedWatchModel.value = prefs.getString("paired_watch_model", "未配對") ?: "未配對"
        pairedWatchFingerprint.value = prefs.getString("paired_watch_fp", "Unknown") ?: "Unknown"
        prefs.getStringSet("url_history_set", setOf(serverUrl.value))?.let { urlHistory.clear(); urlHistory.addAll(it) }
        fun loadList(prefKey: String, targetList: MutableList<String>) { prefs.getString(prefKey, "[]")?.let { try { val arr = JSONArray(it); targetList.clear(); for (i in 0 until arr.length()) targetList.add(arr.getString(i)) } catch (_: Exception) { } } }
        loadList("core_order_json", coreSensorOrder); loadList("dynamic_order_json", dynamicSensorOrder); loadList("ignored_order_json", ignoredSensorOrder)
        prefs.getString("sensor_toggles_json", "{}")?.let { try { val json = JSONObject(it); json.keys().forEach { k -> sensorUploadToggles[k] = json.getBoolean(k) } } catch (_: Exception) { } }
    }

    fun saveSettingsToPrefs() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val togglesJson = JSONObject(); sensorUploadToggles.forEach { (k, v) -> togglesJson.put(k, v) }
        prefs.edit().apply {
            putString("sensor_toggles_json", togglesJson.toString()); putString("core_order_json", JSONArray(coreSensorOrder).toString())
            putString("dynamic_order_json", JSONArray(dynamicSensorOrder).toString()); putString("ignored_order_json", JSONArray(ignoredSensorOrder).toString())
            putString("current_url", serverUrl.value); putStringSet("url_history_set", urlHistory.toSet())
            putBoolean("is_dark_mode", isDarkMode.value)
            putString("phone_nickname", phoneNickname.value); putString("watch_nickname", watchNickname.value)
            putString("paired_watch_id", pairedWatchId.value); putString("paired_watch_model", pairedWatchModel.value); putString("paired_watch_fp", pairedWatchFingerprint.value)
            apply()
        }
    }

    fun handleMessage(message: String, sourceId: String) {
        if (message.startsWith("INFO|WatchHardware|")) {
            val parts = message.split("|"); val model = parts.getOrNull(2) ?: "Unknown"; val fingerprint = parts.getOrNull(3) ?: "Unknown"
            watchModelCache[sourceId] = model; watchFingerprintCache[sourceId] = fingerprint
            if (sourceId == pairedWatchId.value) { pairedWatchModel.value = model; pairedWatchFingerprint.value = fingerprint; saveSettingsToPrefs() }
            addConsoleLog("⌚ 獲取手錶指紋: $model ($sourceId)"); return
        }
        if (!message.startsWith("SENSOR|")) return
        val parts = message.split("|"); if (parts.size < 2) return
        val sensorName = parts[1]; var sensorVal = parts.getOrNull(2) ?: "0"
        
        // --- 針對 Samsung Rotation Vector 套用校準補償 ---
        if (sensorName.contains("Samsung Rotation Vector", ignoreCase = true)) {
            sensorVal = "$sensorVal, ${samsungRotationCalibration.value}"
        }

        val now = System.currentTimeMillis(); val fullTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        markDataAvailable(now)
        if (!latestSensorValues.containsKey(sensorName)) {
            val ignoredKeywords = listOf("Uncalibrated", "LowPower", "Game", "GeoMag", "HR Raw")
            val isIgnored = ignoredKeywords.any { sensorName.contains(it, ignoreCase = true) }
            if (isIgnored) { if (!ignoredSensorOrder.contains(sensorName)) ignoredSensorOrder.add(sensorName) } 
            else { val isKnown = sensorDefinitions.keys.any { sensorName.contains(it, ignoreCase = true) }; if (isKnown) { if (!coreSensorOrder.contains(sensorName)) coreSensorOrder.add(sensorName) } else { if (!dynamicSensorOrder.contains(sensorName)) dynamicSensorOrder.add(sensorName) } }
            if (!sensorUploadToggles.containsKey(sensorName)) sensorUploadToggles[sensorName] = !isIgnored && (sensorDefinitions.containsKey(sensorName) || sensorDefinitions.keys.any { sensorName.contains(it, ignoreCase = true) })
        }
        CoroutineScope(Dispatchers.Main).launch { liveSensorData.value = message; lastUpdateTime.value = fullTime.split(" ").last()
            latestSensorValues[sensorName] = sensorVal
            val history = sensorHistory.getOrPut(sensorName) { mutableListOf() }
            history.add(HistoricalEntry(now, sensorVal))
            if (history.size > 100) history.removeAt(0)
        }
        val pWatchId = pairedWatchId.value.ifBlank { if(sourceId != "Phone") sourceId else "未配對" }; val pWatchModel = watchModelCache[sourceId] ?: pairedWatchModel.value; val pWatchFP = watchFingerprintCache[sourceId] ?: pairedWatchFingerprint.value; val pWatchNick = watchNickname.value
        if (isSaveLocal.value) CoroutineScope(Dispatchers.Main).launch { logGroups.add(LogSession(UUID.randomUUID().toString(), fullTime, message, sensorName, pWatchId, pWatchModel, pWatchNick, phoneId, phoneModel, phoneNickname.value)); if (logGroups.size > 15000) logGroups.removeAt(0) }
        if (isAutoUpload.value && (sensorUploadToggles[sensorName] != false)) uploadUltraStructuredData(message, sensorName, sensorVal, pWatchId, pWatchModel, pWatchNick, pWatchFP, phoneId, phoneAppInstanceId, phoneModel, phoneName, phoneNickname.value, phoneFingerprint, now) 
    }

    private fun uploadUltraStructuredData(rawMessage: String, type: String, value: String, wId: String, wModel: String, wNick: String, wFP: String, pId: String, pAppId: String, pModel: String, pName: String, pNick: String, pFP: String, timestamp: Long) {
        val mId = messageCounter.getAndIncrement()
        scope.launch {
            try {
                val conn = URL(serverUrl.value).openConnection() as HttpURLConnection
                conn.apply { requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); doOutput = true }
                OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().apply {
                    put("watch_info", JSONObject().apply { put("id", wId); put("nickname", wNick); put("model", wModel); put("system_fingerprint", wFP) })
                    put("phone_info", JSONObject().apply { put("id", pId); put("app_instance_id", pAppId); put("nickname", pNick); put("system_name", pName); put("model", pModel); put("system_fingerprint", pFP) })
                    put("session_id", currentSessionId.value); put("message_id", mId); put("timestamp", timestamp); put("event_type", "SENSOR_DATA"); put("sensor_name", type); put("value", value); put("raw_payload", rawMessage)
                }.toString()) }
                if (conn.responseCode in 200..299) {
                    // 為了減少日誌負擔，僅在專家模式或特定間隔紀錄成功上傳，這裡暫時紀錄在除錯日誌
                    addDebugLog("成功上傳: $type")
                } else {
                    addConsoleLog("❌ 上傳異常 (${conn.responseCode})")
                }
            } catch (e: Exception) { addConsoleLog("❌ 網路錯誤: ${e.message}") }
        }
    }

    fun addConsoleLog(message: String) { val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()); CoroutineScope(Dispatchers.Main).launch { consoleLogs.add("[$time] $message"); if (consoleLogs.size > 100) consoleLogs.removeAt(0) } }
    fun addDebugLog(message: String) { val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()); CoroutineScope(Dispatchers.Main).launch { debugLogs.add("[$time] $message"); if (debugLogs.size > 50) debugLogs.removeAt(0) } }
    private fun markDataAvailable(timestamp: Long) { val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp)); val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY); val hours = dataAvailability.getOrPut(date) { mutableSetOf() }; if (!hours.contains(hour)) CoroutineScope(Dispatchers.Main).launch { hours.add(hour) } }
    fun updatePhoneLocation(location: Location) { handleMessage(String.format(Locale.US, "SENSOR|Location|%.6f, %.6f", location.latitude, location.longitude), "Phone") }

    fun mergeAllData() {
        if (logGroups.isEmpty()) { upgradeHourlyToDaily(); return }
        val logsByDate = logGroups.toList().groupBy { it.timestamp.split(" ")[0] }
        logsByDate.forEach { (logDate, dateLogs) ->
            dateLogs.groupBy { it.type }.forEach { (type, typeLogs) ->
                archiveAndZip(logDate, type, typeLogs)
                typeLogs.groupBy { it.timestamp.split(" ")[1].split(":")[0] }.forEach { (hour, hourLogs) ->
                    val hourStart = "$hour:00:00"
                    val existing = mergedReports.find { (it.date == logDate) && (it.sensorType == type) && (it.startTime == hourStart) && (it.level == 1) }
                    val newData = hourLogs.map { "${it.timestamp.split(" ").last()}|${it.content.split("|").last()}" }
                    if (existing != null) {
                        val updated = existing.copy(count = existing.count + hourLogs.size, rawData = existing.rawData + newData)
                        mergedReports.remove(existing); mergedReports.add(0, updated)
                    } else {
                        mergedReports.add(0, MergedReport(logDate, hourStart, "$hour:59:59", type, hourLogs.size, newData, 1))
                    }
                }
            }
        }
        logGroups.clear()
        addConsoleLog("✅ 歸檔完成")
    }

    private fun archiveAndZip(date: String, type: String, logs: List<LogSession>) {
        scope.launch {
            try {
                val dir = File(getRawArchiveFolder(), "$date/$type")
                if (!dir.exists()) dir.mkdirs()
                val jsonArray = JSONArray()
                logs.forEach { log ->
                    jsonArray.put(JSONObject().apply {
                        put("time", log.timestamp.split(" ").last())
                        put("data", log.content.split("|").last())
                    })
                }
                File(dir, "raw_${System.currentTimeMillis()}.json").writeText(jsonArray.toString(4))
                val zipFile = File(getExportZipFolder(), "audit_${type.replace(" ", "_")}_$date.zip")
                SensorUtils.zipFolder(dir, zipFile)
            } catch (e: Exception) { addConsoleLog("❌ 存檔失敗: ${e.message}") }
        }
    }

    private fun upgradeHourlyToDaily() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val overdueHourly = mergedReports.filter { (it.level == 1) && (it.date != todayStr) }
        if (overdueHourly.isEmpty()) return
        overdueHourly.groupBy { it.date }.forEach { (date, dayReports) ->
            dayReports.groupBy { it.sensorType }.forEach { (type, typeReports) ->
                val dailyReport = MergedReport(date, "00:00:00", "23:59:59", type, typeReports.sumOf { it.count }, typeReports.flatMap { it.rawData }, 2)
                mergedReports.removeAll(typeReports); mergedReports.add(dailyReport)
            }
        }
        addConsoleLog("✅ 自動升級日報表")
    }

    fun setSensorUpload(id: String, enabled: Boolean) { sensorUploadToggles[id] = enabled; saveSettingsToPrefs() }

    fun exportSettings(context: Context) {
        try {
            val json = JSONObject()
            val toggles = JSONObject()
            sensorUploadToggles.forEach { (k, v) -> toggles.put(k, v) }
            json.put("toggles", toggles)
            val file = File(context.cacheDir, "sensor_settings.json")
            file.writeText(json.toString(4))
            SensorUtils.shareFile(context, file, "application/json")
        } catch (e: Exception) {
            addConsoleLog("❌ 導出失敗: ${e.message}")
        }
    }

    fun importSettings(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val toggles = json.optJSONObject("toggles")
            toggles?.keys()?.forEach { key ->
                sensorUploadToggles[key] = toggles.getBoolean(key)
            }
            saveSettingsToPrefs()
            addConsoleLog("✅ 設定導入成功")
        } catch (e: Exception) {
            addConsoleLog("❌ 導入失敗: ${e.message}")
        }
    }
    fun getBaseFolder(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SensorLogs").apply { if (!exists()) mkdirs() }
    fun getRawArchiveFolder(): File = File(getBaseFolder(), "RawArchives").apply { if (!exists()) mkdirs() }
    fun getExportZipFolder(): File = File(getBaseFolder(), "ExportZips").apply { if (!exists()) mkdirs() }
}
