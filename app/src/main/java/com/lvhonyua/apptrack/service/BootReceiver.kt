package com.lvhonyua.apptrack.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.lvhonyua.apptrack.data.LocationRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received boot action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            // 1. 初始化 Repository（确保单例准备就绪）
            LocationRepository.init(context)
            
            // 2. 自动启动位置记录服务
            val locationIntent = Intent(context, LocationTrackerService::class.java)
            try {
                ContextCompat.startForegroundService(context, locationIntent)
                Log.i("BootReceiver", "AppTrack services started automatically after boot.")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service on boot: ${e.message}")
            }
        }
    }
}
