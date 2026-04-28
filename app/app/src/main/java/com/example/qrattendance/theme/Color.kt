package com.example.qrattendance.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val AccentLight = Color(0xFFA3E635)
val PresentLight = Color(0xFF16A34A)
val LateLight = Color(0xFFF59E0B)
val AbsentLight = Color(0xFFE11D48)
val ExcusedLight = Color(0xFF8B5CF6)
val PendingLight = Color(0xFF64748B)

val AccentDark = Color(0xFFBEF264)
val PresentDark = Color(0xFF22C55E)
val LateDark = Color(0xFFFBBF24)
val AbsentDark = Color(0xFFFB7185)
val ExcusedDark = Color(0xFFA78BFA)
val PendingDark = Color(0xFF94A3B8)

val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    secondary = Color(0xFF06B6D4),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2FF),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F5F9),
    error = AbsentLight,
  )

val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF111827),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155),
    surfaceContainerLow = Color(0xFF1E293B),
    surfaceContainer = Color(0xFF263449),
    error = AbsentDark,
  )
