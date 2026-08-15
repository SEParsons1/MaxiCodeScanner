package com.vicarriers.maxicodescanner

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCameraShortcutPolicyTest {
    @Test
    fun missingPermission_waitsForReleaseBeforeRequestingPermission() {
        assertEquals(
            PhoneCameraShortcutEndAction.Ignore,
            phoneCameraShortcutEndAction(
                phase = CameraShortcutPhase.PRESSED,
                isCurrentOwner = true,
                cameraPermissionGranted = false,
            ),
        )
        assertEquals(
            PhoneCameraShortcutEndAction.RequestPermission,
            phoneCameraShortcutEndAction(
                phase = CameraShortcutPhase.RELEASED,
                isCurrentOwner = true,
                cameraPermissionGranted = false,
            ),
        )
    }

    @Test
    fun cancelledPress_closesWithoutRequestingPermission() {
        assertEquals(
            PhoneCameraShortcutEndAction.CloseCamera,
            phoneCameraShortcutEndAction(
                phase = CameraShortcutPhase.CANCELLED,
                isCurrentOwner = true,
                cameraPermissionGranted = false,
            ),
        )
    }

    @Test
    fun grantedPermission_releaseClosesNormalCameraSession() {
        assertEquals(
            PhoneCameraShortcutEndAction.CloseCamera,
            phoneCameraShortcutEndAction(
                phase = CameraShortcutPhase.RELEASED,
                isCurrentOwner = true,
                cameraPermissionGranted = true,
            ),
        )
    }

    @Test
    fun unrelatedRelease_cannotAffectCameraSession() {
        assertEquals(
            PhoneCameraShortcutEndAction.Ignore,
            phoneCameraShortcutEndAction(
                phase = CameraShortcutPhase.RELEASED,
                isCurrentOwner = false,
                cameraPermissionGranted = false,
            ),
        )
    }
}
