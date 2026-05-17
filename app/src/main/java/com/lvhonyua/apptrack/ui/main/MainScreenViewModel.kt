package com.lvhonyua.apptrack.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lvhonyua.apptrack.data.LocationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel : ViewModel() {
  val uiState: StateFlow<MainScreenUiState> = combine(
    LocationRepository.locationRecords,
    LocationRepository.isTracking
  ) { records, isTracking ->
    MainScreenUiState.Success(records, isTracking)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = MainScreenUiState.Loading
  )
}
