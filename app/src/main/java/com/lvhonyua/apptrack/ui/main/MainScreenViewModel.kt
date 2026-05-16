package com.lvhonyua.apptrack.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lvhonyua.apptrack.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocationRecord(
  val timestamp: String,
  val latitude: Double,
  val longitude: Double,
  val provider: String
)

class MainScreenViewModel(
  private val dataRepository: DataRepository,
  private val context: Context
) : ViewModel() {

  private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

  private val _isTracking = MutableStateFlow(false)
  val isTracking = _isTracking.asStateFlow()

  private val _locationRecords = MutableStateFlow<List<LocationRecord>>(emptyList())
  val locationRecords = _locationRecords.asStateFlow()

  val uiState: StateFlow<MainScreenUiState> =
    combine(dataRepository.data, _locationRecords, _isTracking) { data, records, isTracking ->
      MainScreenUiState.Success(data = data, locationRecords = records, isTracking = isTracking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  private val locationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) {
      val record = LocationRecord(
        timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
        latitude = location.latitude,
        longitude = location.longitude,
        provider = location.provider ?: "Unknown"
      )
      _locationRecords.update { listOf(record) + it }
    }
  }

  @SuppressLint("MissingPermission")
  fun toggleTracking() {
    if (_isTracking.value) {
      locationManager.removeUpdates(locationListener)
      _isTracking.value = false
    } else {
      try {
        locationManager.requestLocationUpdates(
          LocationManager.GPS_PROVIDER,
          5000L, // 5 seconds
          1f,    // 1 meter
          locationListener
        )
        // Also try Network Provider as fallback for indoor/city use
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
          locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            5000L,
            1f,
            locationListener
          )
        }
        _isTracking.value = true
      } catch (e: Exception) {
        _isTracking.value = false
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    locationManager.removeUpdates(locationListener)
  }
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(
    val data: List<String>,
    val locationRecords: List<LocationRecord> = emptyList(),
    val isTracking: Boolean = false
  ) : MainScreenUiState
}
