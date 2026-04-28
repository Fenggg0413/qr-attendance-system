package com.example.qrattendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
  state: ProfileUiState,
  onDisplayNameChange: (String) -> Unit,
  onCurrentPasswordChange: (String) -> Unit,
  onNewPasswordChange: (String) -> Unit,
  onSaveProfile: () -> Unit,
  onChangePassword: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Text((state.user?.displayName ?: "同").take(1), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
      }
      Column {
        Text(state.user?.displayName ?: "同学", style = MaterialTheme.typography.displayLarge)
        Text(state.user?.username ?: "-", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("编辑资料", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(state.displayName, onDisplayNameChange, Modifier.fillMaxWidth(), label = { Text("显示名称") })
        Button(enabled = !state.loading, onClick = onSaveProfile, modifier = Modifier.fillMaxWidth()) { Text("保存资料") }
      }
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("修改密码", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(state.currentPassword, onCurrentPasswordChange, Modifier.fillMaxWidth(), label = { Text("当前密码") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(state.newPassword, onNewPasswordChange, Modifier.fillMaxWidth(), label = { Text("新密码") }, visualTransformation = PasswordVisualTransformation())
        Button(enabled = !state.loading, onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) { Text("更新密码") }
      }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
  }
}
