package com.lvhonyua.apptrack.ui.main

import com.lvhonyua.apptrack.data.LocationRecord

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState
  data class Success(
    val records: List<LocationRecord>,
    val isTracking: Boolean
  ) : MainScreenUiState
  data class Error(val throwable: Throwable) : MainScreenUiState
}
