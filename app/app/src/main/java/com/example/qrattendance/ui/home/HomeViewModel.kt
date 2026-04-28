package com.example.qrattendance.ui.home

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.Dashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class HomeUiState(val loading: Boolean = true, val dashboard: Dashboard? = null, val error: String? = null)

class HomeViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState

  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.dashboard() }
      .onSuccess { dashboard -> _uiState.update { it.copy(loading = false, dashboard = dashboard) } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }
}
