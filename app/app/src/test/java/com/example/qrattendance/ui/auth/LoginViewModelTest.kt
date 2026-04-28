package com.example.qrattendance.ui.auth

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.ApiEndpointStore
import com.example.qrattendance.data.LoginResponse
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.AuthRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun initialState_hasEmptyCredentials() {
    val viewModel = LoginViewModel(FakeAuthRepository(), FakeApiEndpointStore(), mainDispatcherRule.dispatcher)

    assertEquals("", viewModel.uiState.value.username)
    assertEquals("", viewModel.uiState.value.password)
    assertEquals("http://10.0.2.2:8080/api", viewModel.uiState.value.serverUrl)
  }

  @Test
  fun login_successClearsLoading() = runTest {
    val repository = FakeAuthRepository()
    val endpointStore = FakeApiEndpointStore()
    val viewModel = LoginViewModel(repository, endpointStore, mainDispatcherRule.dispatcher)

    viewModel.updateUsername("student")
    viewModel.updatePassword("pass")
    viewModel.updateServerUrl("192.168.1.23:8080")
    viewModel.login()

    assertEquals("student", repository.username)
    assertEquals("http://192.168.1.23:8080/api", endpointStore.baseUrl.value)
    assertFalse(viewModel.uiState.value.loading)
    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun login_failureShowsError() = runTest {
    val viewModel = LoginViewModel(FakeAuthRepository(RuntimeException("bad credentials")), FakeApiEndpointStore(), mainDispatcherRule.dispatcher)

    viewModel.login()

    assertNotNull(viewModel.uiState.value.error)
    assertEquals("bad credentials", viewModel.uiState.value.error)
  }

  @Test
  fun login_invalidServerUrlShowsErrorAndSkipsRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = LoginViewModel(repository, FakeApiEndpointStore(), mainDispatcherRule.dispatcher)

    viewModel.updateServerUrl("not a host")
    viewModel.login()

    assertEquals("服务器地址格式不正确", viewModel.uiState.value.error)
    assertEquals(null, repository.username)
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

private class FakeApiEndpointStore(initial: String = "http://10.0.2.2:8080/api") : ApiEndpointStore {
  override val baseUrl = kotlinx.coroutines.flow.MutableStateFlow(initial)

  override fun save(value: String): Result<String> {
    val normalized = ApiEndpointStore.normalize(value)
    normalized.onSuccess { baseUrl.value = it }
    return normalized
  }
}
