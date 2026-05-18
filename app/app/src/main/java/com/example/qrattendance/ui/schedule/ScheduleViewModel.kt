package com.example.qrattendance.ui.schedule

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.ScheduleSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

// 课表 UI 状态：selectedWeekday 默认取今天对应的中文星期，避免用户每次都要手动切换。
data class ScheduleUiState(
  val loading: Boolean = true,
  val selectedWeekday: String = defaultWeekday(),
  val slots: List<ScheduleSlot> = emptyList(),
  val error: String? = null,
)

// 课表 ViewModel：一次拉取整周课表，本地按 weekday 切换显示当天节次。
class ScheduleViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(ScheduleUiState())
  val uiState: StateFlow<ScheduleUiState> = _uiState
  // 当天可见的节次列表（已按 period 升序排序），UI 直接 collect。
  private val _visibleSlots = MutableStateFlow<List<ScheduleSlot>>(emptyList())
  val visibleSlots: StateFlow<List<ScheduleSlot>> = _visibleSlots

  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.schedule() }
      .onSuccess { slots ->
        _uiState.update { it.copy(loading = false, slots = slots) }
        syncVisible()
      }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }

  // 切换星期 Tab：写 state 后重算 visibleSlots。
  fun selectWeekday(value: String) {
    _uiState.update { it.copy(selectedWeekday = value) }
    syncVisible()
  }

  // 过滤当天 slot 并按节次升序排列，便于课表 UI 自上而下渲染。
  private fun syncVisible() {
    val state = _uiState.value
    _visibleSlots.value = state.slots.filter { it.weekday == state.selectedWeekday }.sortedBy { it.period }
  }
}

// 把今天的星期几（DayOfWeek.value: 周一=1）映射为中文标签；周末降级为周一，避免空数据。
fun defaultWeekday(): String =
  when (LocalDate.now().dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    else -> "周一"
  }
