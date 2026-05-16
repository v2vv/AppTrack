package com.lvhonyua.apptrack.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LocationRecord(
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String
)

object LocationRepository {
    private val _locationRecords = MutableStateFlow<List<LocationRecord>>(emptyList())
    val locationRecords = _locationRecords.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    fun addRecord(record: LocationRecord) {
        _locationRecords.update { listOf(record) + it }
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }
}
