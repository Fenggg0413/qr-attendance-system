package com.example.qrattendance.ui.profile

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.User
import com.example.qrattendance.data.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ProfileUiState(
  val loading: Boolean = true,
  val user: User? = null,
  val error: String? = null,
  val editingProfile: Boolean = false,
  val displayNameDraft: String = "",
  val profileSaving: Boolean = false,
  val profileMessage: String? = null,
  val editingPassword: Boolean = false,
  val currentPassword: String = "",
  val newPassword: String = "",
  val confirmPassword: String = "",
  val passwordSaving: Boolean = false,
  val passwordMessage: String? = null,
)

class ProfileViewModel(private val api: StudentApi, private val sessionStore: SessionStore) {
  private val _uiState = MutableStateFlow(ProfileUiState())
  val uiState: StateFlow<ProfileUiState> = _uiState

  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.me() }
      .onSuccess { user -> _uiState.update { it.copy(loading = false, user = user) } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }

  fun openEditProfile() {
    val user = uiState.value.user
    _uiState.update {
      it.copy(
        editingProfile = true,
        displayNameDraft = user?.displayName?.ifBlank { user.name }.orEmpty(),
        profileMessage = null,
      )
    }
  }

  fun closeEditProfile() {
    _uiState.update { it.copy(editingProfile = false, profileSaving = false, profileMessage = null) }
  }

  fun updateDisplayNameDraft(value: String) {
    _uiState.update { it.copy(displayNameDraft = value, profileMessage = null) }
  }

  suspend fun submitProfile() {
    val displayName = uiState.value.displayNameDraft.trim()
    if (displayName.isBlank()) {
      _uiState.update { it.copy(profileMessage = "姓名不能为空") }
      return
    }
    _uiState.update { it.copy(profileSaving = true, profileMessage = null) }
    runCatching { api.updateProfile(displayName) }
      .onSuccess { user ->
        sessionStore.token()?.let { token -> sessionStore.save(token, user) }
        _uiState.update {
          it.copy(
            user = user,
            editingProfile = false,
            displayNameDraft = user.displayName.ifBlank { user.name },
            profileSaving = false,
            profileMessage = "资料已更新",
          )
        }
      }
      .onFailure { error ->
        _uiState.update { it.copy(profileSaving = false, profileMessage = error.message ?: "资料更新失败") }
      }
  }

  fun openEditPassword() {
    _uiState.update { it.copy(editingPassword = true, passwordMessage = null) }
  }

  fun closeEditPassword() {
    _uiState.update { it.copy(editingPassword = false, passwordSaving = false, passwordMessage = null) }
  }

  fun updateCurrentPassword(value: String) {
    _uiState.update { it.copy(currentPassword = value, passwordMessage = null) }
  }

  fun updateNewPassword(value: String) {
    _uiState.update { it.copy(newPassword = value, passwordMessage = null) }
  }

  fun updateConfirmPassword(value: String) {
    _uiState.update { it.copy(confirmPassword = value, passwordMessage = null) }
  }

  suspend fun submitPassword() {
    val state = uiState.value
    if (state.currentPassword.isBlank()) {
      _uiState.update { it.copy(passwordMessage = "请输入当前密码") }
      return
    }
    if (state.newPassword.length < 6) {
      _uiState.update { it.copy(passwordMessage = "新密码至少 6 位") }
      return
    }
    if (state.newPassword != state.confirmPassword) {
      _uiState.update { it.copy(passwordMessage = "两次输入的新密码不一致") }
      return
    }
    _uiState.update { it.copy(passwordSaving = true, passwordMessage = null) }
    runCatching { api.changePassword(state.currentPassword, state.newPassword) }
      .onSuccess {
        _uiState.update {
          it.copy(
            editingPassword = false,
            currentPassword = "",
            newPassword = "",
            confirmPassword = "",
            passwordSaving = false,
            passwordMessage = "密码已更新",
          )
        }
      }
      .onFailure { error ->
        _uiState.update { it.copy(passwordSaving = false, passwordMessage = error.message ?: "密码修改失败") }
      }
  }
}
