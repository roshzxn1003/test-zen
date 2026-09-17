package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrCameraView(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    if (hasCameraPermission) {
        val hasScanned = remember { AtomicBoolean(false) }
        val isDisposed = remember { AtomicBoolean(false) }

        DisposableEffect(lifecycleOwner) {
            onDispose {
                isDisposed.set(true)
                try {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    if (cameraProviderFuture.isDone) {
                        cameraProviderFuture.get().unbindAll()
                    }
                } catch (e: Exception) {
                    // Ignore disposal cleanup errors
                }
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()
                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )

                    cameraProviderFuture.addListener({
                        if (isDisposed.get()) {
                            executor.shutdown()
                            scanner.close()
                            return@addListener
                        }
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                if (isDisposed.get() || hasScanned.get()) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            if (!isDisposed.get() && !hasScanned.get()) {
                                                for (barcode in barcodes) {
                                                    val raw = barcode.rawValue ?: barcode.displayValue
                                                    if (!raw.isNullOrBlank() && hasScanned.compareAndSet(false, true)) {
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrScanned(raw)
                                                        }
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        .addOnFailureListener {
                                            // Frame analysis failure handled gracefully
                                        }
                                        .addOnCompleteListener {
                                            try {
                                                imageProxy.close()
                                            } catch (e: Exception) {
                                                // Safe close
                                            }
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()

                            val cameraSelector = when {
                                cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                else -> null
                            }

                            if (cameraSelector != null) {
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                activeCamera = camera
                            } else {
                                Toast.makeText(ctx, "No camera found on this device.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Animated Viewfinder Reticle Overlay
            val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
            val scanLinePos by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scan_line"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = size.width * 0.72f
                val boxHeight = boxWidth
                val left = (size.width - boxWidth) / 2f
                val top = (size.height - boxHeight) / 2.3f

                // Translucent dark mask outside viewfinder
                drawRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    size = size
                )

                // Clear transparent window inside viewfinder
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    blendMode = BlendMode.Clear
                )

                // Viewfinder border
                drawRoundRect(
                    color = EmeraldDarkPrimary.copy(alpha = 0.6f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Corner accents
                val cornerLen = 28.dp.toPx()
                val cornerStroke = 4.dp.toPx()
                val cornerColor = EmeraldDarkPrimary

                // Top-Left
                drawLine(cornerColor, Offset(left - 2, top + cornerLen), Offset(left - 2, top), cornerStroke)
                drawLine(cornerColor, Offset(left - 2, top), Offset(left + cornerLen, top), cornerStroke)

                // Top-Right
                drawLine(cornerColor, Offset(left + boxWidth + 2, top + cornerLen), Offset(left + boxWidth + 2, top), cornerStroke)
                drawLine(cornerColor, Offset(left + boxWidth + 2, top), Offset(left + boxWidth - cornerLen, top), cornerStroke)

                // Bottom-Left
                drawLine(cornerColor, Offset(left - 2, top + boxHeight - cornerLen), Offset(left - 2, top + boxHeight), cornerStroke)
                drawLine(cornerColor, Offset(left - 2, top + boxHeight), Offset(left + cornerLen, top + boxHeight), cornerStroke)

                // Bottom-Right
                drawLine(cornerColor, Offset(left + boxWidth + 2, top + boxHeight - cornerLen), Offset(left + boxWidth + 2, top + boxHeight), cornerStroke)
                drawLine(cornerColor, Offset(left + boxWidth + 2, top + boxHeight), Offset(left + boxWidth - cornerLen, top + boxHeight), cornerStroke)

                // Animated Laser scan line
                val laserY = top + (boxHeight * scanLinePos)
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            EmeraldDarkPrimary.copy(alpha = 0.9f),
                            PastelCyan,
                            EmeraldDarkPrimary.copy(alpha = 0.9f),
                            Color.Transparent
                        ),
                        startX = left,
                        endX = left + boxWidth
                    ),
                    start = Offset(left + 6.dp.toPx(), laserY),
                    end = Offset(left + boxWidth - 6.dp.toPx(), laserY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            // Top-right Torch Toggle Button
            IconButton(
                onClick = {
                    val cam = activeCamera
                    if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                        isTorchOn = !isTorchOn
                        cam.cameraControl.enableTorch(isTorchOn)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flashlight",
                    tint = if (isTorchOn) Color(0xFFFBBF24) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SlateDarkSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(EmeraldDarkPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = EmeraldDarkPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "To scan any UPI QR code instantly, please enable camera access.",
                    fontSize = 13.sp,
                    color = SlateDarkTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
