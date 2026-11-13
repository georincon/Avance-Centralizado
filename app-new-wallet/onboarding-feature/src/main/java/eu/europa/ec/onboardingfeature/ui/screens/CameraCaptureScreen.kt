package eu.europa.ec.onboardingfeature.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.europa.ec.onboardingfeature.R
import eu.europa.ec.onboardingfeature.ui.OnboardingState
import java.io.File

enum class CaptureMode { DOC_FRONT, DOC_BACK, SELFIE }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun SelfieOval(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 5.dp.toPx())
        val color = Color.White.copy(alpha = 0.9f)
        val w = size.width * 0.70f
        val h = w * 1.30f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        drawOval(color = color, topLeft = Offset(left, top), size = Size(w, h), style = stroke)
    }
}

@Composable
private fun CapturedPreview(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

@Composable
fun CameraCaptureScreen(
    state: OnboardingState,
    mode: CaptureMode,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val front = mode == CaptureMode.SELFIE
    val activity = context.findActivity()

    // Cedula: camara HORIZONTAL. Selfie: VERTICAL.
    DisposableEffect(front) {
        activity?.requestedOrientation =
            if (front) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val savedPath = when (mode) {
        CaptureMode.DOC_FRONT -> state.data.frontImagePath
        CaptureMode.DOC_BACK -> state.data.backImagePath
        CaptureMode.SELFIE -> state.data.selfieImagePath
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(hasPermission, front) {
        if (!hasPermission) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        // Para el documento se pide la mayor resolucion que ofrezca la camara (4:3): el codigo
        // PDF417 del reverso es muy denso y con pocos pixeles por modulo no se puede leer.
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        if (!front) {
            builder.setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
        }
        val capture = builder.build()
        imageCapture = capture
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        }
    }

    // Se conserva la foto completa: el servidor localiza y lee el codigo de barras por su cuenta.
    val store: (String) -> Unit = { path ->
        when (mode) {
            CaptureMode.DOC_FRONT -> state.updateFrontImage(path)
            CaptureMode.DOC_BACK -> state.updateBackImage(path)
            CaptureMode.SELFIE -> state.updateSelfie(path)
        }
    }
    val clear: () -> Unit = {
        val old = when (mode) {
            CaptureMode.DOC_FRONT -> state.data.frontImagePath
            CaptureMode.DOC_BACK -> state.data.backImagePath
            CaptureMode.SELFIE -> state.data.selfieImagePath
        }
        old?.let { runCatching { File(it).delete() } }
        when (mode) {
            CaptureMode.DOC_FRONT -> state.updateFrontImage(null)
            CaptureMode.DOC_BACK -> state.updateBackImage(null)
            CaptureMode.SELFIE -> state.updateSelfie(null)
        }
    }
    // Enfoca (y mide la luz) en el punto indicado; devuelve cuando termina o a los 1,5 s.
    val focusAt: (Float, Float) -> Unit = { x, y ->
        val cam = camera
        if (cam != null) {
            runCatching {
                val point = previewView.meteringPointFactory.createPoint(x, y)
                val action = FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                cam.cameraControl.startFocusAndMetering(action)
            }
        }
    }
    val capturePhoto: () -> Unit = {
        val ic = imageCapture
        if (ic != null) {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${mode.name.lowercase()}_${System.currentTimeMillis()}.jpg")
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            val shoot = {
                ic.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                            store(file.absolutePath)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // Sin acción por ahora.
                        }
                    }
                )
            }
            if (!front && camera != null) {
                // Documento: enfocar el centro y esperar un momento antes de disparar.
                focusAt(previewView.width / 2f, previewView.height / 2f)
                previewView.postDelayed({ shoot() }, 900)
            } else {
                shoot()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Necesitamos permiso de cámara para tomar la foto.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Permitir cámara")
                }
            }
        } else if (savedPath == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { o -> focusAt(o.x, o.y) } }
            )

            if (front) {
                SelfieOval(modifier = Modifier.fillMaxSize())
            } else {
                // Modelo: max 88% del ancho y con un margen vertical minimo.
                // Si la pantalla es horizontal (ancha y baja), manda el alto y el modelo
                // queda con un pequeno espacio arriba y abajo. Siempre sin deformar.
                val aspect = if (mode == CaptureMode.DOC_FRONT) 466f / 271f else 465f / 269f
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val margin = 16.dp
                    val availW = maxWidth * 0.88f
                    val availH = maxHeight - margin * 2
                    val hByWidth = availW / aspect
                    val fitsByWidth = hByWidth <= availH
                    val finalW = if (fitsByWidth) availW else availH * aspect
                    val finalH = if (fitsByWidth) hByWidth else availH
                    Image(
                        painter = painterResource(
                            if (mode == CaptureMode.DOC_FRONT) R.drawable.guide_front
                            else R.drawable.guide_back
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(finalW, finalH)
                    )
                }
            }

            Text(
                text = if (front) "Ubica tu rostro dentro del óvalo"
                else "Alinea tu cédula con el modelo. Acércala o aléjala hasta que encaje.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )

            // Obturador
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .border(4.dp, Color.White, CircleShape)
                    .clickable { capturePhoto() }
            )
        } else {
            CapturedPreview(
                path = savedPath,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(onClick = { clear() }) {
                    Text("Repetir", color = Color.White)
                }
                Button(onClick = onNext) {
                    Text("Continuar")
                }
            }
        }

        if (canGoBack && savedPath == null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "\u2190",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
