package com.example.qrattendance.ui.home

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.InMemorySessionStore
import com.example.qrattendance.data.Session
import com.example.qrattendance.data.SessionSummary
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.RecordsRepository
import com.example.qrattendance.data.repository.SessionsRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun refresh_loadsDashboardData() = runTest {
    val store = InMemorySessionStore(Session("token", UserProfile(displayName = "李同学")))
    val viewModel = HomeViewModel(store, FakeSessionsRepository(), FakeRecordsRepository(), mainDispatcherRule.dispatcher)

    viewModel.refresh()

    assertEquals("李同学", viewModel.uiState.value.user?.displayName)
    assertEquals(1, viewModel.uiState.value.activeSessions.size)
    assertEquals(1, viewModel.uiState.value.recentRecords.size)
  }
}

private class FakeSessionsRepository : SessionsRepository {
  override suspend fun sessions(scope: String): List<SessionSummary> = listOf(SessionSummary(id = 1, courseName = "移动开发", canRequestLeave = true))
}

private class FakeRecordsRepository : RecordsRepository {
  override suspend fun records(): List<AttendanceRecord> = listOf(AttendanceRecord(id = 1, courseName = "移动开发", status = "PRESENT"))
}
