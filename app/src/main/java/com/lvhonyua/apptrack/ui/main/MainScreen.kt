package com.lvhonyua.apptrack.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.lvhonyua.apptrack.Settings
import com.lvhonyua.apptrack.data.LocationRecord
import com.lvhonyua.apptrack.data.LocationRepository
import com.lvhonyua.apptrack.data.SettingsManager
import com.lvhonyua.apptrack.service.LocationTrackerService
import com.lvhonyua.apptrack.theme.AppTrackTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: MainScreenViewModel = viewModel()
  
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val settingsManager = remember { SettingsManager(context) }
  val isConfigured = settingsManager.isConfigured()

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions.entries.all { it.value }
    if (granted) {
      // 权限授予后更新设备名称（蓝牙名称）
      LocationRepository.updateDeviceName(context)
      
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
      TopAppBar(
        title = { Text("AppTrack - Supabase 版") },
        actions = {
          IconButton(onClick = { onItemClick(Settings) }) {
            Icon(Icons.Default.Settings, contentDescription = "设置")
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(16.dp)
        .fillMaxSize()
    ) {
      if (!isConfigured) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("未配置 Supabase", style = MaterialTheme.typography.titleSmall)
            Text("请点击右上角设置图标配置 Project URL 和 Anon Key。", style = MaterialTheme.typography.bodySmall)
          }
        }
      }

      when (state) {
        MainScreenUiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is MainScreenUiState.Success -> {
          val successState = state as MainScreenUiState.Success
          LocationControls(
            isTracking = successState.isTracking,
            onToggle = {
              if (successState.isTracking) {
                context.stopService(Intent(context, LocationTrackerService::class.java))
                LocationRepository.setTracking(false)
              } else {
                val permissions = mutableListOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                  permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                permissionLauncher.launch(permissions.toTypedArray())
              }
            }
          )
          Spacer(modifier = Modifier.height(16.dp))
          LocationList(records = successState.records)
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
    Button(
        onClick = onToggle,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
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
        Text(
            text = if (record.isSynced) "已同步" else "未同步", 
            style = MaterialTheme.typography.labelSmall,
            color = if (record.isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
      }
      Text(
        text = "坐标: ${record.latitude}, ${record.longitude}",
        style = MaterialTheme.typography.bodyMedium
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = "来源: ${record.provider}",
          style = MaterialTheme.typography.bodySmall
        )
        Text(
          text = "设备: ${record.deviceName}",
          style = MaterialTheme.typography.bodySmall
        )
        Text(
          text = "电量: ${if (record.batteryLevel >= 0) "${record.batteryLevel}%" else "未知"}",
          style = MaterialTheme.typography.bodySmall
        )
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = "GPS 卫星: ${record.gpsCount}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.secondary
        )
        Text(
          text = "北斗卫星: ${record.beidouCount}",
          style = MaterialTheme.typography.bodySmall,
          color = if (record.beidouCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  AppTrackTheme {
    LocationList(
      records = listOf(
        LocationRecord(1, "12:00:01", 39.9042, 116.4074, "gps", "device_1", "Pixel 6", 80, 15, 6, 8, true),
        LocationRecord(2, "12:00:05", 39.9043, 116.4075, "gps", "device_1", "Pixel 6", 75, 12, 4, 7, false)
      )
    )
  }
}
