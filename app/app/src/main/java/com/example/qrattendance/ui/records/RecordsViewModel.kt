package com.example.qrattendance.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.repository.RecordsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordsUiState(
  val filter: String = "ALL",
  val records: List<AttendanceRecord> = emptyList(),
  val loading: Boolean = false,
  val error: String? = null,
) {
  val filteredRecords: List<AttendanceRecord>
    get() = if (filter == "ALL") records else records.filter { it.status == filter }
}

class RecordsViewModel(
  private val repository: RecordsRepository,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RecordsUiState())
  val uiState: StateFlow<RecordsUiState> = _uiState

  fun setFilter(filter: String) = _uiState.update { it.copy(filter = filter) }

  fun refresh() {
    viewModelScope.launch(dispatcher) {
      _uiState.update { it.copy(loading = true, error = null) }
      runCatching { repository.records() }
        .onSuccess { records -> _uiState.update { it.copy(records = records) } }
        .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "记录加载失败") } }
      _uiState.update { it.copy(loading = false) }
    }
  }
}
