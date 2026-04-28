package com.example.qrattendance.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LeaveComposeScreen(state: LeaveUiState, onReasonChange: (String) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text("申请请假", style = MaterialTheme.typography.displayLarge)
    Text(state.courseName.ifBlank { "会话 #${state.sessionId}" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(state.reason, onReasonChange, Modifier.fillMaxWidth(), label = { Text("请假原因") }, minLines = 4)
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Button(enabled = !state.loading, onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "提交中" else "提交") }
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
  }
}
