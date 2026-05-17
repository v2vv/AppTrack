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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationTrackerService : Service() {

    private lateinit var locationManager: LocationManager
    private val channelId = "location_tracker_channel"
    private val notificationId = 1
    
    private var currentSatelliteCount = 0
    private var currentBeidouCount = 0
    private var currentGpsCount = 0

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var beidouCount = 0
            var gpsCount = 0
            var usedInFixCount = 0
            val totalCount = status.satelliteCount
            for (i in 0 until totalCount) {
                when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_BEIDOU -> beidouCount++
                    GnssStatus.CONSTELLATION_GPS -> gpsCount++
                }
                if (status.usedInFix(i)) {
                    usedInFixCount++
                }
            }
            currentSatelliteCount = totalCount
            currentBeidouCount = beidouCount
            currentGpsCount = gpsCount
            Log.d("LocationTrackerService", "GNSS Status: Total=$totalCount, Beidou=$beidouCount, GPS=$gpsCount, UsedInFix=$usedInFixCount")
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                registerReceiver(null, ifilter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else -1

            // 严格区分定位来源
            val rawProvider = location.provider ?: "Unknown"
            val displayProvider = when {
                rawProvider.lowercase() == "gps" -> {
                    // 如果有北斗卫星参与，显示为“北斗”，否则显示为“GPS”
                    if (currentBeidouCount > 0) "北斗" else "GPS"
                }
                rawProvider.lowercase() == "network" -> "网络"
                rawProvider.lowercase() == "fused" -> "融合定位"
                else -> rawProvider
            }

            val record = LocationRecord(
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                latitude = location.latitude,
                longitude = location.longitude,
                provider = displayProvider,
                batteryLevel = batteryPct,
                satelliteCount = currentSatelliteCount,
                beidouCount = currentBeidouCount,
                gpsCount = currentGpsCount
            )
            LocationRepository.addRecord(record)
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
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                1f,
                locationListener
            )
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    1f,
                    locationListener
                )
            }
            LocationRepository.setTracking(true)
        } catch (e: Exception) {
            stopSelf()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
        locationManager.removeUpdates(locationListener)
        LocationRepository.setTracking(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AppTrack")
            .setContentText("正在记录 GPS 坐标...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}
