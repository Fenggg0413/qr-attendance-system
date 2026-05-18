package com.example.qrattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.PrimaryLight

// 顶部彩色横幅：左侧标题/副标题 + 右侧统计 Pair 列表（label 与 value 同色）。
@Composable
fun Banner(title: String, subtitle: String, stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Brush.linearGradient(listOf(Color(0xFF1565C0), Primary, PrimaryLight)), RoundedCornerShape(16.dp))
      .padding(18.dp),
  ) {
    Text(subtitle, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
      stats.forEach { (value, label) ->
        Column {
          Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}
