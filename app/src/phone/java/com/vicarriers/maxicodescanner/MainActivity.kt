package com.vicarriers.maxicodescanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.vicarriers.maxicodescanner.ui.ScanScreen
import com.vicarriers.maxicodescanner.ui.theme.MaxiCodeScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var postalDirectory: PostalDirectory
    private lateinit var decodeSound: DecodeSound
    private lateinit var ernNames: ErnNamesClient
    private lateinit var deviceIntegration: PhoneDeviceIntegration

    private var tracking by mutableStateOf("")
    private var name by mutableStateOf("")
    private var city by mutableStateOf("")
    private var postalCode by mutableStateOf("")
    private var nameLookupGeneration = 0L

    private var cameraVisible by mutableStateOf(false)
    private var cameraShortcutOwner: String? = null
    private var cameraPermissionRequestPending by mutableStateOf(false)
    private var cameraReturning = false
    private var cameraSystemFlowActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC
        postalDirectory = PostalDirectory(this)
        decodeSound = DecodeSound(this)
        ernNames = ErnNamesClient()
        deviceIntegration = PhoneDeviceIntegration(this)
        lifecycleScope.launch(Dispatchers.IO) {
            ernNames.prefetch()
        }
        lifecycleScope.launch {
            deviceIntegration.cameraShortcutEvents.collect(::handleCameraShortcutEvent)
        }
        setContent {
            MaxiCodeScannerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        ScanScreen(
                            tracking = tracking,
                            name = name,
                            city = city,
                            postalCode = postalCode,
                            showRawPayload = false,
                        )
                    }
                    if (cameraVisible) {
                        deviceIntegration.CameraContent(
                            requestPermission = cameraPermissionRequestPending,
                            onScan = ::handleCameraScan,
                            onPermissionRequestConsumed = {
                                cameraPermissionRequestPending = false
                            },
                            onPermissionFlowFinished = ::onCameraPermissionFlowFinished,
                            onSystemFlowActiveChange = ::onCameraSystemFlowActiveChange,
                            onClose = ::closeCamera,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        deviceIntegration.onResume()
    }

    override fun onPause() {
        if (!cameraSystemFlowActive) {
            deviceIntegration.onPause()
            closeCamera()
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::deviceIntegration.isInitialized && (hasFocus || !cameraSystemFlowActive)) {
            deviceIntegration.onWindowFocusChanged(hasFocus)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::deviceIntegration.isInitialized && deviceIntegration.dispatchKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        if (::deviceIntegration.isInitialized) deviceIntegration.close()
        decodeSound.release()
        postalDirectory.close()
        super.onDestroy()
    }

    private fun handleCameraShortcutEvent(event: CameraShortcutEvent) {
        if (event.phase == CameraShortcutPhase.PRESSED) {
            if (!cameraVisible && cameraShortcutOwner == null) openCamera(event.pressId)
            return
        }
        when (
            phoneCameraShortcutEndAction(
                phase = event.phase,
                isCurrentOwner = cameraShortcutOwner == event.pressId,
                cameraPermissionGranted = hasCameraPermission(),
            )
        ) {
            PhoneCameraShortcutEndAction.Ignore -> Unit
            PhoneCameraShortcutEndAction.CloseCamera -> closeCamera()
            PhoneCameraShortcutEndAction.RequestPermission -> {
                cameraShortcutOwner = null
                window.decorView.post {
                    if (cameraVisible && !hasCameraPermission()) {
                        cameraPermissionRequestPending = true
                    }
                }
            }
        }
    }

    private fun openCamera(shortcutOwner: String) {
        cameraReturning = false
        cameraShortcutOwner = shortcutOwner
        cameraPermissionRequestPending = false
        cameraVisible = true
    }

    private fun closeCamera() {
        cameraSystemFlowActive = false
        cameraShortcutOwner = null
        cameraPermissionRequestPending = false
        cameraVisible = false
    }

    private fun onCameraSystemFlowActiveChange(active: Boolean) {
        cameraSystemFlowActive = active
        if (!active) {
            deviceIntegration.resetShortcutAfterSystemFlow()
            cameraShortcutOwner = null
        }
    }

    private fun onCameraPermissionFlowFinished(granted: Boolean) {
        if (granted) closeCamera()
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun handleCameraScan(scan: PhoneLabelScan) {
        runOnUiThread {
            if (cameraReturning) return@runOnUiThread
            cameraReturning = true
            applyScan(scan)
            closeCamera()
        }
    }

    private fun applyScan(scan: PhoneLabelScan) {
        tracking = MaxiCodeParser.formatTracking(scan.tracking)
        postalCode = scan.postalCode
        city = postalDirectory.cityForPostal(scan.postalCode).orEmpty()
        name = ""
        decodeSound.play()
        val generation = ++nameLookupGeneration
        lifecycleScope.launch(Dispatchers.IO) {
            val found = ernNames.nameForTracking(scan.tracking).orEmpty()
            if (generation != nameLookupGeneration) return@launch
            withContext(Dispatchers.Main) {
                if (generation == nameLookupGeneration) {
                    name = found
                }
            }
        }
    }
}
