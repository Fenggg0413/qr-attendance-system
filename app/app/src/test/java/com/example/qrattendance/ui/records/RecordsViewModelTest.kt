package com.example.qrattendance.ui.records

import com.example.qrattendance.data.api.FakeStudentApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsViewModelTest {
  @Test
  fun filterKeepsMatchingStatuses() = runTest {
    val vm = RecordsViewModel(FakeStudentApi())
    vm.load()
    vm.setFilter(RecordFilter.Absent)
    assertEquals(listOf("ABSENT"), vm.visibleRecords.value.map { it.status }.distinct())
  }
}
