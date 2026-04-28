package com.example.qrattendance.ui.login

import com.example.qrattendance.data.api.FakeStudentApi
import com.example.qrattendance.data.store.MemorySessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginViewModelTest {
  @Test
  fun loginPersistsSession() = runTest {
    val store = MemorySessionStore()
    val vm = LoginViewModel(FakeStudentApi(), store)
    vm.login("B22042101", "123456")
    assertTrue(vm.uiState.value.loggedIn)
    assertEquals("B22042101", store.current()?.username)
  }

  @Test
  fun loginFieldsStartEmpty() {
    val vm = LoginViewModel(FakeStudentApi(), MemorySessionStore())
    assertEquals("", vm.uiState.value.username)
    assertEquals("", vm.uiState.value.password)
  }
}
