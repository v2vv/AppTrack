package com.lvhonyua.apptrack.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lvhonyua.apptrack.data.LocationRecord
import com.lvhonyua.apptrack.data.LocationRepository
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LocationTrackerService : Service() {

    private lateinit var locationManager: LocationManager
    private val channelId = "location_tracker_channel_v2"
    private val notificationId = 1
    
    private var currentSatelliteCount = 0
    private var currentBeidouCount = 0
    private var currentGpsCount = 0

    // 用于缓存最新的位置信息
    private var lastGpsLocation: Location? = null
    private var lastNetworkLocation: Location? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var beidou = 0
            var gps = 0
            for (i in 0 until status.satelliteCount) {
                when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_BEIDOU -> beidou++
                    GnssStatus.CONSTELLATION_GPS -> gps++
                }
            }
            currentBeidouCount = beidou
            currentGpsCount = gps
            currentSatelliteCount = status.satelliteCount
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.provider == LocationManager.GPS_PROVIDER) {
                lastGpsLocation = location
            } else {
                lastNetworkLocation = location
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, createNotification())
        
        try {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, null)
            // 保持高频监听（实际上系统会有波动）
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener)
            }

            // 核心改进：启动一个每 5 秒执行一次的强制节拍器
            startRecordingTimer()
            
            LocationRepository.setTracking(true)
        } catch (e: Exception) {
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun startRecordingTimer() {
        recordingJob?.cancel()
        recordingJob = serviceScope.launch {
            while (isActive) {
                delay(5000L) // 强制 5 秒节拍
                saveCurrentLocation()
            }
        }
    }

    private fun saveCurrentLocation() {
        // 策略：优先使用 GPS 位置，如果没有则回退到网络位置
        val bestLocation = lastGpsLocation ?: lastNetworkLocation ?: return
        
        // 检查位置是否过时（超过 30 秒认为无效，防止由于没信号导致一直记录旧点）
        if (System.currentTimeMillis() - bestLocation.time > 30000) return

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else -1

        val rawProvider = bestLocation.provider ?: "Unknown"
        val displayProvider = when {
            rawProvider.lowercase() == "gps" -> if (currentBeidouCount > 0) "北斗" else "GPS"
            rawProvider.lowercase() == "network" -> "网络"
            else -> rawProvider
        }

        val record = LocationRecord(
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            latitude = bestLocation.latitude,
            longitude = bestLocation.longitude,
            provider = displayProvider,
            batteryLevel = batteryPct,
            satelliteCount = currentSatelliteCount,
            beidouCount = currentBeidouCount,
            gpsCount = currentGpsCount
        )
        
        LocationRepository.addRecord(record)
        Log.d("LocationTrackerService", "Consistent 5s tick: saved location from $displayProvider")
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        serviceScope.cancel()
        locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
        locationManager.removeUpdates(locationListener)
        LocationRepository.setTracking(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "System Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("System Service")
            .setContentText("Running in background")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .build()
    }
}
