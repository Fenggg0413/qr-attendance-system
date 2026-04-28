package com.example.qrattendance.ui.login

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.store.ApiEndpointProvider
import com.example.qrattendance.data.store.ApiEndpointStore
import com.example.qrattendance.data.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
  val username: String = "",
  val password: String = "",
  val loading: Boolean = false,
  val error: String? = null,
  val loggedIn: Boolean = false,
  val showEndpointDialog: Boolean = false,
  val endpointDraft: String = "",
)

class LoginViewModel(
  private val api: StudentApi,
  private val sessionStore: SessionStore,
  private val endpointProvider: ApiEndpointProvider,
) {
  private val _uiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = _uiState

  fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
  fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

  fun openEndpointDialog() {
    _uiState.update { it.copy(showEndpointDialog = true, endpointDraft = endpointProvider.baseUrl()) }
  }

  fun closeEndpointDialog() {
    _uiState.update { it.copy(showEndpointDialog = false, endpointDraft = "") }
  }

  fun updateEndpointDraft(value: String) {
    _uiState.update { it.copy(endpointDraft = value) }
  }

  fun saveEndpoint() {
    val url = _uiState.value.endpointDraft.trim().ifBlank { ApiEndpointStore.DEFAULT_BASE_URL }
    endpointProvider.save(url)
    _uiState.update { it.copy(showEndpointDialog = false, endpointDraft = "") }
  }

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