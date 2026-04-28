package com.example.qrattendance.ui.profile

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.Session
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.ProfileRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun saveProfile_updatesDisplayName() = runTest {
    val repository = FakeProfileRepository()
    val viewModel = ProfileViewModel(repository, mainDispatcherRule.dispatcher)

    viewModel.updateDisplayName("新名字")
    viewModel.saveProfile()

    assertEquals("新名字", repository.displayName)
    assertEquals("资料已更新", viewModel.uiState.value.message)
  }

  @Test
  fun changePassword_sendsPasswords() = runTest {
    val repository = FakeProfileRepository()
    val viewModel = ProfileViewModel(repository, mainDispatcherRule.dispatcher)

    viewModel.updateCurrentPassword("old")
    viewModel.updateNewPassword("newpass")
    viewModel.changePassword()

    assertEquals("old", repository.currentPassword)
    assertEquals("密码已更新", viewModel.uiState.value.message)
  }
}

private class FakeProfileRepository : ProfileRepository {
  override val currentSession: Session? = Session("token", UserProfile(username = "student", displayName = "李同学"))
  var displayName: String? = null
  var currentPassword: String? = null

  override suspend fun updateProfile(displayName: String): UserProfile {
    this.displayName = displayName
    return UserProfile(displayName = displayName)
  }

  override suspend fun changePassword(currentPassword: String, newPassword: String) {
    this.currentPassword = currentPassword
  }

  override suspend fun logout() = Unit
}
