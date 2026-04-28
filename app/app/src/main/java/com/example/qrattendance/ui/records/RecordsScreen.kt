package com.example.qrattendance.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qrattendance.core.Format
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.ui.components.EmptyState
import com.example.qrattendance.ui.components.FilterChips
import com.example.qrattendance.ui.components.SectionHeader
import com.example.qrattendance.ui.components.StatusBadge
import com.example.qrattendance.ui.components.TopBar
import com.example.qrattendance.ui.theme.Background
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.StatusGreen
import com.example.qrattendance.ui.theme.StatusOrange
import com.example.qrattendance.ui.theme.StatusPurple
import com.example.qrattendance.ui.theme.StatusRed
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextSecondary
import java.time.LocalDate

@Composable
fun RecordsScreen() {
  val container = LocalContainer.current
  val vm = remember { RecordsViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  val visible by vm.visibleRecords.collectAsState()
  LaunchedEffect(Unit) { vm.load() }
  Column(modifier = Modifier.fillMaxSize().background(Background)) {
    TopBar("考勤记录", "本学期明细", container.sessionStore.current()?.displayName ?: "学生")
    SummaryCard(state.records)
    FilterChips(
      items = RecordFilter.entries.map { it.label },
      selected = state.filter.label,
      onSelected = { label -> vm.setFilter(RecordFilter.entries.first { it.label == label }) },
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (visible.isEmpty()) item { EmptyState("暂无考勤记录") }
      visible.groupBy { labelFor(it.checkedInAt) }.forEach { (label, records) ->
        item { SectionHeader(label) }
        items(records, key = { it.id }) { RecordRow(it) }
      }
    }
  }
}

@Composable
private fun SummaryCard(records: List<AttendanceRecord>) {
  val present = records.count { it.status == "PRESENT" }
  val absent = records.count { it.status == "ABSENT" }
  val late = records.count { it.status == "LATE" }
  val excused = records.count { it.status == "EXCUSED" }
  val total = records.size.coerceAtLeast(1)
  val rate = (present + late).toFloat() / total
  Column(
    Modifier.padding(16.dp).fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).border(BorderStroke(1.dp, Border), RoundedCornerShape(14.dp)).padding(16.dp),
  ) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      SummaryItem("$present", "出勤", StatusGreen)
      SummaryItem("$absent", "缺勤", StatusRed)
      SummaryItem("$late", "迟到", StatusOrange)
      SummaryItem("$excused", "请假", StatusPurple)
    }
    Text("出勤率 ${Format.percent(rate.toDouble())}", color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).background(Background, RoundedCornerShape(3.dp))) {
      Box(Modifier.fillMaxWidth(rate).height(6.dp).background(Primary, RoundedCornerShape(3.dp)))
    }
  }
}

@Composable
private fun SummaryItem(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
  Column {
    Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun RecordRow(record: AttendanceRecord) {
  Row(
    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Surface, RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, Border), RoundedCornerShape(12.dp)).padding(14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(Modifier.weight(1f)) {
      Text(record.courseName, style = MaterialTheme.typography.bodyMedium)
      Text(Format.compactDateTime(record.checkedInAt), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
    StatusBadge(record.status)
  }
}

private fun labelFor(value: String?): String {
  val date = runCatching { LocalDate.parse(value?.substring(0, 10)) }.getOrNull() ?: return "未记录日期"
  return Format.recordDateLabel(date)
}
