package com.example.qrattendance.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.Format
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.ui.components.EmptyState
import com.example.qrattendance.ui.components.FilterChips
import com.example.qrattendance.ui.components.SectionHeader
import com.example.qrattendance.ui.components.TopBar
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.StatusGreen
import com.example.qrattendance.ui.theme.StatusOrange
import com.example.qrattendance.ui.theme.StatusPurple
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextSecondary
import com.example.qrattendance.ui.theme.TextTertiary

@Composable
// 课表页：顶部周一至周五 Tab，下方 LazyColumn 展示对应日的节次与教室。
fun ScheduleScreen() {
  val container = LocalContainer.current
  val vm = remember { ScheduleViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  val visible by vm.visibleSlots.collectAsState()
  LaunchedEffect(Unit) { vm.load() }
  Column(modifier = Modifier.fillMaxSize().background(Background)) {
    TopBar("本周课表", "第 ${Format.currentSemesterWeek()} 周", container.sessionStore.current()?.displayName ?: "学生")
    FilterChips(
      items = listOf("周一", "周二", "周三", "周四", "周五"),
      selected = state.selectedWeekday,
      onSelected = vm::selectWeekday,
      modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp),
    )
    if (state.loading) {
      CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (visible.isEmpty()) item { EmptyState("当天暂无课程") }
        groupTitle("上午", visible.any { it.period in 1..4 })
        items(visible.filter { it.period in 1..4 }, key = { it.slotId }) { ScheduleCard(it) }
        groupTitle("下午", visible.any { it.period in 5..7 })
        items(visible.filter { it.period in 5..7 }, key = { it.slotId }) { ScheduleCard(it) }
        groupTitle("晚上", visible.any { it.period in 8..9 })
        items(visible.filter { it.period in 8..9 }, key = { it.slotId }) { ScheduleCard(it) }
      }
    }
  }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupTitle(title: String, visible: Boolean) {
  if (visible) item { SectionHeader(title) }
}

@Composable
private fun ScheduleCard(slot: ScheduleSlot) {
  val period = SchedulePeriods.byPeriod(slot.period)
  val palette = listOf(Primary, StatusGreen, StatusOrange, StatusPurple, Color(0xFF0EA5E9))
  val color = palette[(slot.courseId.hashCode().absoluteValueCompat()) % palette.size]
  Row(
    modifier = Modifier
      .padding(horizontal = 16.dp)
      .fillMaxWidth()
      .background(Surface, RoundedCornerShape(12.dp))
      .border(BorderStroke(1.dp, Border), RoundedCornerShape(12.dp))
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
      Text(period.start, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
      Text(period.end, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(slot.courseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      Text("${slot.classroomName} · ${slot.teacherName}", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
    androidx.compose.foundation.layout.Box(Modifier.background(color, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp, vertical = 26.dp))
  }
}

private fun Int.absoluteValueCompat(): Int = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)
