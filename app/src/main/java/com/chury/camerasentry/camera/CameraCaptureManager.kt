package com.chury.camerasentry.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class CameraCaptureManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    suspend fun capture(file: File) {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        provider.unbindAll()
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                capture
            )
            val options = OutputFileOptions.Builder(file).build()
            suspendCancellableCoroutine<Unit> { continuation ->
                capture.takePicture(
                    options,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            continuation.resume(Unit)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            }
        } finally {
            provider.unbind(capture)
        }
    }

    fun stop() {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        cameraExecutor.shutdown()
    }
}
