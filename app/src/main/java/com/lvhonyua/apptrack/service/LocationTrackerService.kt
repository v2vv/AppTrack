package com.lvhonyua.apptrack.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
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

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val record = LocationRecord(
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                latitude = location.latitude,
                longitude = location.longitude,
                provider = location.provider ?: "Unknown"
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
