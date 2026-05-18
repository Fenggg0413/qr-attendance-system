package com.example.qrattendance.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.TextPrimary
import com.example.qrattendance.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val PrimaryApp = Primary
private val BrandText = TextPrimary
private val GradientStart = Color(0xFFEAF2F8)
private val GradientEnd = Background
private val InputBorder = Border

// 登录页：账号密码输入 + 服务端地址配置入口；登录成功通过 onLoggedIn 回调通知上层切换路由。
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { LoginViewModel(container.api, container.sessionStore, container.endpointStore) }
  val state by vm.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  LaunchedEffect(state.loggedIn) {
    if (state.loggedIn) onLoggedIn()
  }

  val backgroundBrush = Brush.linearGradient(
    colors = listOf(GradientStart, GradientEnd)
  )

  Box(modifier = Modifier.fillMaxSize().background(backgroundBrush).padding(WindowInsets.statusBars.asPaddingValues())) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
      ) {
        Column {
          // Top accent line
          Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(PrimaryApp))
          
          Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
          ) {
            // Brand Header
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .background(PrimaryApp, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.AutoMirrored.Rounded.FactCheck,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(24.dp)
                )
              }
              Text(
                "校园云考勤系统",
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 26.sp,
                  color = BrandText
                )
              )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Error Banner
            state.error?.let {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(Color(0xFFFEE2E2), RoundedCornerShape(8.dp))
                  .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(
                  it,
                  color = Color(0xFFB91C1C),
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
              }
            }

            // Input Fields
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
              OutlinedTextField(
                value = state.username,
                onValueChange = vm::updateUsername,
                label = { Text("账号") },
                placeholder = { Text("请输入学号或工号") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFF93A39F)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = PrimaryApp,
                  unfocusedBorderColor = InputBorder,
                  focusedLabelColor = PrimaryApp,
                  cursorColor = PrimaryApp
                )
              )
              
              OutlinedTextField(
                value = state.password,
                onValueChange = vm::updatePassword,
                label = { Text("密码") },
                placeholder = { Text("请输入密码") },
                leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = Color(0xFF93A39F)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = PrimaryApp,
                  unfocusedBorderColor = InputBorder,
                  focusedLabelColor = PrimaryApp,
                  cursorColor = PrimaryApp
                )
              )
            }

            // Options
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              TextButton(onClick = { /* TODO: Forgot password */ }) {
                Text("忘记密码？", color = PrimaryApp, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
              }
            }

            // Login Button
            Button(
              onClick = { scope.launch { vm.login() } },
              enabled = !state.loading,
              modifier = Modifier.fillMaxWidth().height(44.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryApp)
            ) {
              if (state.loading) {
                Text("登录中...")
              } else {
                Text("登录系统")
              }
            }
          }
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
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryApp,
            focusedLabelColor = PrimaryApp,
            cursorColor = PrimaryApp
          )
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
      Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = PrimaryApp)) { Text("保存") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消", color = PrimaryApp) }
    },
  )
}
