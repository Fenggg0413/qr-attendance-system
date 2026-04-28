package com.example.qrattendance.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.components.Avatar
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.PrimaryLight
import com.example.qrattendance.ui.theme.StatusOrange
import com.example.qrattendance.ui.theme.StatusRed
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextSecondary

@Composable
fun ProfileScreen(onOpenLeave: () -> Unit, onLogout: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { ProfileViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  LaunchedEffect(Unit) { vm.load() }
  val user = state.user
  Column(modifier = Modifier.fillMaxSize().background(Background)) {
    Column(
      modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1565C0), Primary, PrimaryLight))).padding(20.dp),
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
      ProfileItem("修改资料", Icons.Rounded.Edit, Primary) {}
      ProfileItem("修改密码", Icons.Rounded.Lock, Primary) {}
      ProfileItem("退出登录", Icons.Rounded.Logout, StatusRed, onLogout)
    }
  }
}

@Composable
private fun ProfileTag(text: String) {
  Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, Border), RoundedCornerShape(14.dp)).padding(vertical = 8.dp)) {
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
