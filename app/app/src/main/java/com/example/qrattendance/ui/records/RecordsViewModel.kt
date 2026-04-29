package com.example.qrattendance.ui.records

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.AttendanceRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class RecordFilter(val label: String, val status: String?) {
  All("全部", null),
  Present("出勤", "PRESENT"),
  Absent("缺勤", "ABSENT"),
}

data class RecordsUiState(
  val loading: Boolean = true,
  val records: List<AttendanceRecord> = emptyList(),
  val filter: RecordFilter = RecordFilter.All,
  val error: String? = null,
)

class RecordsViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(RecordsUiState())
  val uiState: StateFlow<RecordsUiState> = _uiState
  private val _visibleRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
  val visibleRecords: StateFlow<List<AttendanceRecord>> = _visibleRecords

  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.records() }
      .onSuccess { records ->
        _uiState.update { it.copy(loading = false, records = records) }
        syncVisible()
      }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }

  fun setFilter(filter: RecordFilter) {
    _uiState.update { it.copy(filter = filter) }
    syncVisible()
  }

  private fun syncVisible() {
    val state = _uiState.value
    _visibleRecords.value = state.filter.status?.let { status -> state.records.filter { it.status == status } } ?: state.records
  }
}
