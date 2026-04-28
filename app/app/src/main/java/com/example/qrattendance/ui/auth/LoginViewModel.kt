package com.example.qrattendance.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.ApiEndpointStore
import com.example.qrattendance.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
  val username: String = "",
  val password: String = "",
  val serverUrl: String = ApiEndpointStore.DEFAULT_BASE_URL,
  val loading: Boolean = false,
  val error: String? = null,
)

class LoginViewModel(
  private val repository: AuthRepository,
  private val apiEndpointStore: ApiEndpointStore,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow(LoginUiState(serverUrl = apiEndpointStore.baseUrl.value))
  val uiState: StateFlow<LoginUiState> = _uiState

  fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }

  fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

  fun updateServerUrl(value: String) = _uiState.update { it.copy(serverUrl = value) }

  fun login() {
    val state = _uiState.value
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null) }
      apiEndpointStore.save(state.serverUrl)
        .onSuccess { normalized -> _uiState.update { it.copy(serverUrl = normalized) } }
        .onFailure {
          _uiState.update { it.copy(loading = false, error = "服务器地址格式不正确") }
          return@launch
        }
      runCatching { repository.login(state.username, state.password) }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "登录失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}
