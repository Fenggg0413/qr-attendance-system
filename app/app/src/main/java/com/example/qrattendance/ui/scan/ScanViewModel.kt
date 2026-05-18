package com.example.qrattendance.ui.scan

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.qr.QrPayloadParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// 扫码页 UI 状态：loading 控制环形进度、success 触发动画与关闭、message 是提示文案。
data class ScanUiState(val loading: Boolean = false, val message: String = "将二维码放入取景框内", val success: Boolean = false)

// 扫码 ViewModel：接收相机帧分析器抛上来的原始字符串，解析为签到 payload 后请求服务端签到。
class ScanViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(ScanUiState())
  val uiState: StateFlow<ScanUiState> = _uiState

  // 扫到 QR 内容后的处理：解析失败 → 提示无效；解析成功 → 调 checkIn → 成功/失败文案回写。
  suspend fun onPayload(raw: String) {
    val payload = QrPayloadParser.parse(raw)
    // 协议不匹配或字段缺失：仅提示，不切 loading，让用户继续对准重新识别。
    if (payload == null) {
      _uiState.update { it.copy(message = "无效的签到二维码") }
      return
    }
    _uiState.update { it.copy(loading = true, message = "签到中") }
    runCatching { api.checkIn(payload.sessionId, payload.token) }
      .onSuccess { _uiState.update { it.copy(loading = false, success = true, message = "签到成功") } }
      // 失败文案优先用异常 message（如"二维码已过期"），缺失则降级到固定文案。
      .onFailure { error -> _uiState.update { it.copy(loading = false, message = error.message ?: "签到失败") } }
  }
}
