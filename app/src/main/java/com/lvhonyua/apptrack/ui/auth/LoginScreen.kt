package com.lvhonyua.apptrack.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    correctPassword: String,
    onAuthenticated: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp).fillMaxWidth()
        ) {
            Text("验证身份", style = MaterialTheme.typography.headlineMedium)
            Text("应用已开启保护，请输入密码进入。", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { 
                    passwordInput = it
                    isError = false
                },
                label = { Text("请输入密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = isError,
                modifier = Modifier.fillMaxWidth()
            )

            if (isError) {
                Text("密码错误，请重试", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = {
                    if (passwordInput == correctPassword) {
                        onAuthenticated()
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("进入应用")
            }
        }
    }
}
