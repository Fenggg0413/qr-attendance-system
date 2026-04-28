package com.example.qrattendance.ui.home

import com.example.qrattendance.data.api.FakeStudentApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeViewModelTest {
  @Test
  fun loadDashboardUpdatesState() = runTest {
    val vm = HomeViewModel(FakeStudentApi())
    vm.load()
    assertFalse(vm.uiState.value.loading)
    assertEquals(3, vm.uiState.value.dashboard?.todayCount)
  }
}
