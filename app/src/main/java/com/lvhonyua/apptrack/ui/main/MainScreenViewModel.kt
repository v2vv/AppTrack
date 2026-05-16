package com.lvhonyua.apptrack.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lvhonyua.apptrack.data.DataRepository
import com.lvhonyua.apptrack.data.LocationRecord
import com.lvhonyua.apptrack.data.LocationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel(
  dataRepository: DataRepository
) : ViewModel() {

  val uiState: StateFlow<MainScreenUiState> =
    combine(
      dataRepository.data,
      LocationRepository.locationRecords,
      LocationRepository.isTracking
    ) { data, records, isTracking ->
      MainScreenUiState.Success(
        data = data,
        locationRecords = records,
        isTracking = isTracking
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)
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
