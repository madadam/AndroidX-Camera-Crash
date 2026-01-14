package org.example.cameracrash

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var previewView: PreviewView

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)

        setContent {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                floatingActionButton = {
                    LargeFloatingActionButton(
                        onClick = { toggleRecording() },
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.Add, "toggle recording")
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { innerPadding ->
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        requestPermissions.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, mainExecutor)
    }

    private fun toggleRecording() {
        if (recording != null) {
            recording?.stop()
            recording = null
            return
        }

        val storage = getSystemService(STORAGE_SERVICE) as StorageManager
        val handler = Handler(HandlerThread("proxy file descriptor").apply { start() }.looper)

        val pfd = storage.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_WRITE_ONLY,
            ProxyCallback(),
            handler,
        )

        val output = FileDescriptorOutputOptions.Builder(pfd).build()

        val videoCapture = requireNotNull(this.videoCapture)

        recording = videoCapture.output
            .prepareRecording(this, output)
            .withAudioEnabled()
            .start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.i(TAG, "recording started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.e(TAG, "recording failed: ${event.error}", event.cause)
                        } else {
                            Log.i(TAG, "recording complete")
                        }
                    }
                }
            }
    }
}

private class ProxyCallback : ProxyFileDescriptorCallback() {
    companion object {
        private const val TAG = "ProxyCallback"
    }

    override fun onGetSize(): Long {
        Log.d(TAG, "onGetSize")
        return 0
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        Log.d(TAG, "onRead")
        return 0
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        Log.d(TAG, "onWrite")
        return 0
    }

    override fun onFsync() {
        Log.d(TAG, "onFsync")
    }

    override fun onRelease() {
        Log.d(TAG, "onRelease")
    }
}

