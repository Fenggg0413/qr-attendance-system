package com.example.qrattendance.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null) {
  Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    if (actionText != null && onAction != null) {
      Button(onClick = onAction) { Text(actionText) }
    }
  }
}
