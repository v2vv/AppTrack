package com.lvhonyua.apptrack.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LocationRecord(
    @Transient
    val id: Long = 0,
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    @SerialName("device_id")
    val deviceId: String = "unknown",
    @SerialName("device_name")
    val deviceName: String = "unknown",
    @SerialName("battery_level")
    val batteryLevel: Int = -1,
    @SerialName("satellite_count")
    val satelliteCount: Int = 0,
    @SerialName("beidou_count")
    val beidouCount: Int = 0,
    @SerialName("gps_count")
    val gpsCount: Int = 0,
    @Transient
    val isSynced: Boolean = false
)

@Serializable
data class InstalledApp(
    @Transient
    val id: Long = 0,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("app_name")
    val appName: String,
    @SerialName("install_time")
    val installTime: String,
    @SerialName("device_id")
    val deviceId: String = "unknown",
    @SerialName("device_name")
    val deviceName: String = "unknown",
    @Transient
    val isSynced: Boolean = false
)

@Serializable
data class AppUsageRecord(
    @Transient
    val id: Long = 0,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("app_name")
    val appName: String,
    @SerialName("usage_time_s")
    val usageTimeSeconds: Long,
    @SerialName("last_time_used")
    val lastTimeUsed: String,
    @SerialName("device_id")
    val deviceId: String = "unknown",
    @SerialName("device_name")
    val deviceName: String = "unknown",
    @Transient
    val isSynced: Boolean = false
)

@Serializable
data class AppSessionRecord(
    @Transient
    val id: Long = 0,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("app_name")
    val appName: String,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("duration_s")
    val durationSeconds: Long,
    @SerialName("device_id")
    val deviceId: String = "unknown",
    @SerialName("device_name")
    val deviceName: String = "unknown",
    @Transient
    val isSynced: Boolean = false
)

@Serializable
data class CallRecord(
    @Transient
    val id: Long = 0,
    val number: String,
    val name: String?,
    val type: String, // 呼入、呼出、未接
    val time: String,
    @SerialName("duration_s")
    val durationSeconds: Long,
    @SerialName("device_id")
    val deviceId: String = "unknown",
    @SerialName("device_name")
    val deviceName: String = "unknown",
    @Transient
    val isSynced: Boolean = false
)

@Serializable
data class SmsRecord(
    @Transient
    val id: Long = 0,
    val address: String,
    val body: String,
    val type: String, // 接收、发送
    val time: String,
    @SerialName("device_id")
    val deviceId: String = "unknown",
    @SerialName("device_name")
    val deviceName: String = "unknown",
    @Transient
    val isSynced: Boolean = false
)
