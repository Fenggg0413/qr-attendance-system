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
    vm.selectWeekday("周二")
    assertEquals(listOf("周二"), vm.visibleSlots.value.map { it.weekday }.distinct())
  }
}
