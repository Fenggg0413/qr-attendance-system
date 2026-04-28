package com.example.qrattendance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextSecondary

@Composable
fun QuickStatCard(value: String, label: String, tint: Color, icon: ImageVector = Icons.Rounded.CheckCircle, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .background(Surface, RoundedCornerShape(12.dp))
      .border(BorderStroke(1.dp, Border), RoundedCornerShape(12.dp))
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .background(tint.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
    Column(modifier = Modifier.padding(start = 10.dp)) {
      Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
  }
}
