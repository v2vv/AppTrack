package com.lvhonyua.apptrack.service

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.lvhonyua.apptrack.data.LocationRepository
import com.lvhonyua.apptrack.data.NotificationRecord
import java.text.SimpleDateFormat
import java.util.*

class NotificationTrackerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            // 排除应用自身产生的通知
            if (packageName == this.packageName) return

            val extras = sbn.notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString()
            
            val pm = packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val record = NotificationRecord(
                packageName = packageName,
                appName = appName,
                title = title,
                content = text,
                time = sdf.format(Date(sbn.postTime)),
                deviceId = LocationRepository.getDeviceId(),
                deviceName = LocationRepository.getDeviceName()
            )

            LocationRepository.saveNotification(record)
            Log.d("NotificationTracker", "Notification recorded from $appName: $title")
        } catch (e: Exception) {
            Log.e("NotificationTracker", "Error processing notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 可选：处理通知被移除的逻辑
    }
}
