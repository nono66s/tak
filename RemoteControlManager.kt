package com.nono.sensor_log

import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable

class RemoteControlManager(private val activity: ComponentActivity) {

    companion object {
        private const val TAG = "RemoteControlManager"
    }

    /**
     * 底層發送函式：將字串指令發送至所有已連線的手錶節點
     */
    fun sendCommand(path: String, msg: String) {
        val nodeClient = Wearable.getNodeClient(activity)
        val messageClient = Wearable.getMessageClient(activity)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "發送失敗：目前沒有連線中的手錶節點")
            }
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, msg.toByteArray(Charsets.UTF_8))
                    .addOnSuccessListener {
                        Log.d(TAG, "成功發送至節點 ${node.displayName}: $msg")
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "發送至節點 ${node.displayName} 失敗: ${it.message}")
                    }
            }
        }.addOnFailureListener {
            Log.e(TAG, "無法獲取連線節點清單: ${it.message}")
        }
    }
}
