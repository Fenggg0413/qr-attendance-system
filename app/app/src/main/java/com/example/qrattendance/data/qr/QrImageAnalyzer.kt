package com.example.qrattendance.data.qr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrImageAnalyzer(private val onPayload: (String) -> Unit) : ImageAnalysis.Analyzer {
  private val scanner = BarcodeScanning.getClient()
  private var locked = false

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    if (locked) {
      imageProxy.close()
      return
    }
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
      .addOnSuccessListener { barcodes ->
        barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let {
          locked = true
          onPayload(it)
        }
      }
      .addOnCompleteListener { imageProxy.close() }
  }
}
