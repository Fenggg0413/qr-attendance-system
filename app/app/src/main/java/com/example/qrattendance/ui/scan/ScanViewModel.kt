package com.example.qrattendance.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.CheckInResponse
import com.example.qrattendance.data.QrPayloadParser
import com.example.qrattendance.data.repository.ScanRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
  val manualPayload: String = "",
  val loading: Boolean = false,
  val success: CheckInResponse? = null,
  val message: String? = null,
  val error: String? = null,
)

class ScanViewModel(
  private val repository: ScanRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ScanUiState())
  val uiState: StateFlow<ScanUiState> = _uiState
  private var lastPayload: String? = null
  private var lastAcceptedAt: Long = 0L

  fun updateManualPayload(value: String) = _uiState.update { it.copy(manualPayload = value) }

  fun submitManualPayload() = submitPayload(_uiState.value.manualPayload)

  fun submitPayload(payload: String) {
    val now = System.currentTimeMillis()
    if (payload == lastPayload && now - lastAcceptedAt < 500) return
    val request =
      QrPayloadParser.parse(payload) ?: run {
        _uiState.update { it.copy(error = "无效的二维码", message = null) }
        return
      }
    lastPayload = payload
    lastAcceptedAt = now
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null, message = null) }
      runCatching { repository.checkIn(request) }
        .onSuccess { response ->
          _uiState.update { it.copy(success = response, message = if (response.duplicate) "已签到" else "签到成功", manualPayload = "") }
        }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "签到失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}
