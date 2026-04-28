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
    vm.login("student1", "student123")
    assertTrue(vm.uiState.value.loggedIn)
    assertEquals("student1", store.current()?.username)
  }
}
