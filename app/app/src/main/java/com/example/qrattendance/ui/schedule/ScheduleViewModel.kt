package com.example.qrattendance.ui.schedule

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.ScheduleSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class ScheduleUiState(
  val loading: Boolean = true,
  val selectedWeekday: String = defaultWeekday(),
  val slots: List<ScheduleSlot> = emptyList(),
  val error: String? = null,
)

class ScheduleViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(ScheduleUiState())
  val uiState: StateFlow<ScheduleUiState> = _uiState
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

  fun selectWeekday(value: String) {
    _uiState.update { it.copy(selectedWeekday = value) }
    syncVisible()
  }

  private fun syncVisible() {
    val state = _uiState.value
    _visibleSlots.value = state.slots.filter { it.weekday == state.selectedWeekday }.sortedBy { it.period }
  }
}

fun defaultWeekday(): String =
  when (LocalDate.now().dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    else -> "周一"
  }
