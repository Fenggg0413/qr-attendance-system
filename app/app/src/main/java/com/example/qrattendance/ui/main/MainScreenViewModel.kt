package com.example.qrattendance.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.LeaveRequest
import com.example.qrattendance.data.QrPayloadParser
import com.example.qrattendance.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel(private val api: AttendanceApiClient = AttendanceApiClient()) : ViewModel() {
  private val _uiState = MutableStateFlow(StudentUiState())
  val uiState: StateFlow<StudentUiState> = _uiState

  fun login(username: String, password: String) {
    launch("登录失败") {
      val response = api.login(username, password)
      _uiState.update { it.copy(token = response.token, user = response.user, screen = StudentScreen.Scan, message = "登录成功") }
      loadRecords()
    }
  }

  fun submitPayload(payload: String) {
    val request = QrPayloadParser.parse(payload)
    if (request == null) {
      _uiState.update { it.copy(error = "二维码内容无效") }
      return
    }
    launch("签到失败") {
      val token = _uiState.value.token ?: error("未登录")
      val response = api.checkIn(token, request)
      _uiState.update { it.copy(message = if (response.duplicate) "已签到，本次不重复记录" else "签到成功", error = null) }
      loadRecords()
    }
  }

  fun loadRecords() {
    val token = _uiState.value.token ?: return
    launch("记录加载失败") {
      _uiState.update { it.copy(records = api.records(token)) }
    }
  }

  fun submitLeave(sessionId: Long, reason: String) {
    val token = _uiState.value.token ?: return
    launch("申报失败") {
      val response = api.submitLeave(token, LeaveRequest(sessionId, reason))
      _uiState.update { it.copy(message = "申报已提交：${response.status}", error = null) }
    }
  }

  fun show(screen: StudentScreen) {
    _uiState.update { it.copy(screen = screen, error = null, message = null) }
  }

  private fun launch(errorPrefix: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      _uiState.update { it.copy(loading = true, error = null) }
      runCatching { block() }
        .onFailure { throwable -> _uiState.update { it.copy(error = "$errorPrefix：${throwable.message}") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}

data class StudentUiState(
  val token: String? = null,
  val user: UserProfile? = null,
  val screen: StudentScreen = StudentScreen.Login,
  val records: List<AttendanceRecord> = emptyList(),
  val loading: Boolean = false,
  val message: String? = null,
  val error: String? = null,
)

enum class StudentScreen { Login, Profile, Scan, Records, Leave }
