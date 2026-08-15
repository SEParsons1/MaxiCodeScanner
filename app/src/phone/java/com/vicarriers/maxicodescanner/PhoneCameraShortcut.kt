package com.vicarriers.maxicodescanner

internal enum class CameraShortcutPhase {
    PRESSED,
    RELEASED,
    CANCELLED,
}

internal data class CameraShortcutEvent(
    val phase: CameraShortcutPhase,
    val pressId: String,
)

internal enum class PhoneCameraShortcutEndAction {
    Ignore,
    CloseCamera,
    RequestPermission,
}

internal fun phoneCameraShortcutEndAction(
    phase: CameraShortcutPhase,
    isCurrentOwner: Boolean,
    cameraPermissionGranted: Boolean,
): PhoneCameraShortcutEndAction {
    if (!isCurrentOwner) return PhoneCameraShortcutEndAction.Ignore
    return when (phase) {
        CameraShortcutPhase.PRESSED -> PhoneCameraShortcutEndAction.Ignore
        CameraShortcutPhase.CANCELLED -> PhoneCameraShortcutEndAction.CloseCamera
        CameraShortcutPhase.RELEASED -> {
            if (cameraPermissionGranted) {
                PhoneCameraShortcutEndAction.CloseCamera
            } else {
                PhoneCameraShortcutEndAction.RequestPermission
            }
        }
    }
}
