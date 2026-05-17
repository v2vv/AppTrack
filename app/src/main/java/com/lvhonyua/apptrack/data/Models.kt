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
    @Transient
    val isSynced: Boolean = false
)
