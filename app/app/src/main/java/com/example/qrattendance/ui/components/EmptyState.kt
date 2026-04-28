package com.example.qrattendance.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.theme.TextSecondary
import com.example.qrattendance.ui.theme.TextTertiary

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Icons.Rounded.Inbox, contentDescription = null, tint = TextTertiary)
    Text(text, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
  }
}
