package com.example.qrattendance.ui.leave

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.LeaveRequestSummary
import com.example.qrattendance.data.LeaveResponse
import com.example.qrattendance.data.repository.LeaveRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LeaveViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun submit_requiresReason() {
    val viewModel = LeaveViewModel(FakeLeaveRepository(), mainDispatcherRule.dispatcher)
    viewModel.prepare(9, "移动开发")

    viewModel.submit()

    assertEquals("请填写原因", viewModel.uiState.value.error)
  }

  @Test
  fun submit_successShowsMessage() = runTest {
    val repository = FakeLeaveRepository()
    val viewModel = LeaveViewModel(repository, mainDispatcherRule.dispatcher)
    viewModel.prepare(9, "移动开发")
    viewModel.updateReason("病假")

    viewModel.submit()

    assertEquals(9L, repository.sessionId)
    assertEquals("申报已提交", viewModel.uiState.value.message)
  }
}

private class FakeLeaveRepository : LeaveRepository {
  var sessionId: Long? = null

  override suspend fun leaveRequests(): List<LeaveRequestSummary> = emptyList()

  override suspend fun submitLeave(sessionId: Long, reason: String): LeaveResponse {
    this.sessionId = sessionId
    return LeaveResponse(1, "PENDING")
  }
}
