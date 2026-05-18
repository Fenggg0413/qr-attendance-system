package com.example.qrattendance.ui.records

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.AttendanceRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// 考勤记录页的筛选 Tab：label 是中文按钮文案，status 是后端 attendance_records.status 枚举值（null 表示不过滤）。
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

// 考勤记录 ViewModel：一次拉取全部记录，本地按 filter 计算可见列表（避免每次切 Tab 都请求服务端）。
class RecordsViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(RecordsUiState())
  val uiState: StateFlow<RecordsUiState> = _uiState
  // visibleRecords 是 records 经 filter 过滤后的视图流，UI 直接 collect 此 Flow 即可。
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

  // 切换筛选 Tab：写 state 后立即重算 visibleRecords。
  fun setFilter(filter: RecordFilter) {
    _uiState.update { it.copy(filter = filter) }
    syncVisible()
  }

  // 根据当前 filter 重算可见列表；filter.status 为 null 时直接返回全集，避免不必要的复制。
  private fun syncVisible() {
    val state = _uiState.value
    _visibleRecords.value = state.filter.status?.let { status -> state.records.filter { it.status == status } } ?: state.records
  }
}
