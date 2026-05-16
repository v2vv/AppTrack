package com.lvhonyua.apptrack.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.lvhonyua.apptrack.data.DefaultDataRepository
import com.lvhonyua.apptrack.theme.AppTrackTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: MainScreenViewModel = viewModel {
    MainScreenViewModel(DefaultDataRepository(), context.applicationContext)
  }
  
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions.values.all { it }
    if (granted) {
      viewModel.toggleTracking()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("AppTrack - GPS/Beidou", style = MaterialTheme.typography.headlineMedium)
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(16.dp)
        .fillMaxSize()
    ) {
      when (state) {
        MainScreenUiState.Loading -> {
          Text("Loading...")
        }
        is MainScreenUiState.Success -> {
          val successState = state as MainScreenUiState.Success
          LocationControls(
            isTracking = successState.isTracking,
            onToggle = {
              permissionLauncher.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
              )
            }
          )
          Spacer(modifier = Modifier.height(16.dp))
          LocationList(records = successState.locationRecords)
        }
        is MainScreenUiState.Error -> {
          Text("Error: ${(state as MainScreenUiState.Error).throwable.message}")
        }
      }
    }
  }
}

@Composable
fun LocationControls(
  isTracking: Boolean,
  onToggle: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = if (isTracking) "Tracking Status: ON" else "Tracking Status: OFF",
      style = MaterialTheme.typography.bodyLarge
    )
    Button(onClick = onToggle) {
      Text(if (isTracking) "Stop Recording" else "Start Recording")
    }
  }
}

@Composable
fun LocationList(records: List<LocationRecord>) {
  Text("Recorded Coordinates:", style = MaterialTheme.typography.titleMedium)
  Spacer(modifier = Modifier.height(8.dp))
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(records) { record ->
      LocationItem(record)
    }
  }
}

@Composable
fun LocationItem(record: LocationRecord) {
  Card(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Time: ${record.timestamp}", style = MaterialTheme.typography.labelMedium)
        Text(text = "Provider: ${record.provider}", style = MaterialTheme.typography.labelSmall)
      }
      Text(
        text = "Lat: ${record.latitude}",
        style = MaterialTheme.typography.bodyMedium
      )
      Text(
        text = "Lng: ${record.longitude}",
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  AppTrackTheme {
    LocationList(
      records = listOf(
        LocationRecord("12:00:01", 39.9042, 116.4074, "gps"),
        LocationRecord("12:00:05", 39.9043, 116.4075, "gps")
      )
    )
  }
}
