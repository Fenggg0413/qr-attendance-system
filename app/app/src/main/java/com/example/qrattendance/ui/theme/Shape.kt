package com.example.qrattendance.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 Shapes：调整 small/medium/large/extraLarge 四档圆角，统一卡片风格。
val StudentShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(20.dp),
)
