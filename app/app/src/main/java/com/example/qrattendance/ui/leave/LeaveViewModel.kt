package com.example.qrattendance.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.LeaveRequestSummary
import com.example.qrattendance.data.repository.LeaveRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaveUiState(
  val sessionId: String = "",
  val courseName: String = "",
  val reason: String = "",
  val requests: List<LeaveRequestSummary> = emptyList(),
  val loading: Boolean = false,
  val message: String? = null,
  val error: String? = null,
)

class LeaveViewModel(
  private val repository: LeaveRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow(LeaveUiState())
  val uiState: StateFlow<LeaveUiState> = _uiState

  fun prepare(sessionId: Long, courseName: String) {
    _uiState.update { it.copy(sessionId = sessionId.toString(), courseName = courseName) }
  }

  fun updateReason(value: String) = _uiState.update { it.copy(reason = value) }

  fun refresh() {
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null) }
      runCatching { repository.leaveRequests() }
        .onSuccess { requests -> _uiState.update { it.copy(requests = requests) } }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "请假记录加载失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }

  fun submit() {
    val sessionId = _uiState.value.sessionId.toLongOrNull()
    if (sessionId == null) {
      _uiState.update { it.copy(error = "请选择会话") }
      return
    }
    val reason = _uiState.value.reason.trim()
    if (reason.isBlank()) {
      _uiState.update { it.copy(error = "请填写原因") }
      return
    }
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null, message = null) }
      runCatching { repository.submitLeave(sessionId, reason) }
        .onSuccess { _uiState.update { it.copy(message = "申报已提交", reason = "") } }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "申报失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}
