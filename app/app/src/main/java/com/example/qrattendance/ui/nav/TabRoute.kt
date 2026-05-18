package com.example.qrattendance.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

// 底部导航条的四个 Tab：每项绑定中文文案与 Material Symbols 图标。
enum class TabRoute(val label: String, val icon: ImageVector) {
  Home("首页", Icons.Rounded.Home),
  Schedule("课表", Icons.Rounded.CalendarMonth),
  Records("考勤", Icons.Rounded.FactCheck),
  Profile("我的", Icons.Rounded.Person),
}
