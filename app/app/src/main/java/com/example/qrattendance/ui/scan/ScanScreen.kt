package com.example.qrattendance.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Window
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.qrattendance.scanner.QrImageAnalyzer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
  state: ScanUiState,
  onPayloadChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onPayloadDetected: (String) -> Unit = {},
) {
  val context = LocalContext.current
  val window = context.findActivity()?.window
  DisposableEffect(window) {
    window?.enterScannerMode()
    onDispose { window?.leaveScannerMode() }
  }

  var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
  var torchEnabled by remember { mutableStateOf(false) }
  var camera by remember { mutableStateOf<Camera?>(null) }
  var showManualSheet by remember { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission = granted }
  val transition = rememberInfiniteTransition(label = "scan")
  val lineOffset by transition.animateFloat(0f, 276f, infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "line")
  val successScale by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (state.success == null) 1f else 0.85f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "successScale",
  )

  LaunchedEffect(state.success) {
    if (state.success != null) context.vibrateOnce()
  }

  Box(modifier.fillMaxSize().background(Color.Black)) {
    if (hasCameraPermission) {
      CameraPreview(onPayloadDetected, onCameraBound = { camera = it })
    }
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.80f)))
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ScannerIconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White) }
        ScannerIconButton(
          onClick = {
            if (!hasCameraPermission) {
              permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
              torchEnabled = !torchEnabled
              camera?.cameraControl?.enableTorch(torchEnabled)
            }
          },
        ) {
          Icon(if (torchEnabled) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn, contentDescription = "闪光灯", tint = Color.White)
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
          Modifier
            .size(280.dp)
            .scale(successScale)
            .scannerCorners(MaterialTheme.colorScheme.primary)
            .testTag("scan_viewfinder"),
        ) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(4.dp)
              .offset(y = lineOffset.dp)
              .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.primary, Color.Transparent))),
          )
          if (state.success != null) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp).align(Alignment.Center))
          }
        }
        Text("将二维码放入取景框", color = Color.White, modifier = Modifier.padding(top = 18.dp))
      }
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
        TextButton(onClick = { showManualSheet = true }) { Text("无法扫描？手动输入", color = Color.White) }
      }
    }

    state.success?.let {
      Card(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("签到成功", style = MaterialTheme.typography.titleLarge)
          Text("打卡时间已记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("确定") }
        }
      }
    }
  }

  if (showManualSheet) {
    ModalBottomSheet(onDismissRequest = { showManualSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("手动输入", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(state.manualPayload, onPayloadChange, Modifier.fillMaxWidth(), label = { Text("qr-attendance://...") })
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(enabled = !state.loading, onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "提交中" else "提交签到") }
      }
    }
  }
}

@Composable
private fun ScannerIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.16f), CircleShape)) { content() }
}

private fun Modifier.scannerCorners(color: Color): Modifier =
  drawBehind {
    val length = 28.dp.toPx()
    val stroke = 4.dp.toPx()
    val half = stroke / 2f
    fun corner(x: Float, y: Float, sx: Float, sy: Float) {
      drawLine(color, start = androidx.compose.ui.geometry.Offset(x, y), end = androidx.compose.ui.geometry.Offset(x + sx * length, y), strokeWidth = stroke, cap = StrokeCap.Square)
      drawLine(color, start = androidx.compose.ui.geometry.Offset(x, y), end = androidx.compose.ui.geometry.Offset(x, y + sy * length), strokeWidth = stroke, cap = StrokeCap.Square)
    }
    drawRect(Color.Transparent, style = Stroke(stroke))
    corner(half, half, 1f, 1f)
    corner(size.width - half, half, -1f, 1f)
    corner(half, size.height - half, 1f, -1f)
    corner(size.width - half, size.height - half, -1f, -1f)
  }

@Composable
private fun CameraPreview(onPayloadDetected: (String) -> Unit, onCameraBound: (Camera) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxSize().testTag("camera_preview")) { previewView ->
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
      {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis =
          ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), QrImageAnalyzer(onPayloadDetected)) }
        cameraProvider.unbindAll()
        onCameraBound(cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
      },
      ContextCompat.getMainExecutor(context),
    )
  }
}

private fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

private fun Window.enterScannerMode() {
  WindowCompat.setDecorFitsSystemWindows(this, false)
  statusBarColor = AndroidColor.TRANSPARENT
}

private fun Window.leaveScannerMode() {
  WindowCompat.setDecorFitsSystemWindows(this, true)
}

private fun Context.vibrateOnce() {
  val vibrator = getSystemService(Vibrator::class.java) ?: return
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
  } else {
    @Suppress("DEPRECATION")
    vibrator.vibrate(50)
  }
}
