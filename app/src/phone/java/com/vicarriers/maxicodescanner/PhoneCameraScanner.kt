package com.vicarriers.maxicodescanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal class PhoneCameraScanner(
    private val activity: ComponentActivity,
) {
    @Composable
    fun Content(
        requestPermission: Boolean,
        onScan: (PhoneLabelScan) -> Unit,
        onPermissionRequestConsumed: () -> Unit,
        onPermissionFlowFinished: (Boolean) -> Unit,
        onSystemFlowActiveChange: (Boolean) -> Unit,
        onClose: () -> Unit,
    ) {
        BackHandler(onBack = onClose)
        var permissionGranted by remember {
            mutableStateOf(
                activity.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        var permissionPermanentlyDenied by remember { mutableStateOf(false) }
        val permissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                onSystemFlowActiveChange(false)
                permissionGranted = granted
                permissionPermanentlyDenied =
                    !granted &&
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                onPermissionFlowFinished(granted)
            }
        val settingsLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                onSystemFlowActiveChange(false)
                permissionGranted =
                    activity.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                permissionPermanentlyDenied = !permissionGranted
                onPermissionFlowFinished(permissionGranted)
            }

        LaunchedEffect(requestPermission) {
            if (requestPermission) {
                onPermissionRequestConsumed()
            }
            if (requestPermission && !permissionGranted) {
                onSystemFlowActiveChange(true)
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        if (permissionGranted) {
            CameraScreen(onScan = onScan, onClose = onClose)
        } else {
            PermissionScreen(
                requestPermission = {
                    if (permissionPermanentlyDenied) {
                        onSystemFlowActiveChange(true)
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${activity.packageName}"),
                            ),
                        )
                    } else {
                        onSystemFlowActiveChange(true)
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                permissionPermanentlyDenied = permissionPermanentlyDenied,
                onClose = onClose,
            )
        }
    }

    @Composable
    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalMaterial3Api::class)
    private fun CameraScreen(
        onScan: (PhoneLabelScan) -> Unit,
        onClose: () -> Unit,
    ) {
        var camera by remember { mutableStateOf<Camera?>(null) }
        var torchEnabled by remember { mutableStateOf(false) }
        var cameraError by remember { mutableStateOf<String?>(null) }

        DisposableEffect(camera) {
            onDispose {
                camera?.cameraControl?.enableTorch(false)
            }
        }

        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Scan label") },
                    navigationIcon = {
                        TextButton(
                            onClick = onClose,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text("Back")
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = camera?.cameraInfo?.hasFlashUnit() == true,
                            onClick = {
                                val next = !torchEnabled
                                camera?.cameraControl?.enableTorch(next)?.addListener(
                                    { torchEnabled = next },
                                    mainExecutor(activity),
                                )
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.38f),
                                ),
                        ) {
                            Text(if (torchEnabled) "Torch off" else "Torch")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        ),
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreview(
                        onScan = onScan,
                        onCameraBound = { boundCamera -> camera = boundCamera },
                        onError = { message -> cameraError = message },
                    )
                    ScanWindowOverlay()
                    cameraError?.let { message ->
                        CameraError(message = message, onClose = onClose)
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionScreen(
        requestPermission: () -> Unit,
        permissionPermanentlyDenied: Boolean,
        onClose: () -> Unit,
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Camera scanning is unavailable.",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Grant camera permission to scan the label.",
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = onClose,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("Close")
                    }
                    Button(onClick = requestPermission) {
                        Text(if (permissionPermanentlyDenied) "Open settings" else "Grant permission")
                    }
                }
            }
        }
    }

    @Composable
    private fun BoxScope.CameraError(message: String, onClose: () -> Unit) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(Color.Black.copy(alpha = 0.82f), MaterialTheme.shapes.medium)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Camera scanning is unavailable.",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message.ifBlank { "Check camera permission and try again." },
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text("Close")
            }
        }
    }

    @Composable
    @SuppressLint("UnsafeOptInUsageError")
    @ExperimentalGetImage
    @TransformExperimental
    private fun CameraPreview(
        onScan: (PhoneLabelScan) -> Unit,
        onCameraBound: (Camera) -> Unit,
        onError: (String) -> Unit,
    ) {
        val currentOnScan by rememberUpdatedState(onScan)
        val currentOnCameraBound by rememberUpdatedState(onCameraBound)
        val currentOnError by rememberUpdatedState(onError)
        val previewView =
            remember {
                PreviewView(activity).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        DisposableEffect(previewView) {
            val analysisExecutor = Executors.newSingleThreadExecutor()
            var barcodeScanner: BarcodeScanner? = null
            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            var provider: ProcessCameraProvider? = null
            var disposed = false
            val providerFuture = ProcessCameraProvider.getInstance(activity)
            previewView.post {
                providerFuture.addListener(
                    {
                        if (disposed) return@addListener
                        try {
                            val resolvedProvider = providerFuture.get()
                            provider = resolvedProvider
                            resolvedProvider.unbindAll()
                            val useCaseGroup =
                                UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(imageAnalysis)
                                    .apply {
                                        previewView.viewPort?.let { viewPort -> setViewPort(viewPort) }
                                    }
                                    .build()
                            val boundCamera =
                                resolvedProvider.bindToLifecycle(
                                    activity,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    useCaseGroup,
                                )
                            val scanner = createBarcodeScanner(boundCamera)
                            barcodeScanner = scanner
                            imageAnalysis.setAnalyzer(
                                analysisExecutor,
                                Code128PairAnalyzer(
                                    scanner = scanner,
                                    previewView = previewView,
                                    onScan = { currentOnScan(it) },
                                    onError = { currentOnError(it) },
                                ),
                            )
                            currentOnCameraBound(boundCamera)
                        } catch (error: Exception) {
                            currentOnError(
                                error.localizedMessage ?: "Check camera permission and try again.",
                            )
                        }
                    },
                    mainExecutor(activity),
                )
            }

            onDispose {
                disposed = true
                imageAnalysis.clearAnalyzer()
                provider?.unbind(preview, imageAnalysis)
                barcodeScanner?.close()
                analysisExecutor.shutdown()
            }
        }
    }
}

private fun createBarcodeScanner(camera: Camera): BarcodeScanner {
    val zoomState = camera.cameraInfo.zoomState.value
    val minimumZoom = zoomState?.minZoomRatio ?: 1f
    val maximumZoom = zoomState?.maxZoomRatio?.coerceAtLeast(minimumZoom) ?: 1f
    val zoomOptions =
        ZoomSuggestionOptions.Builder { suggestedRatio ->
            camera.cameraControl.setZoomRatio(suggestedRatio.coerceIn(minimumZoom, maximumZoom))
            true
        }.setMaxSupportedZoomRatio(maximumZoom)
            .build()
    return BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_CODE_128)
            .enableAllPotentialBarcodes()
            .setZoomSuggestionOptions(zoomOptions)
            .build(),
    )
}

@ExperimentalGetImage
@TransformExperimental
private class Code128PairAnalyzer(
    private val scanner: BarcodeScanner,
    private val previewView: PreviewView,
    private val onScan: (PhoneLabelScan) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val transformFactory =
        ImageProxyTransformFactory().apply {
            isUsingCropRect = true
            isUsingRotationDegrees = true
        }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage =
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val outputTransform = previewView.outputTransform ?: return@addOnSuccessListener
                val coordinateTransform =
                    CoordinateTransform(
                        transformFactory.getOutputTransform(imageProxy),
                        outputTransform,
                    )
                val window = scanWindow(previewView.width, previewView.height)
                val values =
                    barcodes.mapNotNull { barcode ->
                        if (barcode.format != Barcode.FORMAT_CODE_128) return@mapNotNull null
                        val bounds = barcode.boundingBox ?: return@mapNotNull null
                        val mappedBounds = RectF(bounds)
                        coordinateTransform.mapRect(mappedBounds)
                        if (!window.contains(mappedBounds.centerX(), mappedBounds.centerY())) {
                            return@mapNotNull null
                        }
                        barcode.rawValue
                    }
                PhoneCode128Pair.pair(values)?.let(onScan)
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Check camera permission and try again.")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

@Composable
private fun ScanWindowOverlay() {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it },
    ) {
        if (canvasSize == IntSize.Zero) return@Canvas
        val window = scanWindow(size.width.toInt(), size.height.toInt())
        val radius = 24.dp.toPx()
        val overlay =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(
                    RoundRect(
                        rect =
                            Rect(
                                left = window.left,
                                top = window.top,
                                right = window.right,
                                bottom = window.bottom,
                            ),
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            }
        drawPath(overlay, color = Color.Black.copy(alpha = 0.54f))
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(window.left, window.top),
            size = Size(window.width(), window.height()),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

private fun scanWindow(width: Int, height: Int): RectF {
    val windowWidth = width * 0.88f
    val windowHeight = height * 0.70f
    val left = (width - windowWidth) / 2f
    val top = (height - windowHeight) / 2f
    return RectF(left, top, left + windowWidth, top + windowHeight)
}

private fun mainExecutor(activity: ComponentActivity): Executor =
    Executor { command -> activity.runOnUiThread(command) }
