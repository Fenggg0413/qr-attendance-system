package com.example.qrattendance.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qrattendance.data.AppContainer
import com.example.qrattendance.ui.auth.LoginScreen
import com.example.qrattendance.ui.auth.LoginViewModel
import com.example.qrattendance.ui.home.HomeScreen
import com.example.qrattendance.ui.home.HomeViewModel
import com.example.qrattendance.ui.leave.LeaveComposeScreen
import com.example.qrattendance.ui.leave.LeaveViewModel
import com.example.qrattendance.ui.profile.ProfileScreen
import com.example.qrattendance.ui.profile.ProfileViewModel
import com.example.qrattendance.ui.records.RecordsScreen
import com.example.qrattendance.ui.records.RecordsViewModel
import com.example.qrattendance.ui.scan.ScanScreen
import com.example.qrattendance.ui.scan.ScanViewModel
import com.example.qrattendance.ui.sessions.SessionsScreen
import com.example.qrattendance.ui.sessions.SessionsViewModel

@Composable
fun AppNavigation(container: AppContainer, modifier: Modifier = Modifier) {
  val session by container.sessionStore.sessions.collectAsStateWithLifecycle()
  val backStack = remember { mutableStateListOf<Any>(if (session == null) Login else HomeShell) }
  LaunchedEffect(session) {
    backStack.clear()
    backStack.add(if (session == null) Login else HomeShell)
  }
  when (val route = backStack.last()) {
    Login -> {
      val viewModel: LoginViewModel = viewModel(factory = container.loginViewModelFactory)
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      LoginScreen(state, viewModel::updateUsername, viewModel::updatePassword, viewModel::login, modifier)
    }
    HomeShell -> HomeShellScreen(container, onScan = { backStack.add(Scan) }, onLeave = { id, name -> backStack.add(LeaveCompose(id, name)) }, modifier = modifier)
    Scan -> {
      val viewModel: ScanViewModel = viewModel(factory = container.scanViewModelFactory)
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      ScanScreen(state, viewModel::updateManualPayload, viewModel::submitManualPayload, onBack = { backStack.removeLastOrNull() }, modifier = modifier, onPayloadDetected = viewModel::submitPayload)
    }
    is LeaveCompose -> {
      val viewModel: LeaveViewModel = viewModel(factory = container.leaveViewModelFactory)
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      LaunchedEffect(route.sessionId) { viewModel.prepare(route.sessionId, route.courseName) }
      LeaveComposeScreen(state, viewModel::updateReason, viewModel::submit, onBack = { backStack.removeLastOrNull() }, modifier = modifier)
    }
  }
}

@Composable
private fun HomeShellScreen(container: AppContainer, onScan: () -> Unit, onLeave: (Long, String) -> Unit, modifier: Modifier = Modifier) {
  var tab by remember { mutableStateOf(HomeTab.Home) }
  val transition = rememberInfiniteTransition(label = "fab_breath")
  val fabScale by transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "fab_scale")
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButtonPosition = FabPosition.Center,
    floatingActionButton = {
      Box(
        modifier =
          Modifier
            .offset(y = 28.dp)
            .size(64.dp)
            .scale(fabScale)
            .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
            .background(
              Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
              CircleShape,
            ),
      ) {
        FloatingActionButton(
          onClick = onScan,
          shape = CircleShape,
          containerColor = Color.Transparent,
          contentColor = Color.White,
          elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码签到", modifier = Modifier.size(28.dp))
        }
      }
    },
    bottomBar = { BottomTabBar(tab) { tab = it } },
  ) { innerPadding ->
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopStart) {
      AnimatedContent(
        targetState = tab,
        transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut() },
        label = "tab",
      ) { current ->
        when (current) {
          HomeTab.Home -> {
            val viewModel: HomeViewModel = viewModel(factory = container.homeViewModelFactory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.refresh() }
            HomeScreen(state, onScan, onLeave, viewModel::refresh, Modifier.fillMaxSize())
          }
          HomeTab.Sessions -> {
            val viewModel: SessionsViewModel = viewModel(factory = container.sessionsViewModelFactory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.refresh() }
            SessionsScreen(state, viewModel::setScope, viewModel::setStatusFilter, viewModel::refresh, onLeave)
          }
          HomeTab.Records -> {
            val viewModel: RecordsViewModel = viewModel(factory = container.recordsViewModelFactory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.refresh() }
            RecordsScreen(state, viewModel::setFilter, viewModel::refresh)
          }
          HomeTab.Profile -> {
            val viewModel: ProfileViewModel = viewModel(factory = container.profileViewModelFactory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ProfileScreen(
              state,
              viewModel::updateDisplayName,
              viewModel::updateCurrentPassword,
              viewModel::updateNewPassword,
              viewModel::saveProfile,
              viewModel::changePassword,
              viewModel::logout,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BottomTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 4.dp,
    shadowElevation = 8.dp,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
  ) {
    NavigationBar(
      containerColor = Color.Transparent,
      tonalElevation = 0.dp,
      modifier = Modifier.fillMaxWidth().height(80.dp),
    ) {
      val left = listOf(HomeTab.Home, HomeTab.Sessions)
      val right = listOf(HomeTab.Records, HomeTab.Profile)
      left.forEach { TabItem(it, selected == it) { onSelect(it) } }
      Spacer(Modifier.width(72.dp))
      right.forEach { TabItem(it, selected == it) { onSelect(it) } }
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(item: HomeTab, isSelected: Boolean, onClick: () -> Unit) {
  NavigationBarItem(
    selected = isSelected,
    onClick = onClick,
    icon = { Icon(item.icon(isSelected), contentDescription = item.label) },
    label = { Text(item.label) },
    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
  )
}

private fun HomeTab.icon(selected: Boolean) =
  when (this) {
    HomeTab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    HomeTab.Sessions -> if (selected) Icons.Filled.Schedule else Icons.Outlined.Schedule
    HomeTab.Records -> if (selected) Icons.AutoMirrored.Filled.EventNote else Icons.AutoMirrored.Outlined.EventNote
    HomeTab.Profile -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
  }
