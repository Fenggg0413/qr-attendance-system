package com.example.qrattendance.ui.profile

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ProfileUiState(val loading: Boolean = true, val user: User? = null, val error: String? = null)

class ProfileViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState

  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.me() }
      .onSuccess { user -> _uiState.update { it.copy(loading = false, user = user) } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }
}
