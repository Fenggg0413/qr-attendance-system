package com.example.qrattendance.ui.login

import com.example.qrattendance.data.api.FakeStudentApi
import com.example.qrattendance.data.store.ApiEndpointStore
import com.example.qrattendance.data.store.MemoryApiEndpointProvider
import com.example.qrattendance.data.store.MemorySessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginViewModelTest {
  @Test
  fun loginPersistsSession() = runTest {
    val store = MemorySessionStore()
    val vm = LoginViewModel(FakeStudentApi(), store, MemoryApiEndpointProvider())
    vm.login("B22042101", "123456")
    assertTrue(vm.uiState.value.loggedIn)
    assertEquals("B22042101", store.current()?.username)
  }

  @Test
  fun loginFieldsStartEmpty() {
    val vm = LoginViewModel(FakeStudentApi(), MemorySessionStore(), MemoryApiEndpointProvider())
    assertEquals("", vm.uiState.value.username)
    assertEquals("", vm.uiState.value.password)
  }

  @Test
  fun openEndpointDialogShowsDialogAndLoadsCurrentUrl() {
    val endpointStore = MemoryApiEndpointProvider("http://192.168.1.100:8080")
    val vm = LoginViewModel(FakeStudentApi(), MemorySessionStore(), endpointStore)

    vm.openEndpointDialog()

    assertTrue(vm.uiState.value.showEndpointDialog)
    assertEquals("http://192.168.1.100:8080", vm.uiState.value.endpointDraft)
  }

  @Test
  fun closeEndpointDialogResetsState() {
    val vm = LoginViewModel(FakeStudentApi(), MemorySessionStore(), MemoryApiEndpointProvider())
    vm.openEndpointDialog()

    vm.closeEndpointDialog()

    assertFalse(vm.uiState.value.showEndpointDialog)
  }

  @Test
  fun saveEndpointPersistsNewUrl() {
    val endpointStore = MemoryApiEndpointProvider()
    val vm = LoginViewModel(FakeStudentApi(), MemorySessionStore(), endpointStore)
    vm.openEndpointDialog()
    vm.updateEndpointDraft("http://192.168.1.50:9090")

    vm.saveEndpoint()

    assertEquals("http://192.168.1.50:9090", endpointStore.baseUrl())
    assertFalse(vm.uiState.value.showEndpointDialog)
  }

  @Test
  fun saveEndpointFallsBackToDefaultWhenBlank() {
    val endpointStore = MemoryApiEndpointProvider()
    val vm = LoginViewModel(FakeStudentApi(), MemorySessionStore(), endpointStore)
    vm.openEndpointDialog()
    vm.updateEndpointDraft("   ")

    vm.saveEndpoint()

    assertEquals(ApiEndpointStore.DEFAULT_BASE_URL, endpointStore.baseUrl())
    assertFalse(vm.uiState.value.showEndpointDialog)
  }
}
