package com.example.qrattendance.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.SessionSummary
import com.example.qrattendance.data.repository.SessionsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsUiState(
  val scope: String = "active",
  val statusFilter: String = "ALL",
  val sessions: List<SessionSummary> = emptyList(),
  val loading: Boolean = false,
  val error: String? = null,
) {
  val filteredSessions: List<SessionSummary>
    get() = if (statusFilter == "ALL") sessions else sessions.filter { it.status == statusFilter || it.recordStatus == statusFilter }
}

class SessionsViewModel(
  private val repository: SessionsRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SessionsUiState())
  val uiState: StateFlow<SessionsUiState> = _uiState

  fun setScope(scope: String) {
    _uiState.update { it.copy(scope = scope) }
    refresh()
  }

  fun setStatusFilter(status: String) {
    _uiState.update { it.copy(statusFilter = status) }
  }

  fun refresh() {
    val scope = _uiState.value.scope
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null) }
      runCatching { repository.sessions(scope) }
        .onSuccess { sessions -> _uiState.update { it.copy(sessions = sessions) } }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "会话加载失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}
