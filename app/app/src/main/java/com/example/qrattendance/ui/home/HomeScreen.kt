package com.example.qrattendance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.common.EmptyState
import com.example.qrattendance.ui.common.SectionHeader
import com.example.qrattendance.ui.common.StatusBadge
import java.time.LocalDate

@Composable
fun HomeScreen(state: HomeUiState, onScan: () -> Unit, onLeave: (Long, String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
  LazyColumn(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    item {
      Text("Hi，${state.user?.displayName ?: "同学"} 👋", style = MaterialTheme.typography.displayLarge)
      Text("今天是 ${LocalDate.now()}，${state.activeSessions.count { it.canRequestLeave || !it.checkedIn }} 个待签到", color = MaterialTheme.colorScheme.onSurfaceVariant)
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    item {
      val current = state.activeSessions.firstOrNull()
      Card(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (current == null) {
          EmptyState("暂无进行中会话", "有新签到时会显示在这里", actionText = "刷新", onAction = onRefresh)
        } else {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(current.courseName, style = MaterialTheme.typography.titleLarge)
              StatusBadge(if (current.checkedIn) current.recordStatus else "待签到")
            }
            Text("${current.startedAt} - ${current.endsAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Button(onClick = onScan) { Text("扫码签到") }
              if (current.canRequestLeave) Button(onClick = { onLeave(current.id, current.courseName) }) { Text("申请请假") }
            }
          }
        }
      }
    }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("出勤率", "${attendanceRate(state)}%", Modifier.weight(1f))
        MetricCard("待审请假", state.pendingLeaveCount.toString(), Modifier.weight(1f))
      }
    }
    item { SectionHeader("最近活动") }
    if (state.recentRecords.isEmpty()) {
      item { EmptyState("暂无记录", "完成签到后会生成记录") }
    } else {
      items(state.recentRecords) { record ->
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
          Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
              Text(record.courseName.ifBlank { "会话 #${record.sessionId}" }, style = MaterialTheme.typography.titleMedium)
              Text(record.checkedInAt ?: "-", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(record.status)
          }
        }
      }
    }
  }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
  Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary)
      }
      Text(value, style = MaterialTheme.typography.titleLarge)
      Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun attendanceRate(state: HomeUiState): Int {
  val total = state.recentRecords.size
  if (total == 0) return 0
  val present = state.recentRecords.count { it.status == "PRESENT" || it.status == "LATE" || it.status == "EXCUSED" }
  return present * 100 / total
}
