package com.nico2.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.ImageFormat
import android.graphics.YuvImage
import java.nio.ByteBuffer
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal enum class LiveCameraError {
    CameraUnavailable,
    FrameSendFailed,
}

@Composable
internal fun LiveCameraPreview(
    lifecycleOwner: LifecycleOwner,
    liveSession: GeminiLiveSession?,
    modifier: Modifier = Modifier,
    onError: (LiveCameraError) -> Unit,
) {
    val context = LocalContext.current
    val currentSession = rememberUpdatedState(liveSession)
    val currentOnError = rememberUpdatedState(onError)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )

    DisposableEffect(lifecycleOwner, previewView, cameraExecutor) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        val disposed = AtomicBoolean(false)
        val reportedError = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener(
            {
                try {
                    if (disposed.get()) return@addListener
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(
                                    AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
                                )
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(TARGET_WIDTH, TARGET_HEIGHT),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                    ),
                                )
                                .build(),
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    var lastFrameAt = 0L
                    analysis.setAnalyzer(cameraExecutor) { image ->
                        try {
                            val now = System.currentTimeMillis()
                            if (now - lastFrameAt >= FRAME_INTERVAL_MILLIS) {
                                lastFrameAt = now
                                val frameSent = currentSession.value
                                    ?.sendVideoFrame(image.toJpeg()) == true
                                if (!frameSent && reportedError.compareAndSet(false, true)) {
                                    mainExecutor.execute {
                                        currentOnError.value(LiveCameraError.FrameSendFailed)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            if (reportedError.compareAndSet(false, true)) {
                                mainExecutor.execute {
                                    currentOnError.value(LiveCameraError.FrameSendFailed)
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    if (disposed.get()) return@addListener
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        },
                        preview,
                        analysis,
                    )
                } catch (_: Exception) {
                    if (reportedError.compareAndSet(false, true)) {
                        currentOnError.value(LiveCameraError.CameraUnavailable)
                    }
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed.set(true)
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }
}

private fun ImageProxy.toJpeg(): ByteArray {
    val yuv = yuv420ToNv21()
    val jpeg = ByteArrayOutputStream().use { output ->
        check(
            YuvImage(yuv, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, output),
        ) {
            "Camera frame JPEG compression failed."
        }
        output.toByteArray()
    }
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return jpeg

    val source = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        ?: error("Camera frame JPEG decoding failed.")
    val rotated = Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        Matrix().apply { postRotate(rotation.toFloat()) },
        true,
    )
    if (rotated !== source) source.recycle()
    return try {
        ByteArrayOutputStream().use { output ->
            check(rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Rotated camera frame JPEG compression failed."
            }
            output.toByteArray()
        }
    } finally {
        rotated.recycle()
    }
}

private fun ImageProxy.yuv420ToNv21(): ByteArray {
    val output = ByteArray(width * height * 3 / 2)
    copyPlane(planes[0], width, height, output, 0, 1)
    val chromaOffset = width * height
    copyPlane(planes[2], width / 2, height / 2, output, chromaOffset, 2)
    copyPlane(planes[1], width / 2, height / 2, output, chromaOffset + 1, 2)
    return output
}

private fun copyPlane(
    plane: androidx.camera.core.ImageProxy.PlaneProxy,
    planeWidth: Int,
    planeHeight: Int,
    output: ByteArray,
    outputOffset: Int,
    outputPixelStride: Int,
) {
    val buffer: ByteBuffer = plane.buffer
    val base = buffer.position()
    for (row in 0 until planeHeight) {
        for (column in 0 until planeWidth) {
            val inputIndex = base + row * plane.rowStride + column * plane.pixelStride
            val outputIndex = outputOffset + (row * planeWidth + column) * outputPixelStride
            output[outputIndex] = buffer.get(inputIndex)
        }
    }
}

private const val TARGET_WIDTH = 640
private const val TARGET_HEIGHT = 480
private const val FRAME_INTERVAL_MILLIS = 1_000L
private const val JPEG_QUALITY = 60
