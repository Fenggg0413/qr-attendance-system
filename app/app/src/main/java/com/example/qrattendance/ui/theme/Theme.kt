package com.example.qrattendance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 业务状态色集合：把出勤/缺勤/迟到/请假等业务色统一封装，避免散落在各 UI 中重复硬编码。
data class StatusColors(
  val present: Color = StatusGreen,
  val pending: Color = StatusOrange,
  val absent: Color = StatusRed,
  val late: Color = StatusOrange,
  val excused: Color = StatusPurple,
)

// 通过 CompositionLocal 把 StatusColors 下发到任意 Composable，便于自定义主题时覆盖。
val LocalStatusColors = staticCompositionLocalOf { StatusColors() }

// 应用主题入口：组合 Material 3 ColorScheme、Typography、Shapes，包裹整棵 UI 树。
@Composable
fun StudentAppTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = lightColorScheme(
      primary = Primary,
      secondary = Accent,
      background = Background,
      surface = Surface,
      onPrimary = Color.White,
      onSurface = TextPrimary,
    ),
    typography = StudentTypography,
    shapes = StudentShapes,
    content = content,
  )
}
