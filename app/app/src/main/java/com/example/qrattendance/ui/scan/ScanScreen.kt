package com.example.qrattendance.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.qrattendance.scanner.QrImageAnalyzer

@Composable
fun ScanScreen(
  state: ScanUiState,
  onPayloadChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  onPayloadDetected: (String) -> Unit = {},
) {
  val transition = rememberInfiniteTransition(label = "scan")
  val scale by transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale")
  val context = LocalContext.current
  var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission = granted }
  Box(modifier.fillMaxSize().background(Color.Black)) {
    if (hasCameraPermission) {
      CameraPreview(onPayloadDetected)
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Button(onClick = onBack) { Text("返回") }
        Button(onClick = { if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(if (hasCameraPermission) "闪光灯" else "相机") }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(280.dp).scale(scale).background(Color.Transparent, RoundedCornerShape(28.dp))) {
          Box(Modifier.matchParentSize().padding(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(24.dp)))
        }
        Text("将二维码放入取景框", color = Color.White, modifier = Modifier.padding(top = 18.dp))
      }
      Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("无法扫描？手动输入", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(state.manualPayload, onPayloadChange, Modifier.fillMaxWidth(), label = { Text("qr-attendance://...") })
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(enabled = !state.loading, onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "提交中" else "提交签到") }
      }
    }
  }
}

@Composable
private fun CameraPreview(onPayloadDetected: (String) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxSize()) { previewView ->
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
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
      },
      ContextCompat.getMainExecutor(context),
    )
  }
}
