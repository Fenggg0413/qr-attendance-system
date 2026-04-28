package com.example.qrattendance.ui.login

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
  val username: String = "student1",
  val password: String = "student123",
  val loading: Boolean = false,
  val error: String? = null,
  val loggedIn: Boolean = false,
)

class LoginViewModel(private val api: StudentApi, private val sessionStore: SessionStore) {
  private val _uiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = _uiState

  fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
  fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

  suspend fun login(username: String = uiState.value.username, password: String = uiState.value.password) {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.login(username, password) }
      .onSuccess { response ->
        sessionStore.save(response.token, response.user)
        _uiState.update { it.copy(loading = false, loggedIn = true) }
      }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "登录失败") } }
  }
}
