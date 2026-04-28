package com.example.qrattendance.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(state: LoginUiState, onUsernameChange: (String) -> Unit, onPasswordChange: (String) -> Unit, onLogin: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("学生考勤", style = MaterialTheme.typography.displayLarge)
    Text("扫码签到、请假申报与出勤记录", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
    Card(
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(state.username, onUsernameChange, Modifier.fillMaxWidth(), label = { Text("账号") }, singleLine = true)
        OutlinedTextField(
          state.password,
          onPasswordChange,
          Modifier.fillMaxWidth(),
          label = { Text("密码") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !state.loading, onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "登录中" else "登录") }
      }
    }
  }
}
