package com.nono.sensor_log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsPage(viewModel: MainViewModel, onControl: RemoteControlManager) {
    var inputUrl by remember(viewModel.serverUrl.value) { mutableStateOf(viewModel.serverUrl.value) }
    val time = viewModel.lastUpdateTime.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(text = "監控連線設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // --- 會話狀態卡片 ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "會話狀態", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "Session ID", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(text = SensorDataManager.currentSessionId.value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "數據最後更新", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(text = time, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 改進：使用 Primary 顏色增加醒目程度，不再灰暗
                Button(
                    onClick = { SensorDataManager.startNewSession() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("🚀 開啟新 Session (重置計數器)", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        // --- 伺服器設定卡片 ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "伺服器位址 (HTTP POST)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("目標 URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://example.com/api") }
                )

                Button(
                    onClick = { viewModel.saveUrl(inputUrl) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("儲存並使用")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastUpdate = viewModel.lastUpdateTime.value
                    Column(modifier = Modifier.weight(1f)) {
                        Text("手錶數據流狀態", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (lastUpdate != "-") "最後接收時間: $lastUpdate" else "⚪ 待機中 (等待手錶開始)",
                            fontSize = 12.sp, 
                            color = if (lastUpdate != "-") MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自動上傳數據", fontWeight = FontWeight.Bold)
                        Text("接收到訊息時立即送往伺服器", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = viewModel.isAutoUpload.value,
                        onCheckedChange = { viewModel.isAutoUpload.value = it }
                    )
                }
            }
        }

        // --- 歷史紀錄區塊：改用 Card 包裹使其成為一個明顯的區塊 ---
        Text(text = "歷史位址紀錄", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (viewModel.urlHistory.isEmpty()) {
                    Text("尚無紀錄", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                } else {
                    viewModel.urlHistory.asReversed().forEach { historyUrl ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = historyUrl,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (historyUrl == viewModel.serverUrl.value) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                TextButton(onClick = { viewModel.saveUrl(historyUrl) }) {
                                    Text("切換", fontSize = 11.sp)
                                }

                                TextButton(onClick = { viewModel.deleteUrl(historyUrl) }) {
                                    Text("刪除", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
