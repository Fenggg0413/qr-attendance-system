package com.example.qrattendance.ui.records

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.repository.RecordsRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RecordsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun refresh_loadsRecordsAndFilterApplies() = runTest {
    val viewModel = RecordsViewModel(FakeRecordsRepository(), mainDispatcherRule.dispatcher)

    viewModel.refresh()
    viewModel.setFilter("PRESENT")

    assertEquals(2, viewModel.uiState.value.records.size)
    assertEquals(1, viewModel.uiState.value.filteredRecords.size)
  }
}

private class FakeRecordsRepository : RecordsRepository {
  override suspend fun records(): List<AttendanceRecord> =
    listOf(AttendanceRecord(id = 1, status = "PRESENT"), AttendanceRecord(id = 2, status = "ABSENT"))
}
