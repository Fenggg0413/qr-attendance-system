package com.example.qrattendance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class StatusColors(
  val present: Color = StatusGreen,
  val pending: Color = StatusOrange,
  val absent: Color = StatusRed,
  val late: Color = StatusOrange,
  val excused: Color = StatusPurple,
)

val LocalStatusColors = staticCompositionLocalOf { StatusColors() }

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
