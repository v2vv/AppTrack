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
