package com.example.qrattendance.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey

@Serializable data object HomeShell : NavKey

@Serializable data object Scan : NavKey

@Serializable data class LeaveCompose(val sessionId: Long, val courseName: String) : NavKey

enum class HomeTab(val label: String) {
  Home("首页"),
  Sessions("会话"),
  Records("记录"),
  Profile("我的"),
}
