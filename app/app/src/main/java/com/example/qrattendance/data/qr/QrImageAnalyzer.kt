package com.example.qrattendance.data.qr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

// CameraX 帧分析器：把每帧画面送入 ML Kit 扫码，一旦发现 QR 码就回调 onPayload 并锁定不再触发。
class QrImageAnalyzer(private val onPayload: (String) -> Unit) : ImageAnalysis.Analyzer {
  private val scanner = BarcodeScanning.getClient()
  // 帧锁：QR Token 在同一时间桶内可能被反复扫到，识别成功后停止处理后续帧，避免重复回调引发重复签到。
  private var locked = false

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    // 已识别过：直接释放当前帧（必须 close 否则相机缓冲耗尽后会停止送帧）。
    if (locked) {
      imageProxy.close()
      return
    }
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }
    // 把 ImageProxy 包成 ML Kit 的 InputImage；rotationDegrees 透传保证横竖屏旋转后画面也能识别。
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
      .addOnSuccessListener { barcodes ->
        // 只接受 QR 码，过滤掉一维条码等其他格式。
        barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let {
          locked = true
          onPayload(it)
        }
      }
      // 不论成功/失败都要在 onComplete 内 close，否则相机缓冲耗尽后会停止送帧。
      .addOnCompleteListener { imageProxy.close() }
  }
}
