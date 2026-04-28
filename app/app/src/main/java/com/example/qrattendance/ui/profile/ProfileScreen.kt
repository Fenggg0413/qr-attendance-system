package com.example.qrattendance.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.components.Avatar
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.PrimaryLight
import com.example.qrattendance.ui.theme.StatusOrange
import com.example.qrattendance.ui.theme.StatusRed
import com.example.qrattendance.ui.theme.Surface as AppSurface
import com.example.qrattendance.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onOpenLeave: () -> Unit, onLogout: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { ProfileViewModel(container.api, container.sessionStore) }
  val state by vm.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) { vm.load() }
  val user = state.user
  Column(modifier = Modifier.fillMaxSize().background(Background)) {
    Column(
      modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1565C0), Primary, PrimaryLight))).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).padding(20.dp),
    ) {
      Avatar(user?.displayName ?: container.sessionStore.current()?.displayName ?: "学生", size = 68.dp)
      Text(user?.displayName ?: user?.name ?: "学生", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
      Text("学号：${user?.studentNo.orEmpty().ifBlank { "-" }} · ${user?.grade.orEmpty()} ${user?.department.orEmpty()}", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
      Row(modifier = Modifier.padding(top = 12.dp)) {
        ProfileTag(user?.grade.orEmpty().ifBlank { "未设置年级" })
        ProfileTag(user?.department.orEmpty().ifBlank { "未设置院系" })
        ProfileTag("学生")
      }
    }
    ProfileSection("考勤功能") {
      ProfileItem("缺勤申诉", Icons.Rounded.EventBusy, StatusRed, onOpenLeave)
      ProfileItem("请假申请", Icons.Rounded.NoteAdd, StatusOrange, onOpenLeave)
    }
    ProfileSection("账户") {
      ProfileItem("修改资料", Icons.Rounded.Edit, Primary, vm::openEditProfile)
      ProfileItem("修改密码", Icons.Rounded.Lock, Primary, vm::openEditPassword)
      ProfileItem("退出登录", Icons.AutoMirrored.Rounded.Logout, StatusRed, onLogout)
      state.profileMessage?.takeIf { !state.editingProfile }?.let { ProfileMessage(it) }
      state.passwordMessage?.takeIf { !state.editingPassword }?.let { ProfileMessage(it) }
    }
  }
  if (state.editingProfile) {
    EditProfileDialog(
      state = state,
      onNameChange = vm::updateDisplayNameDraft,
      onDismiss = vm::closeEditProfile,
      onSubmit = { scope.launch { vm.submitProfile() } },
    )
  }
  if (state.editingPassword) {
    EditPasswordDialog(
      state = state,
      onCurrentChange = vm::updateCurrentPassword,
      onNewChange = vm::updateNewPassword,
      onConfirmChange = vm::updateConfirmPassword,
      onDismiss = vm::closeEditPassword,
      onSubmit = { scope.launch { vm.submitPassword() } },
    )
  }
}

@Composable
private fun ProfileTag(text: String) {
  Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(AppSurface, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, Border), RoundedCornerShape(14.dp)).padding(vertical = 8.dp)) {
    Text(title, color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    content()
  }
}

@Composable
private fun ProfileItem(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
  }
}

@Composable
private fun ProfileMessage(message: String) {
  Text(
    message,
    color = if (message.contains("已")) Primary else MaterialTheme.colorScheme.error,
    style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
  )
}

@Composable
private fun EditProfileDialog(
  state: ProfileUiState,
  onNameChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onSubmit: () -> Unit,
) {
  Dialog(onDismissRequest = { if (!state.profileSaving) onDismiss() }) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = AppSurface,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        Text("修改资料", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
          value = state.displayNameDraft,
          onValueChange = onNameChange,
          label = { Text("姓名") },
          singleLine = true,
          enabled = !state.profileSaving,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            focusedLabelColor = Primary,
          ),
          shape = RoundedCornerShape(12.dp),
        )
        state.profileMessage?.let { ProfileMessage(it) }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss, enabled = !state.profileSaving) { Text("取消") }
          Spacer(Modifier.size(8.dp))
          Button(
            onClick = onSubmit,
            enabled = !state.profileSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
          ) {
            Text(if (state.profileSaving) "保存中" else "保存")
          }
        }
      }
    }
  }
}

@Composable
private fun EditPasswordDialog(
  state: ProfileUiState,
  onCurrentChange: (String) -> Unit,
  onNewChange: (String) -> Unit,
  onConfirmChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onSubmit: () -> Unit,
) {
  Dialog(onDismissRequest = { if (!state.passwordSaving) onDismiss() }) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = AppSurface,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        Text("修改密码", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
          value = state.currentPassword,
          onValueChange = onCurrentChange,
          label = { Text("当前密码") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          enabled = !state.passwordSaving,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            focusedLabelColor = Primary,
          ),
          shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = state.newPassword,
          onValueChange = onNewChange,
          label = { Text("新密码") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          enabled = !state.passwordSaving,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            focusedLabelColor = Primary,
          ),
          shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = state.confirmPassword,
          onValueChange = onConfirmChange,
          label = { Text("确认新密码") },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          enabled = !state.passwordSaving,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            focusedLabelColor = Primary,
          ),
          shape = RoundedCornerShape(12.dp),
        )
        state.passwordMessage?.let { ProfileMessage(it) }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss, enabled = !state.passwordSaving) { Text("取消") }
          Spacer(Modifier.size(8.dp))
          Button(
            onClick = onSubmit,
            enabled = !state.passwordSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
          ) {
            Text(if (state.passwordSaving) "保存中" else "保存")
          }
        }
      }
    }
  }
}