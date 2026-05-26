package com.nono.sensor_log

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.Wearable

@Composable
fun PairingPage() {
    val context = LocalContext.current
    var connectedNodes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshNodes() {
        isRefreshing = true
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            connectedNodes = nodes.map { it.id to it.displayName }
            isRefreshing = false
        }.addOnFailureListener {
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refreshNodes() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "設備資訊與配對", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = "管理設備暱稱與唯一識別碼以利資料溯源", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(24.dp))

        // --- 手機資訊區塊 ---
        Text(text = "📱 手機端 (Handheld) 資訊", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = SensorDataManager.phoneNickname.value,
                    onValueChange = { 
                        SensorDataManager.phoneNickname.value = it
                        SensorDataManager.saveSettingsToPrefs()
                    },
                    label = { Text("手機暱稱 (實驗標籤)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                HardwareInfoRow("型號 (Model)", Build.MODEL)
                HardwareInfoRow("系統名稱", SensorDataManager.phoneName)
                HardwareInfoRow("Android ID", SensorDataManager.phoneId)
                HardwareInfoRow("App 專屬 UUID", SensorDataManager.phoneAppInstanceId)
                
                Text(
                    text = "系統指紋 (Fingerprint):", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        text = Build.FINGERPRINT,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary // 改用 Primary 增加可讀性
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 已配對手錶區塊 ---
        Text(text = "⌚ 手錶端 (Wearable) 資訊", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (SensorDataManager.pairedWatchId.value.isEmpty()) {
                    Text(text = "目前尚未選定配對手錶", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(text = "請從下方清單選擇一個已連線的設備", fontSize = 12.sp, color = Color.Gray)
                } else {
                    OutlinedTextField(
                        value = SensorDataManager.watchNickname.value,
                        onValueChange = { 
                            SensorDataManager.watchNickname.value = it
                            SensorDataManager.saveSettingsToPrefs()
                        },
                        label = { Text("手錶暱稱 (實驗標籤)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    HardwareInfoRow("配對 Node ID", SensorDataManager.pairedWatchId.value)
                    HardwareInfoRow("型號 (Model)", SensorDataManager.pairedWatchModel.value)

                    Text(
                        text = "手錶系統指紋:", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            text = SensorDataManager.pairedWatchFingerprint.value,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary // 改用 Primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 可選連線清單 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "配對中設備", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { refreshNodes() }, enabled = !isRefreshing) {
                Text(text = "🔄", fontSize = 16.sp)
            }
        }

        if (connectedNodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("找不到連線中的手錶\n請確認藍牙與 WearOS 配對狀態", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            connectedNodes.forEach { (id, name) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            SensorDataManager.pairedWatchId.value = id
                            SensorDataManager.pairedWatchModel.value = name
                            SensorDataManager.saveSettingsToPrefs()
                            android.widget.Toast.makeText(context, "已成功配對: $name", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    color = if (id == SensorDataManager.pairedWatchId.value) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surface
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (id == SensorDataManager.pairedWatchId.value) "✅" else "⌚", modifier = Modifier.padding(end = 12.dp), fontSize = 20.sp)
                        Column {
                            Text(text = name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Node ID: $id", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun HardwareInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            text = value, 
            style = MaterialTheme.typography.bodySmall, 
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
