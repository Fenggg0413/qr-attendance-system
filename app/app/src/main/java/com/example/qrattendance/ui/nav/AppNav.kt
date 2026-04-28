package com.example.qrattendance.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.login.LoginScreen

@Composable
fun AppNav() {
  val container = LocalContainer.current
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
