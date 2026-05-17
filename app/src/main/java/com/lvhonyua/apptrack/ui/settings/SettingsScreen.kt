package com.lvhonyua.apptrack.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lvhonyua.apptrack.data.LocationRepository
import com.lvhonyua.apptrack.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    var supabaseUrl by remember { mutableStateOf(settingsManager.supabaseUrl) }
    var supabaseKey by remember { mutableStateOf(settingsManager.supabaseAnonKey) }
    var tableName by remember { mutableStateOf(settingsManager.tableName) }

    var isValidating by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supabase 配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "请从 Supabase 项目设置中复制 Project URL 和 Anon Key。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (validationMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (validationMessage!!.second) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationMessage!!.first,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedTextField(
                value = supabaseUrl,
                onValueChange = { supabaseUrl = it },
                label = { Text("Supabase URL") },
                placeholder = { Text("https://xxx.supabase.co") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = supabaseKey,
                onValueChange = { supabaseKey = it },
                label = { Text("Anon Key") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tableName,
                onValueChange = { tableName = it },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        isValidating = true
                        validationMessage = null
                        val result = LocationRepository.validateConnection(supabaseUrl, supabaseKey, tableName)
                        isValidating = false
                        if (result.isSuccess) {
                            settingsManager.supabaseUrl = supabaseUrl
                            settingsManager.supabaseAnonKey = supabaseKey
                            settingsManager.tableName = tableName
                            LocationRepository.updateClient()
                            validationMessage = "验证成功并已保存！" to true
                        } else {
                            validationMessage = "验证失败: ${result.exceptionOrNull()?.message}" to false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isValidating && supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("验证并保存")
                }
            }
        }
    }
}
