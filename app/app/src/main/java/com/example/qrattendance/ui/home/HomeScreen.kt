package com.example.qrattendance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.ui.common.EmptyState
import com.example.qrattendance.ui.common.SectionHeader
import com.example.qrattendance.ui.common.StatusBadge
import com.example.qrattendance.theme.LocalStatusColors
import java.time.LocalDate

@Composable
fun HomeScreen(state: HomeUiState, onScan: () -> Unit, onLeave: (Long, String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
  LazyColumn(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("Hi，${state.user?.displayName ?: "同学"} 👋", style = MaterialTheme.typography.displayLarge)
          Text(compactDate(LocalDate.now()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
      }
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
    item {
      val current = state.activeSessions.firstOrNull()
      Card(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)))) {
          if (current == null) {
            EmptyState("暂无进行中会话", "有新签到时会显示在这里", actionText = "刷新", onAction = onRefresh)
          } else {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                StatusBadge(if (current.checkedIn) current.recordStatus else "待签到")
              }
              Text(current.courseName, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
              Text("教师 ${current.method.ifBlank { "-" }}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f))
              Text(
                "${current.startedAt.ifBlank { "--:--" }} - ${current.endsAt.ifBlank { "--:--" }}",
                color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onScan) { Text("扫码签到") }
                if (current.canRequestLeave) Button(onClick = { onLeave(current.id, current.courseName) }) { Text("申请请假") }
              }
            }
          }
        }
      }
    }
    item {
      val rate = attendanceRate(state)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("出勤率", "$rate%", Icons.Outlined.TrendingUp, Modifier.weight(1f), progress = rate / 100f)
        MetricCard("待审请假", state.pendingLeaveCount.toString(), Icons.Outlined.HourglassEmpty, Modifier.weight(1f), subtitle = "审批中")
      }
    }
    item { SectionHeader("最近活动") }
    if (state.recentRecords.isEmpty()) {
      item { EmptyState("暂无记录", "完成签到后会生成记录") }
    } else {
      items(state.recentRecords, key = { it.id }) { record ->
        RecentRecordItem(record)
      }
    }
  }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, progress: Float? = null, subtitle: String? = null) {
  Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(
        Modifier
          .size(40.dp)
          .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
      }
      Text(value, style = MaterialTheme.typography.displaySmall)
      Text(subtitle ?: title, color = MaterialTheme.colorScheme.onSurfaceVariant)
      progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
    }
  }
}

@Composable
private fun RecentRecordItem(record: AttendanceRecord) {
  Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Row(Modifier.fillMaxWidth().height(76.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(8.dp).fillMaxHeight().background(statusColor(record.status), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(record.courseName.ifBlank { "会话 #${record.sessionId}" }, style = MaterialTheme.typography.titleMedium)
        Text(record.checkedInAt ?: "-", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      StatusBadge(record.status)
      Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun statusColor(status: String): Color {
  val colors = LocalStatusColors.current
  return when (status.uppercase()) {
    "PRESENT" -> colors.present
    "LATE" -> colors.late
    "ABSENT" -> colors.absent
    "EXCUSED" -> colors.excused
    else -> colors.pending
  }
}

private fun attendanceRate(state: HomeUiState): Int {
  val total = state.recentRecords.size
  if (total == 0) return 0
  val present = state.recentRecords.count { it.status == "PRESENT" || it.status == "LATE" || it.status == "EXCUSED" }
  return present * 100 / total
}

private fun compactDate(date: LocalDate): String {
  val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]
  return "$week · ${date.monthValue} 月 ${date.dayOfMonth} 日"
}
