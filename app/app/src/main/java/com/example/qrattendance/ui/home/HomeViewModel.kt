package com.example.qrattendance.ui.home

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.model.Dashboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// 首页 UI 状态：loading 控制骨架屏，dashboard 持有聚合数据，error 用于错误条展示。
data class HomeUiState(val loading: Boolean = true, val dashboard: Dashboard? = null, val error: String? = null)

// 首页 ViewModel：拉取仪表板聚合数据（今日课程 + 出勤统计）。
class HomeViewModel(private val api: StudentApi) {
  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState

  // 进入首页或下拉刷新时调用。
  suspend fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.dashboard() }
      .onSuccess { dashboard -> _uiState.update { it.copy(loading = false, dashboard = dashboard) } }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
  }
}
