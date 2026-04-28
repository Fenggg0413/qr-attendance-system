package com.example.qrattendance.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors
  CompositionLocalProvider(LocalStatusColors provides statusColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
