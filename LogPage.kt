package com.nono.sensor_log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LogPage(viewModel: MainViewModel) {
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf("內容預覽") }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- 系統控制台區域 (專注於 App 運作日誌) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "系統主控台",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    previewTitle = "完整系統日誌"
                    previewText = viewModel.consoleLogs.joinToString("\n")
                }) {
                    Text("(全螢幕查看)", fontSize = 10.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("即時追蹤", fontSize = 11.sp, color = Color.Gray)
                Switch(
                    checked = viewModel.showSensorLog.value,
                    onCheckedChange = { viewModel.showSensorLog.value = it },
                    modifier = Modifier.scale(0.7f)
                )
            }
        }

        LogConsoleView(
            logs = viewModel.consoleLogs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 現在控制台佔滿整個頁面
        )

        // 底部按鈕：清空控制台
        Button(
            onClick = { viewModel.consoleLogs.clear() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
        ) {
            Text("清除控制台日誌")
        }
    }

    // 全螢幕預覽彈窗
    if (previewText != null) {
        AlertDialog(
            onDismissRequest = { previewText = null },
            title = { Text(previewTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (previewText!!.isEmpty()) "無日誌內容" else previewText!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFB5CEA8)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewText = null }) {
                    Text("關閉")
                }
            }
        )
    }
}
