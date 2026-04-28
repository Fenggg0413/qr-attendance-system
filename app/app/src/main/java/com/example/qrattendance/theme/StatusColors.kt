package com.example.qrattendance.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class StatusColors(
  val present: Color,
  val late: Color,
  val absent: Color,
  val excused: Color,
  val pending: Color,
  val accent: Color,
)

val LightStatusColors =
  StatusColors(
    present = PresentLight,
    late = LateLight,
    absent = AbsentLight,
    excused = ExcusedLight,
    pending = PendingLight,
    accent = AccentLight,
  )

val DarkStatusColors =
  StatusColors(
    present = PresentDark,
    late = LateDark,
    absent = AbsentDark,
    excused = ExcusedDark,
    pending = PendingDark,
    accent = AccentDark,
  )

val LocalStatusColors = compositionLocalOf { LightStatusColors }
