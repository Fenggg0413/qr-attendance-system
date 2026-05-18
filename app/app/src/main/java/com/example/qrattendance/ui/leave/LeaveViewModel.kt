package com.example.qrattendance.ui.leave

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.TodaySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// 请假页 UI 状态：同时持有历史申请列表（leaves）与可选会话列表（sessions），用同一份 state 驱动列表页与表单页。
data class LeaveUiState(
  val loading: Boolean = true,
  val leaves: List<LeaveRequest> = emptyList(),
  val sessions: List<TodaySession> = emptyList(),
  val selectedSessionId: Long = 0,
  val reason: String = "",
  val error: String? = null,
  val submitted: Boolean = false,
)

// 请假 ViewModel：管理列表加载、表单输入、提交。
class LeaveViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(LeaveUiState())
  val uiState: StateFlow<LeaveUiState> = _uiState

  // 并行拉取历史申请 + 最近可请假的会话列表（注意：to 这里是顺序调用，未做真正并行，因 API 数量少不优化）。
  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.leaveRequests() to api.sessions("recent") }
      .onSuccess { (leaves, sessions) -> _uiState.update { it.copy(loading = false, leaves = leaves, sessions = sessions) } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }

  fun selectSession(id: Long) = _uiState.update { it.copy(selectedSessionId = id) }
  fun updateReason(value: String) = _uiState.update { it.copy(reason = value) }

  // 提交请假：本地校验 sessionId 非 0 且 reason 非空 → 调 API → 成功后清空 reason 并标记 submitted 触发返回上一页。
  suspend fun submit() {
    val state = uiState.value
    if (state.selectedSessionId == 0L || state.reason.isBlank()) {
      _uiState.update { it.copy(error = "请选择课程并填写原因") }
      return
    }
    runCatching { api.submitLeave(state.selectedSessionId, state.reason) }
      .onSuccess { _uiState.update { it.copy(submitted = true, reason = "", error = null) } }
      .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "提交失败") } }
  }
}
