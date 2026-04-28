package com.example.qrattendance.ui.scan

import com.example.qrattendance.MainDispatcherRule
import com.example.qrattendance.data.CheckInRequest
import com.example.qrattendance.data.CheckInResponse
import com.example.qrattendance.data.repository.ScanRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ScanViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun submitPayload_invalidPayloadShowsError() {
    val viewModel = ScanViewModel(FakeScanRepository(), mainDispatcherRule.dispatcher)

    viewModel.submitPayload("bad")

    assertEquals("无效的二维码", viewModel.uiState.value.error)
  }

  @Test
  fun submitPayload_validPayloadChecksIn() = runTest {
    val repository = FakeScanRepository()
    val viewModel = ScanViewModel(repository, mainDispatcherRule.dispatcher)

    viewModel.submitPayload("qr-attendance://checkin?sessionId=42&token=abc")

    assertEquals(42L, repository.request?.sessionId)
    assertEquals("签到成功", viewModel.uiState.value.message)
    assertEquals("", viewModel.uiState.value.manualPayload)
  }
}

private class FakeScanRepository : ScanRepository {
  var request: CheckInRequest? = null

  override suspend fun checkIn(request: CheckInRequest): CheckInResponse {
    this.request = request
    return CheckInResponse(duplicate = false)
  }
}
