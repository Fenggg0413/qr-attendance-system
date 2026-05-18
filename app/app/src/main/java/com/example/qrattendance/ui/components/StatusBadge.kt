package com.example.qrattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.StatusGreen
import com.example.qrattendance.ui.theme.StatusOrange
import com.example.qrattendance.ui.theme.StatusPurple
import com.example.qrattendance.ui.theme.StatusRed
import com.example.qrattendance.ui.theme.TextSecondary

// 状态徽章：根据 status 字符串（PRESENT/LATE/ABSENT/EXCUSED 等）选择对应的背景/文字色和中文标签。
@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
  val (label, color) = when (status.uppercase()) {
    "PRESENT" -> "已签到" to StatusGreen
    "ABSENT" -> "缺勤" to StatusRed
    "LATE" -> "迟到" to StatusOrange
    "EXCUSED" -> "请假" to StatusPurple
    "OPEN", "进行中" -> "进行中" to Primary
    "PENDING" -> "待确认" to StatusOrange
    "CLOSED" -> "已结束" to TextSecondary
    else -> "待签到" to TextSecondary
  }
  Text(
    label,
    color = color,
    style = MaterialTheme.typography.labelSmall,
    modifier = modifier
      .background(color.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  )
}
