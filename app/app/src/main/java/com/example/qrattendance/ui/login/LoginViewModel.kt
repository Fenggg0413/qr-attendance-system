package com.example.qrattendance.ui.login

import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.store.ApiEndpointProvider
import com.example.qrattendance.data.store.ApiEndpointStore
import com.example.qrattendance.data.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// 登录页 UI 状态：输入框内容、加载/错误、登录成功标志，以及"设置服务端地址"对话框的本地草稿。
data class LoginUiState(
  val username: String = "",
  val password: String = "",
  val loading: Boolean = false,
  val error: String? = null,
  val loggedIn: Boolean = false,
  val showEndpointDialog: Boolean = false,
  val endpointDraft: String = "",
)

// 登录 ViewModel：负责账号密码输入、服务端 baseUrl 配置对话框、调 API 与持久化会话。
class LoginViewModel(
  private val api: StudentApi,
  private val sessionStore: SessionStore,
  private val endpointProvider: ApiEndpointProvider,
) {
  private val _uiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = _uiState

  // 输入框双向绑定：用户名/密码即时回写 state。
  fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }
  fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

  // 打开"服务端地址"对话框：把当前持久化的地址灌入草稿便于编辑。
  fun openEndpointDialog() {
    _uiState.update { it.copy(showEndpointDialog = true, endpointDraft = endpointProvider.baseUrl()) }
  }

  // 关闭对话框并清空草稿，避免下次打开看到上次未保存的值。
  fun closeEndpointDialog() {
    _uiState.update { it.copy(showEndpointDialog = false, endpointDraft = "") }
  }

  fun updateEndpointDraft(value: String) {
    _uiState.update { it.copy(endpointDraft = value) }
  }

  // 保存地址：去空后回落到默认值，避免把空串写入造成网络请求 URL 无效。
  fun saveEndpoint() {
    val url = _uiState.value.endpointDraft.trim().ifBlank { ApiEndpointStore.DEFAULT_BASE_URL }
    endpointProvider.save(url)
    _uiState.update { it.copy(showEndpointDialog = false, endpointDraft = "") }
  }

  // 登录流程：① 切 loading 状态 ② 调 api.login ③ 成功则二次保险写入 sessionStore（api 内部已保存一次） ④ 切 loggedIn 触发上层路由跳转。
  suspend fun login(username: String = uiState.value.username, password: String = uiState.value.password) {
    _uiState.update { it.copy(loading = true, error = null) }
    runCatching { api.login(username, password) }
      .onSuccess { response ->
        // 显式再写一次：解耦"是否登录"判定与 api 实现，便于测试中 fake api 不写 store 也能登录。
        sessionStore.save(response.token, response.user)
        _uiState.update { it.copy(loading = false, loggedIn = true) }
      }
      .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.message ?: "登录失败") } }
  }
}