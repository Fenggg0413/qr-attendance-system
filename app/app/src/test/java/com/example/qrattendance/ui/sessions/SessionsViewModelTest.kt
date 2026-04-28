package com.example.qrattendance.ui.sessions

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.SessionSummary
import com.example.qrattendance.data.repository.SessionsRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SessionsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun setScope_refreshesSessions() = runTest {
    val repository = FakeSessionsRepository()
    val viewModel = SessionsViewModel(repository, mainDispatcherRule.dispatcher)

    viewModel.setScope("recent")

    assertEquals("recent", repository.scope)
    assertEquals(1, viewModel.uiState.value.sessions.size)
  }

  @Test
  fun setStatusFilter_filtersRecords() {
    val state = SessionsUiState(sessions = listOf(SessionSummary(status = "OPEN"), SessionSummary(status = "CLOSED")), statusFilter = "OPEN")

    assertEquals(1, state.filteredSessions.size)
  }
}

private class FakeSessionsRepository : SessionsRepository {
  var scope: String? = null

  override suspend fun sessions(scope: String): List<SessionSummary> {
    this.scope = scope
    return listOf(SessionSummary(id = 1, status = "OPEN"))
  }
}
