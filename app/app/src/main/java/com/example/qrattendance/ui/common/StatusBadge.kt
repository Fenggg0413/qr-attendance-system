package com.example.qrattendance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.qrattendance.theme.LocalStatusColors

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
  val colors = LocalStatusColors.current
  val color =
    when (status.uppercase()) {
      "PRESENT" -> colors.present
      "LATE" -> colors.late
      "ABSENT" -> colors.absent
      "EXCUSED" -> colors.excused
      "PENDING" -> colors.pending
      else -> MaterialTheme.colorScheme.primary
    }
  Box(modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp)) {
    Text(status.ifBlank { "未签到" }, color = color, style = MaterialTheme.typography.labelLarge)
  }
}
