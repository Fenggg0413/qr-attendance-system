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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
  LazyColumn(
    modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Hi，${state.user?.displayName ?: "同学"} 👋", style = MaterialTheme.typography.displayLarge)
          Text(compactDate(LocalDate.now()), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
      }
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
    item { HeroCard(state.activeSessions.firstOrNull(), onScan, onLeave, onRefresh) }
    item {
      val rate = attendanceRate(state)
      val absent = absentRate(state)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("出勤率", "$rate%", Icons.AutoMirrored.Outlined.TrendingUp, Modifier.weight(1f), progress = rate / 100f, accent = MaterialTheme.colorScheme.primary)
        MetricCard("缺勤率", "$absent%", Icons.Outlined.EventBusy, Modifier.weight(1f), progress = absent / 100f, accent = LocalStatusColors.current.absent)
      }
    }
    item { SectionHeader("最近活动") }
    if (state.recentRecords.isEmpty()) {
      item { EmptyState("暂无记录", "完成签到后会生成记录") }
    } else {
      items(state.recentRecords, key = { it.id }) { record -> RecentRecordItem(record) }
    }
  }
}

@Composable
private fun HeroCard(current: com.example.qrattendance.data.SessionSummary?, onScan: () -> Unit, onLeave: (Long, String) -> Unit, onRefresh: () -> Unit) {
  val gradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(Modifier.fillMaxWidth().background(gradient)) {
      if (current == null) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("暂无进行中会话", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
          }
          Text("有新课程开放签到时会显示在这里", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
          Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
          ) { Text("刷新") }
        }
      } else {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            StatusBadge(if (current.checkedIn) current.recordStatus else "待签到")
          }
          Text(current.courseName, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
          Text(
            "${current.startedAt.ifBlank { "--:--" }} - ${current.endsAt.ifBlank { "--:--" }}",
            color = MaterialTheme.colorScheme.onPrimary,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
              onClick = onScan,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
            ) { Text("扫码签到") }
            if (current.canRequestLeave) {
              Button(
                onClick = { onLeave(current.id, current.courseName) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onPrimary),
              ) { Text("申请请假") }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricCard(
  title: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  progress: Float,
  accent: Color,
) {
  Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
      }
      Text(value, style = MaterialTheme.typography.displaySmall)
      Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
      LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = accent, modifier = Modifier.fillMaxWidth())
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

private fun absentRate(state: HomeUiState): Int {
  val total = state.recentRecords.size
  if (total == 0) return 0
  val absent = state.recentRecords.count { it.status == "ABSENT" }
  return absent * 100 / total
}

private fun compactDate(date: LocalDate): String {
  val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]
  return "$week · ${date.monthValue} 月 ${date.dayOfMonth} 日"
}
