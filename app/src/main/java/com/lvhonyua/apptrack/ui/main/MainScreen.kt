package com.lvhonyua.apptrack.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.lvhonyua.apptrack.data.DefaultDataRepository
import com.lvhonyua.apptrack.data.LocationRecord
import com.lvhonyua.apptrack.service.LocationTrackerService
import com.lvhonyua.apptrack.theme.AppTrackTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: MainScreenViewModel = viewModel {
    MainScreenViewModel(DefaultDataRepository())
  }
  
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions.entries.all { it.value }
    if (granted) {
      val intent = Intent(context, LocationTrackerService::class.java)
      val isTracking = (state as? MainScreenUiState.Success)?.isTracking ?: false
      if (!isTracking) {
        ContextCompat.startForegroundService(context, intent)
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("AppTrack - 后台位置记录", style = MaterialTheme.typography.headlineMedium)
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
          Text("正在加载...")
        }
        is MainScreenUiState.Success -> {
          val successState = state as MainScreenUiState.Success
          LocationControls(
            isTracking = successState.isTracking,
            onToggle = {
              if (successState.isTracking) {
                context.stopService(Intent(context, LocationTrackerService::class.java))
              } else {
                val permissions = mutableListOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissions.toTypedArray())
              }
            }
          )
          Spacer(modifier = Modifier.height(16.dp))
          LocationList(records = successState.locationRecords)
        }
        is MainScreenUiState.Error -> {
          Text("错误: ${(state as MainScreenUiState.Error).throwable.message}")
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
      text = if (isTracking) "记录状态: 开启" else "记录状态: 关闭",
      style = MaterialTheme.typography.bodyLarge
    )
    Button(onClick = onToggle) {
      Text(if (isTracking) "停止记录" else "开始记录")
    }
  }
}

@Composable
fun LocationList(records: List<LocationRecord>) {
  Text("已记录的坐标:", style = MaterialTheme.typography.titleMedium)
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
        Text(text = "时间: ${record.timestamp}", style = MaterialTheme.typography.labelMedium)
        Text(text = "来源: ${record.provider}", style = MaterialTheme.typography.labelSmall)
      }
      Text(
        text = "纬度: ${record.latitude}",
        style = MaterialTheme.typography.bodyMedium
      )
      Text(
        text = "经度: ${record.longitude}",
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
