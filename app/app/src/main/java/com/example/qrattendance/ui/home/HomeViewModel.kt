package com.example.qrattendance.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.SessionStore
import com.example.qrattendance.data.SessionSummary
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.RecordsRepository
import com.example.qrattendance.data.repository.SessionsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
  val user: UserProfile? = null,
  val activeSessions: List<SessionSummary> = emptyList(),
  val recentRecords: List<AttendanceRecord> = emptyList(),
  val loading: Boolean = false,
  val error: String? = null,
)

class HomeViewModel(
  private val sessionStore: SessionStore,
  private val sessionsRepository: SessionsRepository,
  private val recordsRepository: RecordsRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow(HomeUiState(user = sessionStore.sessions.value?.user))
  val uiState: StateFlow<HomeUiState> = _uiState

  fun refresh() {
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null, user = sessionStore.sessions.value?.user) }
      runCatching {
          val sessions = sessionsRepository.sessions("active")
          val records = recordsRepository.records()
          _uiState.update { it.copy(activeSessions = sessions, recentRecords = records.take(5)) }
        }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "首页加载失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}
