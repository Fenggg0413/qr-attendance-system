package com.example.qrattendance.ui.scan

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.qr.QrPayloadParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ScanUiState(val loading: Boolean = false, val message: String = "将二维码放入取景框内", val success: Boolean = false)

class ScanViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(ScanUiState())
  val uiState: StateFlow<ScanUiState> = _uiState

  suspend fun onPayload(raw: String) {
    val payload = QrPayloadParser.parse(raw)
    if (payload == null) {
      _uiState.update { it.copy(message = "无效的签到二维码") }
      return
    }
    _uiState.update { it.copy(loading = true, message = "签到中") }
    runCatching { api.checkIn(payload.sessionId, payload.token) }
      .onSuccess { _uiState.update { it.copy(loading = false, success = true, message = "签到成功") } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.message ?: "签到失败") } }
  }
}
