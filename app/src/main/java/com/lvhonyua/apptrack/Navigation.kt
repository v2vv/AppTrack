package com.lvhonyua.apptrack

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lvhonyua.apptrack.data.SettingsManager
import com.lvhonyua.apptrack.ui.auth.LoginScreen
import com.lvhonyua.apptrack.ui.main.MainScreen
import com.lvhonyua.apptrack.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val settingsManager = remember { SettingsManager(context) }
  
  // 核心验证状态：如果没有开启密码，则默认为已验证
  var isAuthenticated by remember { mutableStateOf(!settingsManager.isPasswordEnabled) }

  if (!isAuthenticated && settingsManager.isPasswordEnabled) {
      // 显示登录验证页面
      LoginScreen(
          correctPassword = settingsManager.appPassword,
          onAuthenticated = { isAuthenticated = true }
      )
  } else {
      // 原有正常的导航逻辑
      val backStack = rememberNavBackStack(Main)

      NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
          entryProvider {
            entry<Main> {
              MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
            }
            entry<Settings> {
              SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
          },
      )
  }
}
