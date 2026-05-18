package com.example.qrattendance.ui.leave

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.Format
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.ui.components.EmptyState
import com.example.qrattendance.ui.components.StatusBadge
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextSecondary

@Composable
// 请假列表页：展示已提交的请假申请，右下角 FAB 跳转到 LeaveComposeScreen 新增申请。
fun LeaveListScreen(onBack: () -> Unit, onCompose: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { LeaveViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  LaunchedEffect(Unit) { vm.load() }
  androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Background)) {
    Column(Modifier.fillMaxSize()) {
      LeaveTopBar("请假与申诉", onBack)
      LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.leaves.isEmpty()) item { EmptyState("暂无请假或申诉记录") }
        items(state.leaves, key = { it.id }) { LeaveRow(it) }
      }
    }
    FloatingActionButton(onClick = onCompose, containerColor = Primary, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
      Icon(Icons.Rounded.Add, contentDescription = "新建", tint = androidx.compose.ui.graphics.Color.White)
    }
  }
}

@Composable
// 请假相关页面共用的顶栏：左侧返回箭头 + 居中标题。
fun LeaveTopBar(title: String, onBack: () -> Unit) {
  Row(Modifier.fillMaxWidth().background(Surface).padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
    Text(title, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
private fun LeaveRow(item: LeaveRequest) {
  Row(
    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Surface, RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, Border), RoundedCornerShape(12.dp)).padding(14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(Modifier.weight(1f)) {
      Text(item.courseName.ifBlank { "课程" }, style = MaterialTheme.typography.bodyMedium)
      Text("${Format.compactDateTime(item.createdAt)} · ${item.reason}", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
    StatusBadge(item.status)
  }
}
