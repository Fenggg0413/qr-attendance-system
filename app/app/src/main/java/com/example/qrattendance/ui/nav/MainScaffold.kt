package com.example.qrattendance.ui.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.qrattendance.ui.home.HomeScreen
import com.example.qrattendance.ui.leave.LeaveComposeScreen
import com.example.qrattendance.ui.leave.LeaveListScreen
import com.example.qrattendance.ui.profile.ProfileScreen
import com.example.qrattendance.ui.records.RecordsScreen
import com.example.qrattendance.ui.scan.ScanScreen
import com.example.qrattendance.ui.schedule.ScheduleScreen
import com.example.qrattendance.ui.theme.Border
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.PrimaryLight
import com.example.qrattendance.ui.theme.Surface
import com.example.qrattendance.ui.theme.TextTertiary

private enum class OverlayRoute { None, LeaveList, LeaveCompose, Scan }

@Composable
fun MainScaffold(onLogout: () -> Unit) {
  var selected by remember { mutableStateOf(TabRoute.Home) }
  var overlay by remember { mutableStateOf(OverlayRoute.None) }

  Box(modifier = Modifier.fillMaxSize().background(com.example.qrattendance.ui.theme.Background)) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 68.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
      when (selected) {
        TabRoute.Home -> HomeScreen(onOpenScan = { overlay = OverlayRoute.Scan })
        TabRoute.Schedule -> ScheduleScreen()
        TabRoute.Records -> RecordsScreen()
        TabRoute.Profile -> ProfileScreen(
          onOpenLeave = { overlay = OverlayRoute.LeaveList },
          onLogout = onLogout,
        )
      }
    }
    BottomNav(
      selected = selected,
      onSelected = { selected = it },
      onScan = { overlay = OverlayRoute.Scan },
      modifier = Modifier.align(Alignment.BottomCenter),
    )
    when (overlay) {
      OverlayRoute.None -> Unit
      OverlayRoute.LeaveList -> LeaveListScreen(onBack = { overlay = OverlayRoute.None }, onCompose = { overlay = OverlayRoute.LeaveCompose })
      OverlayRoute.LeaveCompose -> LeaveComposeScreen(onBack = { overlay = OverlayRoute.LeaveList })
      OverlayRoute.Scan -> ScanScreen(onClose = { overlay = OverlayRoute.None })
    }
  }
}

@Composable
private fun BottomNav(selected: TabRoute, onSelected: (TabRoute) -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(68.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        .background(Surface)
        .border(BorderStroke(1.dp, Border))
        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TabButton(TabRoute.Home, selected == TabRoute.Home, onSelected, Modifier.weight(1f))
      TabButton(TabRoute.Schedule, selected == TabRoute.Schedule, onSelected, Modifier.weight(1f))
      Spacer(Modifier.weight(1f))
      TabButton(TabRoute.Records, selected == TabRoute.Records, onSelected, Modifier.weight(1f))
      TabButton(TabRoute.Profile, selected == TabRoute.Profile, onSelected, Modifier.weight(1f))
    }
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .offset(y = (-22).dp)
        .size(64.dp)
        .clip(CircleShape)
        .background(Surface)
        .padding(4.dp)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Primary, PrimaryLight)))
        .clickable { onScan() },
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.QrCodeScanner, contentDescription = "扫码签到", tint = Color.White, modifier = Modifier.size(26.dp))
    }
  }
}

@Composable
private fun TabButton(route: TabRoute, active: Boolean, onSelected: (TabRoute) -> Unit, modifier: Modifier = Modifier) {
  val color = if (active) Primary else TextTertiary
  Column(
    modifier = modifier.clickable { onSelected(route) },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(route.icon, contentDescription = route.label, tint = color, modifier = Modifier.size(22.dp))
    Text(route.label, color = color, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
  }
}
