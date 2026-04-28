package com.example.qrattendance.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
  val user: UserProfile? = null,
  val displayName: String = "",
  val currentPassword: String = "",
  val newPassword: String = "",
  val loading: Boolean = false,
  val message: String? = null,
  val error: String? = null,
)

class ProfileViewModel(
  private val repository: ProfileRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState =
    MutableStateFlow(
      ProfileUiState(
        user = repository.currentSession?.user,
        displayName = repository.currentSession?.user?.displayName.orEmpty(),
      ),
    )
  val uiState: StateFlow<ProfileUiState> = _uiState

  fun updateDisplayName(value: String) = _uiState.update { it.copy(displayName = value) }

  fun updateCurrentPassword(value: String) = _uiState.update { it.copy(currentPassword = value) }

  fun updateNewPassword(value: String) = _uiState.update { it.copy(newPassword = value) }

  fun saveProfile() {
    val displayName = _uiState.value.displayName.trim()
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null, message = null) }
      runCatching { repository.updateProfile(displayName) }
        .onSuccess { profile -> _uiState.update { it.copy(user = profile, message = "资料已更新") } }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "资料更新失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }

  fun changePassword() {
    val state = _uiState.value
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null, message = null) }
      runCatching { repository.changePassword(state.currentPassword, state.newPassword) }
        .onSuccess { _uiState.update { it.copy(currentPassword = "", newPassword = "", message = "密码已更新") } }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "密码更新失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }

  fun logout() {
    viewModelScope.launch(dispatcher) { repository.logout() }
  }
}
