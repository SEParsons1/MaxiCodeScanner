package com.vicarriers.maxicodescanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Brown,
    background = Cream,
    surface = SurfaceRaised,
    onPrimary = White,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun MaxiCodeScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
