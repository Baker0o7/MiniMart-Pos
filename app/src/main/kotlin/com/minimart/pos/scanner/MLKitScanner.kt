package com.minimart.pos.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen camera composable that uses CameraX + ML Kit for real-time barcode scanning.
 * Fires [onBarcodeDetected] once per unique barcode, debounced to avoid duplicates.
 */
@Composable
fun BarcodeScannerView(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastScanned by remember { mutableStateOf("") }
    var lastScannedTime by remember { mutableLongStateOf(0L) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            setupCamera(ctx, previewView) { barcode ->
                val now = System.currentTimeMillis()
                // Debounce: ignore same barcode within 2 seconds
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
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
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
                previewView.context as androidx.lifecycle.LifecycleOwner,
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

// ─── Viewfinder overlay UI ────────────────────────────────────────────────────

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Dimmed corners
        Box(
            modifier = Modifier
                .fillMaxSize()
        )
        // Scan window
        Box(
            modifier = Modifier
                .size(260.dp, 160.dp)
                .align(Alignment.Center)
        ) {
            // Corner brackets
            CornerBracket(Alignment.TopStart)
            CornerBracket(Alignment.TopEnd)
            CornerBracket(Alignment.BottomStart)
            CornerBracket(Alignment.BottomEnd)
        }
        // Label
        Text(
            text = "Point at barcode",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 200.dp)
        )
    }
}

@Composable
private fun BoxScope.CornerBracket(alignment: Alignment) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .align(alignment),
        color = Color.Transparent,
        shape = RoundedCornerShape(4.dp)
    ) {
        // White L-shaped bracket via Canvas
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            val color = Color.White
            val len = 32.dp.toPx()
            when (alignment) {
                Alignment.TopStart -> {
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(len, 0f), strokeWidth = 4.dp.toPx())
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, len), strokeWidth = 4.dp.toPx())
                }
                Alignment.TopEnd -> {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - len, 0f), strokeWidth = 4.dp.toPx())
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, len), strokeWidth = 4.dp.toPx())
                }
                Alignment.BottomStart -> {
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(len, size.height), strokeWidth = 4.dp.toPx())
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - len), strokeWidth = 4.dp.toPx())
                }
                else -> {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - len, size.height), strokeWidth = 4.dp.toPx())
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - len), strokeWidth = 4.dp.toPx())
                }
            }
        }
    }
}
