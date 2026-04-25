package com.example.qrattendance.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.qrattendance.theme.MyApplicationTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  StudentApp(state = state, actions = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudentApp(state: StudentUiState, actions: MainScreenViewModel, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { TopAppBar(title = { Text("学生考勤") }) },
    bottomBar = {
      if (state.token != null) {
        NavigationBar {
          StudentScreen.entries.forEach { screen ->
            if (screen != StudentScreen.Login) {
              NavigationBarItem(
                selected = state.screen == screen,
                onClick = { actions.show(screen) },
                icon = {},
                label = { Text(screen.label()) },
              )
            }
          }
        }
      }
    },
  ) { inner ->
    Column(Modifier.padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
      when (state.screen) {
        StudentScreen.Login -> LoginPanel(state.loading, actions::login)
        StudentScreen.Profile -> ProfilePanel(state)
        StudentScreen.Scan -> ScanPanel(state.loading, actions::submitPayload)
        StudentScreen.Records -> RecordsPanel(state)
        StudentScreen.Leave -> LeavePanel(state.loading, actions::submitLeave)
      }
    }
  }
}

@Composable
private fun LoginPanel(loading: Boolean, onLogin: (String, String) -> Unit) {
  var username by remember { mutableStateOf("student1") }
  var password by remember { mutableStateOf("student123") }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("账号") })
      OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, visualTransformation = PasswordVisualTransformation())
      Button(enabled = !loading, onClick = { onLogin(username, password) }) { Text(if (loading) "登录中" else "登录") }
    }
  }
}

@Composable
private fun ProfilePanel(state: StudentUiState) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(state.user?.displayName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
      Text("账号：${state.user?.username.orEmpty()}")
      Text("角色：${state.user?.role.orEmpty()}")
    }
  }
}

@Composable
private fun ScanPanel(loading: Boolean, onSubmit: (String) -> Unit) {
  var payload by remember { mutableStateOf("") }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(payload, { payload = it }, Modifier.fillMaxWidth().height(112.dp), label = { Text("二维码内容") })
      Button(enabled = !loading, onClick = { onSubmit(payload) }) { Text(if (loading) "提交中" else "扫码签到") }
    }
  }
}

@Composable
private fun RecordsPanel(state: StudentUiState) {
  LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(state.records) { record ->
      Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Text(record.courseName.ifBlank { "会话 #${record.sessionId}" }, style = MaterialTheme.typography.titleMedium)
            Text(record.checkedInAt ?: "-")
          }
          Text(record.status, color = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}

@Composable
private fun LeavePanel(loading: Boolean, onSubmit: (Long, String) -> Unit) {
  var sessionId by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(sessionId, { sessionId = it }, Modifier.fillMaxWidth(), label = { Text("考勤会话 ID") })
      OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth().height(112.dp), label = { Text("申报原因") })
      Button(enabled = !loading, onClick = { sessionId.toLongOrNull()?.let { onSubmit(it, reason) } }) { Text("提交申报") }
    }
  }
}

private fun StudentScreen.label(): String =
  when (this) {
    StudentScreen.Login -> "登录"
    StudentScreen.Profile -> "我的"
    StudentScreen.Scan -> "扫码"
    StudentScreen.Records -> "记录"
    StudentScreen.Leave -> "申报"
  }

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  MyApplicationTheme {
    StudentApp(StudentUiState(screen = StudentScreen.Login), MainScreenViewModel())
  }
}
