package com.example.qrattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.TextSecondary

@Composable
fun FilterChips(items: List<String>, selected: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items.forEach { item ->
      val active = item == selected
      Text(
        item,
        color = if (active) Color.White else TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
          .background(if (active) Primary else Color.White, RoundedCornerShape(20.dp))
          .clickable { onSelected(item) }
          .padding(horizontal = 14.dp, vertical = 7.dp),
      )
    }
  }
}
