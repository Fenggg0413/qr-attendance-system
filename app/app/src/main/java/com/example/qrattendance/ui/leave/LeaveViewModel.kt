package com.example.qrattendance.ui.leave

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.TodaySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LeaveUiState(
  val loading: Boolean = true,
  val leaves: List<LeaveRequest> = emptyList(),
  val sessions: List<TodaySession> = emptyList(),
  val selectedSessionId: Long = 0,
  val reason: String = "",
  val error: String? = null,
  val submitted: Boolean = false,
)

class LeaveViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(LeaveUiState())
  val uiState: StateFlow<LeaveUiState> = _uiState

  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.leaveRequests() to api.sessions("recent") }
      .onSuccess { (leaves, sessions) -> _uiState.update { it.copy(loading = false, leaves = leaves, sessions = sessions) } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }

  fun selectSession(id: Long) = _uiState.update { it.copy(selectedSessionId = id) }
  fun updateReason(value: String) = _uiState.update { it.copy(reason = value) }

  suspend fun submit() {
    val state = uiState.value
    if (state.selectedSessionId == 0L || state.reason.isBlank()) {
      _uiState.update { it.copy(error = "请选择课程并填写原因") }
      return
    }
    runCatching { api.submitLeave(state.selectedSessionId, state.reason) }
      .onSuccess { _uiState.update { it.copy(submitted = true, reason = "", error = null) } }
      .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "提交失败") } }
  }
}
