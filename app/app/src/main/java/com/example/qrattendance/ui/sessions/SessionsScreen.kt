package com.example.qrattendance.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.common.EmptyState
import com.example.qrattendance.ui.common.StatusBadge

@Composable
fun SessionsScreen(state: SessionsUiState, onScope: (String) -> Unit, onFilter: (String) -> Unit, onRefresh: () -> Unit, onLeave: (Long, String) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("会话", style = MaterialTheme.typography.displayLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = state.scope == "active", onClick = { onScope("active") }, label = { Text("进行中") })
      FilterChip(selected = state.scope == "recent", onClick = { onScope("recent") }, label = { Text("最近") })
      AssistChip(onClick = onRefresh, label = { Text("刷新") })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("ALL" to "全部", "OPEN" to "开放", "CLOSED" to "关闭").forEach { (value, label) ->
        FilterChip(selected = state.statusFilter == value, onClick = { onFilter(value) }, label = { Text(label) })
      }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.filteredSessions.isEmpty()) {
        item { EmptyState("暂无会话", "课程会话会显示在这里") }
      } else {
        items(state.filteredSessions) { session ->
          Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.courseName, style = MaterialTheme.typography.titleLarge)
                StatusBadge(if (session.checkedIn) session.recordStatus else session.status)
              }
              Text("${session.startedAt} - ${session.endsAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
              if (session.canRequestLeave) {
                Button(onClick = { onLeave(session.id, session.courseName) }) { Text("申请请假") }
              }
            }
          }
        }
      }
    }
  }
}
