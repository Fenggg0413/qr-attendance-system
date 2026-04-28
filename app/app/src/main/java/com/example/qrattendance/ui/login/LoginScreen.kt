package com.example.qrattendance.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { LoginViewModel(container.api, container.sessionStore, container.endpointStore) }
  val state by vm.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  LaunchedEffect(state.loggedIn) {
    if (state.loggedIn) onLoggedIn()
  }
  Box(modifier = Modifier.fillMaxSize().background(Background).padding(WindowInsets.statusBars.asPaddingValues())) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text("学生考勤助手", style = MaterialTheme.typography.titleLarge)
      Text("使用学生账号登录", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
      Column(
        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(value = state.username, onValueChange = vm::updateUsername, label = { Text("账号") }, placeholder = { Text("请输入学号账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.password, onValueChange = vm::updatePassword, label = { Text("密码") }, placeholder = { Text("默认密码 123456") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { scope.launch { vm.login() } }, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
          Icon(Icons.Rounded.Login, contentDescription = null)
          Spacer(Modifier.padding(4.dp))
          Text(if (state.loading) "登录中" else "登录")
        }
      }
    }
    IconButton(
      onClick = vm::openEndpointDialog,
      modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 8.dp),
    ) {
      Icon(
        Icons.Rounded.Settings,
        contentDescription = "服务器配置",
        tint = TextSecondary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
  if (state.showEndpointDialog) {
    EndpointConfigDialog(
      url = state.endpointDraft,
      onUrlChange = vm::updateEndpointDraft,
      onDismiss = vm::closeEndpointDialog,
      onSave = vm::saveEndpoint,
    )
  }
}

@Composable
private fun EndpointConfigDialog(
  url: String,
  onUrlChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onSave: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("服务器配置") },
    text = {
      Column {
        OutlinedTextField(
          value = url,
          onValueChange = onUrlChange,
          label = { Text("服务器地址") },
          placeholder = { Text("http://192.168.1.100:8080") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          "配置考勤系统服务器的局域网地址",
          color = TextSecondary,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    },
    confirmButton = {
      Button(onClick = onSave) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}