package com.example.qrattendance.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.Format
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.ui.components.Banner
import com.example.qrattendance.ui.components.EmptyState
import com.example.qrattendance.ui.components.QuickStatCard
import com.example.qrattendance.ui.components.SectionHeader
import com.example.qrattendance.ui.components.StatusBadge
import com.example.qrattendance.ui.components.TopBar
import com.example.qrattendance.ui.schedule.SchedulePeriods
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.StatusGreen
import com.example.qrattendance.ui.theme.StatusOrange
import com.example.qrattendance.ui.theme.StatusRed
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextSecondary

@Composable
fun HomeScreen(onOpenScan: () -> Unit) {
  val container = LocalContainer.current
  val vm = remember { HomeViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  LaunchedEffect(Unit) { vm.load() }
  Column(modifier = Modifier.fillMaxSize().background(Background)) {
    TopBar("", Format.greeting(), container.sessionStore.current()?.displayName ?: "学生")
    if (state.loading) {
      Spacer(Modifier.height(40.dp))
      CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    } else if (state.error != null) {
      Spacer(Modifier.height(40.dp))
      EmptyState(state.error ?: "加载失败")
    } else {
      val dashboard = state.dashboard
      LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          Banner(
            title = "${container.sessionStore.current()?.displayName ?: "同学"}，加油！",
            subtitle = "本学期考勤概况",
            stats = listOf(
              Format.percent(dashboard?.semesterAttendanceRate ?: 0.0) to "出勤率",
              "${dashboard?.absentCount ?: 0}" to "缺勤次数",
            ),
            modifier = Modifier.padding(16.dp),
          )
        }
        item {
          Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              QuickStatCard("${dashboard?.todayCount ?: 0}", "今日课程", Primary, Icons.Rounded.CalendarMonth, Modifier.weight(1f))
              QuickStatCard("${dashboard?.checkedInCount ?: 0}", "已签到", StatusGreen, Icons.Rounded.CheckCircle, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              QuickStatCard("${dashboard?.pendingLeaveCount ?: 0}", "待确认", StatusOrange, Icons.Rounded.Schedule, Modifier.weight(1f))
              QuickStatCard("${dashboard?.absentCount ?: 0}", "缺勤", StatusRed, Icons.Rounded.Error, Modifier.weight(1f))
            }
          }
        }
        item { SectionHeader("今日课程") }
        val sessions = dashboard?.todaySessions.orEmpty()
        if (sessions.isEmpty()) item { EmptyState("今天暂无课程") } else items(sessions, key = { it.itemKey() }) { session ->
          TodaySessionRow(session, onOpenScan, Modifier.padding(horizontal = 16.dp))
        }
        item { Spacer(Modifier.height(12.dp)) }
      }
    }
  }
}

@Composable
private fun TodaySessionRow(session: TodaySession, onOpenScan: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(Surface, RoundedCornerShape(12.dp))
      .border(BorderStroke(1.dp, Border), RoundedCornerShape(12.dp))
      .clickable(enabled = session.recordStatus.isBlank()) { onOpenScan() }
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(session.courseName, style = MaterialTheme.typography.bodyMedium)
      Text("${session.timeLabel()} · ${session.classroomName.ifBlank { "未设置教室" }}", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
    StatusBadge(session.recordStatus.ifBlank { session.status })
  }
}

private fun TodaySession.itemKey(): String =
  if (slotId > 0) "slot-$slotId" else "session-$id"

private fun TodaySession.timeLabel(): String {
  if (period > 0) {
    val slot = SchedulePeriods.byPeriod(period)
    return "${slot.start}-${slot.end}"
  }
  return Format.compactDateTime(startedAt)
}
