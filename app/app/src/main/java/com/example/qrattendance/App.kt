package com.example.qrattendance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.qrattendance.core.AppContainer
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.ui.nav.AppNav
import com.example.qrattendance.ui.theme.StudentAppTheme

@Composable
fun App() {
  val context = LocalContext.current.applicationContext
  val container = remember { AppContainer(context) }
  StudentAppTheme {
    CompositionLocalProvider(LocalContainer provides container) {
      AppNav()
    }
  }
}
