package com.minimart.pos.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen camera composable using CameraX + ML Kit barcode scanning.
 * Accepts an explicit [lifecycleOwner] so it works correctly inside Dialogs.
 */
@Composable
fun BarcodeScannerView(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var lastScanned by remember { mutableStateOf("") }
    var lastScannedTime by remember { mutableLongStateOf(0L) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            setupCamera(ctx, previewView, lifecycleOwner) { barcode ->
                val now = System.currentTimeMillis()
                if (barcode != lastScanned || now - lastScannedTime > 2000L) {
                    lastScanned = barcode
                    lastScannedTime = now
                    onBarcodeDetected(barcode)
                }
            }
            previewView
        }
    )
}

private fun setupCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    onBarcodeDetected: (String) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE
            ).build()

        val barcodeScanner = BarcodeScanning.getClient(options)
        val analysisExecutor = Executors.newSingleThreadExecutor()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processImageProxy(barcodeScanner, imageProxy, onBarcodeDetected)
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,   // use passed-in owner, not view context cast
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e("BarcodeScanner", "Camera bind failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let { onBarcodeDetected(it) }
        }
        .addOnCompleteListener { imageProxy.close() }
}

// ─── Viewfinder overlay ───────────────────────────────────────────────────────

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.size(260.dp, 160.dp).align(Alignment.Center)) {
            val stroke = Stroke(width = 4.dp.toPx())
            val len = 32.dp.toPx()
            val w = size.width; val h = size.height
            listOf(
                Offset(0f, 0f) to Offset(len, 0f), Offset(0f, 0f) to Offset(0f, len),
                Offset(w, 0f) to Offset(w - len, 0f), Offset(w, 0f) to Offset(w, len),
                Offset(0f, h) to Offset(len, h), Offset(0f, h) to Offset(0f, h - len),
                Offset(w, h) to Offset(w - len, h), Offset(w, h) to Offset(w, h - len)
            ).forEach { (start, end) -> drawLine(Color.White, start, end, strokeWidth = 4.dp.toPx()) }
        }
    }
}
