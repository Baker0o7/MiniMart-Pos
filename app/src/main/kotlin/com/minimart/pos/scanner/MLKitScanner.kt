package com.minimart.pos.scanner

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

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
        factory = { ctx ->
            PreviewView(ctx).also { pv ->
                startCamera(ctx, pv, lifecycleOwner) { barcode ->
                    val now = System.currentTimeMillis()
                    if (barcode != lastScanned || now - lastScannedTime > 1500) {
                        lastScanned = barcode; lastScannedTime = now
                        onBarcodeDetected(barcode)
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun startCamera(
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
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX
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
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview, imageAnalysis
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

// ─── Animated scanner overlay ──────────────────────────────────────────────────

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier, label: String = "Point at barcode") {
    // Animated laser line
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "laserY"
    )
    // Corner pulse
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pw = size.width; val ph = size.height

            // Dark vignette around the scan area
            val boxW = pw * 0.85f; val boxH = ph * 0.55f
            val bx = (pw - boxW) / 2f; val by = (ph - boxH) / 2f

            // Semi-transparent overlay outside the scan box
            drawRect(Color.Black.copy(0.55f), Offset.Zero, Size(pw, by))
            drawRect(Color.Black.copy(0.55f), Offset(0f, by + boxH), Size(pw, ph - by - boxH))
            drawRect(Color.Black.copy(0.55f), Offset(0f, by), Size(bx, boxH))
            drawRect(Color.Black.copy(0.55f), Offset(bx + boxW, by), Size(pw - bx - boxW, boxH))

            // Scan box border (rounded rect outline)
            drawRoundRect(
                color = Color.White.copy(pulse),
                topLeft = Offset(bx, by),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Animated teal laser line
            val laserPosY = by + laserY * boxH
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF00C9A7), Color(0xFF00C9A7), Color.Transparent),
                    startX = bx, endX = bx + boxW
                ),
                start = Offset(bx, laserPosY),
                end = Offset(bx + boxW, laserPosY),
                strokeWidth = 2.5.dp.toPx()
            )

            // Corner brackets (teal)
            val len = 28.dp.toPx()
            val r = 8.dp.toPx()
            val strokeW = 3.5.dp.toPx()
            val corners = listOf(
                Offset(bx, by) to Pair(1f, 1f),
                Offset(bx + boxW, by) to Pair(-1f, 1f),
                Offset(bx, by + boxH) to Pair(1f, -1f),
                Offset(bx + boxW, by + boxH) to Pair(-1f, -1f)
            )
            corners.forEach { (corner, dir) ->
                val (dx, dy) = dir
                drawLine(Color(0xFF00C9A7), corner, Offset(corner.x + dx * len, corner.y), strokeWidth = strokeW)
                drawLine(Color(0xFF00C9A7), corner, Offset(corner.x, corner.y + dy * len), strokeWidth = strokeW)
            }
        }

        // Bottom label
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(0.6f))
            .padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(label, color = Color(0xFF00C9A7), fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}
