package rs.moma.janus.privezak.ui.screens

import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxWidth
import com.google.mlkit.vision.barcode.BarcodeScanning
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import androidx.camera.core.Preview as CameraPreview
import androidx.compose.ui.graphics.drawscope.Stroke
import rs.moma.janus.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.BlendMode
import androidx.activity.compose.BackHandler
import rs.moma.janus.privezak.ui.theme.Muted
import kotlinx.coroutines.awaitCancellation
import androidx.camera.core.SurfaceRequest
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import android.Manifest.permission.CAMERA
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.annotation.OptIn
import rs.moma.janus.privezak.R
import android.content.Context

private const val FIDO_LINK = "FIDO:/"

@Composable
fun ScanScreen(onBack: () -> Unit, onScanned: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val permission = rememberLauncherForActivityResult(RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(CAMERA) }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    BackHandler(onBack = onBack)

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val provider = cameraProvider(context)
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(FORMAT_QR_CODE).build()
        val scanner = BarcodeScanning.getClient(options)
        val preview = CameraPreview.Builder().build().apply {
            setSurfaceProvider { surfaceRequest = it }
        }

        var read = false
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
                    scanner.readQrCode(image) {
                        if (!read && it.startsWith(FIDO_LINK, ignoreCase = true)) {
                            read = true
                            onScanned(it)
                        }
                    }
                }
            }

        provider.bindToLifecycle(lifecycleOwner, DEFAULT_BACK_CAMERA, preview, analysis)
        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        surfaceRequest?.let { CameraXViewfinder(it, Modifier.fillMaxSize()) }
        ScanOverlay(granted, onBack)
    }
}

@Composable
private fun BoxScope.ScanOverlay(granted: Boolean, onBack: () -> Unit) {
    ScanWindow()

    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = "Close",
                modifier = Modifier.size(32.dp)
            )
        }
    }

    if (!granted) Text(
        text = "Privezak needs the camera\naccess to scan a QR code.",
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
        style = typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = Muted
    )
}

@Composable
private fun ScanWindow() {
    Canvas(
        Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val side = size.minDimension * 0.75f
        val window = Size(side, side)
        val corner = CornerRadius(32.dp.toPx())
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
        drawRect(Color.Black, alpha = 0.7f)
        drawRoundRect(Color.Black, topLeft, window, corner, blendMode = BlendMode.Clear)
        drawRoundRect(Muted, topLeft, window, corner, style = Stroke(2.dp.toPx()))
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, CAMERA) == PERMISSION_GRANTED

private suspend fun cameraProvider(context: Context): ProcessCameraProvider =
    withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }

@OptIn(ExperimentalGetImage::class)
private fun BarcodeScanner.readQrCode(image: ImageProxy, onRead: (String) -> Unit) {
    val frame = image.image ?: return image.close()
    process(InputImage.fromMediaImage(frame, image.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onRead) }
        .addOnCompleteListener { image.close() }
}

@Preview(showSystemUi = true)
@Composable
private fun ScanScreenPreview() {
    PrivezakTheme {
        Surface {
            Box(Modifier.fillMaxSize()) { ScanOverlay(granted = true, onBack = {}) }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ScanScreenDeniedPreview() {
    PrivezakTheme {
        Surface {
            Box(Modifier.fillMaxSize()) { ScanOverlay(granted = false, onBack = {}) }
        }
    }
}
