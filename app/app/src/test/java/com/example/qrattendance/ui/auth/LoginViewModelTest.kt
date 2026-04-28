package com.example.qrattendance.ui.auth

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.LoginResponse
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.AuthRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun login_successClearsLoading() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = LoginViewModel(repository, mainDispatcherRule.dispatcher)

    viewModel.updateUsername("student")
    viewModel.updatePassword("pass")
    viewModel.login()

    assertEquals("student", repository.username)
    assertFalse(viewModel.uiState.value.loading)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun login_failureShowsError() = runTest {
    val viewModel = LoginViewModel(FakeAuthRepository(RuntimeException("bad credentials")), mainDispatcherRule.dispatcher)

    viewModel.login()

    assertEquals("bad credentials", viewModel.uiState.value.error)
  }
}

private class FakeAuthRepository(private val error: Throwable? = null) : AuthRepository {
  var username: String? = null

  override suspend fun login(username: String, password: String): LoginResponse {
    error?.let { throw it }
    this.username = username
    return LoginResponse("token", UserProfile(username = username, role = "STUDENT", displayName = "同学"))
  }

  override suspend fun logout() = Unit
}
