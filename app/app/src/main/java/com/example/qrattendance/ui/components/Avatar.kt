package com.example.qrattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.PrimaryLight

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 34.dp) {
  val initial = name.firstOrNull()?.toString() ?: "学"
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(Brush.linearGradient(listOf(Primary, PrimaryLight))),
    contentAlignment = Alignment.Center,
  ) {
    Text(initial, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
  }
}
