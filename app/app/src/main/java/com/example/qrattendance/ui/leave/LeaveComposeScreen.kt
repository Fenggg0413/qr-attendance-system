package com.example.qrattendance.ui.leave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.theme.Background
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveComposeScreen(onBack: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { LeaveViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  var expanded by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { vm.load() }
  LaunchedEffect(state.submitted) { if (state.submitted) onBack() }
  Column(Modifier.fillMaxSize().background(Background)) {
    LeaveTopBar("新建申请", onBack)
    Column(Modifier.padding(16.dp)) {
      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        val selected = state.sessions.firstOrNull { it.id == state.selectedSessionId }
        OutlinedTextField(
          value = selected?.courseName ?: "选择课程",
          onValueChange = {},
          readOnly = true,
          label = { Text("课程") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
          modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          state.sessions.forEach { session ->
            DropdownMenuItem(text = { Text(session.courseName) }, onClick = {
              vm.selectSession(session.id)
              expanded = false
            })
          }
        }
      }
      OutlinedTextField(
        value = state.reason,
        onValueChange = vm::updateReason,
        label = { Text("原因") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      )
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
      Button(onClick = { scope.launch { vm.submit() } }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("提交")
      }
    }
  }
}
