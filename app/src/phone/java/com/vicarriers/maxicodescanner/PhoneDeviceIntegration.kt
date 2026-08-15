package com.vicarriers.maxicodescanner

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class PhoneDeviceIntegration(
    private val activity: ComponentActivity,
) {
    private val mutableShortcutEvents =
        MutableSharedFlow<CameraShortcutEvent>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val cameraShortcutEvents: Flow<CameraShortcutEvent> = mutableShortcutEvents

    private val cameraScanner = PhoneCameraScanner(activity)
    private var isResumed = false
    private var activeVolumeDownPressId: String? = null

    fun onResume() {
        isResumed = true
    }

    fun onPause() {
        isResumed = false
        cancelActivePress()
    }

    fun onWindowFocusChanged(@Suppress("UNUSED_PARAMETER") hasFocus: Boolean) {
        // Samsung's volume overlay steals window focus. Cancelling here closes
        // the camera while the user is still holding Volume Down.
    }

    fun resetShortcutAfterSystemFlow() {
        activeVolumeDownPressId = null
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN ||
                !isResumed ||
                !activity.hasWindowFocus()
        ) {
            return false
        }

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                press(event)
                true
            }
            KeyEvent.ACTION_UP -> {
                release(cancelled = event.isCanceled)
                true
            }
            else -> false
        }
    }

    private fun press(event: KeyEvent) {
        if (event.repeatCount != 0 || activeVolumeDownPressId != null) return
        val pressId = "${event.deviceId}:${event.downTime}"
        activeVolumeDownPressId = pressId
        mutableShortcutEvents.tryEmit(
            CameraShortcutEvent(CameraShortcutPhase.PRESSED, pressId),
        )
    }

    private fun release(cancelled: Boolean) {
        val pressId = activeVolumeDownPressId ?: return
        activeVolumeDownPressId = null
        mutableShortcutEvents.tryEmit(
            CameraShortcutEvent(
                phase = if (cancelled) CameraShortcutPhase.CANCELLED else CameraShortcutPhase.RELEASED,
                pressId = pressId,
            ),
        )
    }

    private fun cancelActivePress() {
        release(cancelled = true)
    }

    @Composable
    fun CameraContent(
        requestPermission: Boolean,
        onScan: (PhoneLabelScan) -> Unit,
        onPermissionRequestConsumed: () -> Unit,
        onPermissionFlowFinished: (Boolean) -> Unit,
        onSystemFlowActiveChange: (Boolean) -> Unit,
        onClose: () -> Unit,
    ) {
        cameraScanner.Content(
            requestPermission = requestPermission,
            onScan = onScan,
            onPermissionRequestConsumed = onPermissionRequestConsumed,
            onPermissionFlowFinished = onPermissionFlowFinished,
            onSystemFlowActiveChange = onSystemFlowActiveChange,
            onClose = {
                cancelActivePress()
                onClose()
            },
        )
    }

    fun close() {
        isResumed = false
        cancelActivePress()
    }
}
