package com.nono.sensor_log

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SensorUtils {

    fun createReportJson(session: SensorDataManager.LogSession): JSONObject {
        return JSONObject().apply {
            put("session_id", session.id)
            put("sync_time", session.timestamp)
            put("event_type", session.type)
            put("raw_content", session.content)
        }
    }

    fun createMergedReportJson(report: SensorDataManager.MergedReport): JSONObject {
        return JSONObject().apply {
            put("date", report.date)
            put("start_time", report.startTime)
            put("end_time", report.endTime)
            put("sensor_type", report.sensorType)
            put("sample_count", report.count)
            put("level", report.level)
            put("data", JSONArray(report.rawData))
        }
    }

    fun exportLogToJson(context: Context, json: JSONObject, fileName: String) {
        try {
            val file = File(context.cacheDir, fileName)
            file.writeText(json.toString(4))
            shareFile(context, file, "application/json")
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享報告檔案"))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "分享失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打包指定目錄為 ZIP
     */
    fun zipFolder(sourceFolder: File, zipFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                sourceFolder.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entryName = file.relativeTo(sourceFolder).path
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
