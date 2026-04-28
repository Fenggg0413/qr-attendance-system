package com.example.qrattendance.ui.profile

import com.example.qrattendance.data.api.FakeStudentApi
import com.example.qrattendance.data.store.MemorySessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileViewModelTest {
  @Test
  fun loadFetchesCurrentUser() = runTest {
    val vm = ProfileViewModel(FakeStudentApi(), MemorySessionStore())

    vm.load()

    assertFalse(vm.uiState.value.loading)
    assertEquals("李同学", vm.uiState.value.user?.displayName)
  }

  @Test
  fun openEditProfilePrefillsDisplayName() = runTest {
    val vm = ProfileViewModel(FakeStudentApi(), MemorySessionStore())
    vm.load()

    vm.openEditProfile()

    assertTrue(vm.uiState.value.editingProfile)
    assertEquals("李同学", vm.uiState.value.displayNameDraft)
  }

  @Test
  fun submitProfileRejectsBlankDisplayName() = runTest {
    val api = FakeStudentApi()
    val vm = ProfileViewModel(api, MemorySessionStore())
    vm.updateDisplayNameDraft("   ")

    vm.submitProfile()

    assertEquals("姓名不能为空", vm.uiState.value.profileMessage)
    assertNull(api.lastProfileDisplayName)
  }

  @Test
  fun submitProfileUpdatesUserAndSessionSnapshot() = runTest {
    val api = FakeStudentApi()
    val store = MemorySessionStore()
    store.save("token", api.me())
    val vm = ProfileViewModel(api, store)
    vm.load()
    vm.openEditProfile()
    vm.updateDisplayNameDraft("新名字")

    vm.submitProfile()

    assertFalse(vm.uiState.value.editingProfile)
    assertEquals("新名字", vm.uiState.value.user?.displayName)
    assertEquals("新名字", store.current()?.displayName)
    assertEquals("新名字", api.lastProfileDisplayName)
  }

  @Test
  fun submitPasswordRejectsMismatchConfirmation() = runTest {
    val api = FakeStudentApi()
    val vm = ProfileViewModel(api, MemorySessionStore())
    vm.updateCurrentPassword("123456")
    vm.updateNewPassword("abcdef")
    vm.updateConfirmPassword("abcdeg")

    vm.submitPassword()

    assertEquals("两次输入的新密码不一致", vm.uiState.value.passwordMessage)
    assertNull(api.lastPasswordChange)
  }

  @Test
  fun submitPasswordClearsFieldsAndClosesDialog() = runTest {
    val api = FakeStudentApi()
    val vm = ProfileViewModel(api, MemorySessionStore())
    vm.openEditPassword()
    vm.updateCurrentPassword("123456")
    vm.updateNewPassword("abcdef")
    vm.updateConfirmPassword("abcdef")

    vm.submitPassword()

    assertFalse(vm.uiState.value.editingPassword)
    assertEquals("", vm.uiState.value.currentPassword)
    assertEquals("", vm.uiState.value.newPassword)
    assertEquals("", vm.uiState.value.confirmPassword)
    assertEquals("123456" to "abcdef", api.lastPasswordChange)
  }
}
