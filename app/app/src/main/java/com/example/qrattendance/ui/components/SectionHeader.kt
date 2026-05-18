package com.example.qrattendance.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// 列表分组的小标题：左侧竖条 + 文字，用于分隔同页内的多个模块。
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
  }
}
