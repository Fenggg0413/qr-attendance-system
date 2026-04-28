package com.example.qrattendance.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrattendance.theme.LocalStatusColors
import com.example.qrattendance.ui.common.EmptyState
import com.example.qrattendance.ui.common.StatusBadge

@Composable
fun RecordsScreen(state: RecordsUiState, onFilter: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("记录", style = MaterialTheme.typography.displayLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("ALL" to "全部", "PRESENT" to "已签到", "LATE" to "迟到", "ABSENT" to "缺勤", "EXCUSED" to "请假").forEach { (value, label) ->
        FilterChip(selected = state.filter == value, onClick = { onFilter(value) }, label = { Text(label) })
      }
      AssistChip(onClick = onRefresh, label = { Text("刷新") })
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.filteredRecords.isEmpty()) {
        item { EmptyState("暂无记录", "完成签到或请假后会显示在这里") }
      } else {
        items(state.filteredRecords) { record ->
          Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Box(Modifier.width(8.dp).fillMaxHeight().background(statusColor(record.status), RoundedCornerShape(8.dp)))
              Column(Modifier.weight(1f)) {
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
}

@Composable
private fun statusColor(status: String) =
  when (status) {
    "PRESENT" -> LocalStatusColors.current.present
    "LATE" -> LocalStatusColors.current.late
    "ABSENT" -> LocalStatusColors.current.absent
    "EXCUSED" -> LocalStatusColors.current.excused
    else -> LocalStatusColors.current.pending
  }
