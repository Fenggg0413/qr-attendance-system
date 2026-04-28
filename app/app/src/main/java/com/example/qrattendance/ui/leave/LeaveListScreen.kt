package com.example.qrattendance.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.common.EmptyState
import com.example.qrattendance.ui.common.StatusBadge

@Composable
fun LeaveListScreen(state: LeaveUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("请假", style = MaterialTheme.typography.displayLarge)
      AssistChip(onClick = onRefresh, label = { Text("刷新") })
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.requests.isEmpty()) {
        item { EmptyState("暂无请假记录", "从会话中发起请假后会显示在这里") }
      } else {
        items(state.requests) { request ->
          Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
              Column(Modifier.weight(1f)) {
                Text(request.courseName, style = MaterialTheme.typography.titleMedium)
                Text(request.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              StatusBadge(request.status)
            }
          }
        }
      }
    }
  }
}
