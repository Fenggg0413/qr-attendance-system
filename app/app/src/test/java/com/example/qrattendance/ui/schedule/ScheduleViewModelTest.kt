package com.example.qrattendance.ui.schedule

import com.example.qrattendance.data.api.FakeStudentApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleViewModelTest {
  @Test
  fun selectedDayFiltersSlots() = runTest {
    val vm = ScheduleViewModel(FakeStudentApi())
    vm.load()
    vm.selectWeekday("周三")
    assertEquals(listOf("周三"), vm.visibleSlots.value.map { it.weekday }.distinct())
  }

  @Test
  fun studentScheduleIncludesAdminArrangedWeekdays() = runTest {
    val vm = ScheduleViewModel(FakeStudentApi())
    vm.load()
    assertEquals(listOf("周一", "周三", "周五"), vm.uiState.value.slots.map { it.weekday }.distinct())
  }
}
