package com.example.qrattendance.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.login.LoginScreen

// 应用入口路由：判定是否已登录 → 进入主 Scaffold 或登录页；登录/登出会切换 loggedIn 触发重组。
@Composable
fun AppNav() {
  val container = LocalContainer.current
  val current = container.sessionStore.current()
  // 启动时清除旧的演示账号会话，强制用户用新版账号重新登录。
  if (shouldClearLegacySession(current)) {
    container.sessionStore.clear()
  }
  var loggedIn by remember { mutableStateOf(container.sessionStore.current() != null) }
  if (loggedIn) {
    MainScaffold(onLogout = {
      container.sessionStore.clear()
      loggedIn = false
    })
  } else {
    LoginScreen(onLoggedIn = { loggedIn = true })
  }
}

// 旧版演示账号 "student1" 已弃用：检测到本地仍残留则清除，避免用户用过期凭据访问服务端被 401。
fun shouldClearLegacySession(session: com.example.qrattendance.data.model.SessionSnapshot?): Boolean =
  session?.username == "student1"
