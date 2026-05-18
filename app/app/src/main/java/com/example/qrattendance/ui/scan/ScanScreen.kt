package com.example.qrattendance.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qrattendance.core.LocalContainer
import com.example.qrattendance.data.qr.QrImageAnalyzer
import com.example.qrattendance.ui.theme.Primary
import com.example.qrattendance.ui.theme.PrimaryLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

// 扫码页：CameraX PreviewView + ImageAnalysis 把帧送入 QrImageAnalyzer；扫到合法 QR 后委托 ScanViewModel.onPayload 完成签到。
// 相机生命周期绑定到 LocalLifecycleOwner，Composable 离开时自动 unbind 释放相机硬件。
@Composable
fun ScanScreen(onClose: () -> Unit) {
  val context = LocalContext.current
  val container = LocalContainer.current
  val vm = remember { ScanViewModel(container.api) }
  val state by vm.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(state.success) {
    if (state.success) {
      val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
      if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vibrator?.vibrate(80)
      delay(800)
      onClose()
    }
  }
  Box(Modifier.fillMaxSize().background(Color.Black)) {
    if (hasPermission) CameraPreview { raw -> scope.launch { vm.onPayload(raw) } } else PermissionPrompt { launcher.launch(Manifest.permission.CAMERA) }
    ScanChrome(message = state.message, onClose = onClose)
  }
}

@Composable
private fun CameraPreview(onPayload: (String) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val executor = remember { Executors.newSingleThreadExecutor() }
  AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxSize()) { previewView ->
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
      val provider = providerFuture.get()
      val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
      val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
        it.setAnalyzer(executor, QrImageAnalyzer(onPayload))
      }
      provider.unbindAll()
      provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }, ContextCompat.getMainExecutor(context))
  }
  DisposableEffect(Unit) { onDispose { executor.shutdown() } }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text("需要相机权限才能扫码", color = Color.White)
    Button(onClick = onRequest, modifier = Modifier.padding(top = 12.dp)) { Text("授权相机") }
  }
}

@Composable
private fun ScanChrome(message: String, onClose: () -> Unit) {
  val transition = rememberInfiniteTransition(label = "laser")
  val y by transition.animateFloat(12f, 204f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "laserY")
  Column(Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(Modifier.align(Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose, modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)) {
        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
      }
      Text("扫码签到", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
    }
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Box(Modifier.size(220.dp)) {
        Canvas(Modifier.fillMaxSize()) {
          val stroke = 3.dp.toPx()
          val len = 24.dp.toPx()
          fun corner(a: Offset, b: Offset) = drawLine(Color.White, a, b, stroke, StrokeCap.Square)
          corner(Offset.Zero, Offset(len, 0f)); corner(Offset.Zero, Offset(0f, len))
          corner(Offset(size.width, 0f), Offset(size.width - len, 0f)); corner(Offset(size.width, 0f), Offset(size.width, len))
          corner(Offset(0f, size.height), Offset(len, size.height)); corner(Offset(0f, size.height), Offset(0f, size.height - len))
          corner(Offset(size.width, size.height), Offset(size.width - len, size.height)); corner(Offset(size.width, size.height), Offset(size.width, size.height - len))
        }
        Box(
          Modifier
            .offset(y = y.dp)
            .padding(horizontal = 4.dp)
            .size(width = 212.dp, height = 2.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, PrimaryLight, Primary, PrimaryLight, Color.Transparent))),
        )
      }
    }
    Text(message, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("扫码签到", color = Color.White, modifier = Modifier.background(Primary, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 8.dp))
    }
  }
}
