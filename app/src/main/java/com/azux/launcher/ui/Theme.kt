package com.azux.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AzuxBlack = Color(0xFF080808)
val AzuxPanel = Color(0xFF141414)
val AzuxPanel2 = Color(0xFF1F1F1F)
val AzuxLine = Color(0xFF2E2E2E)
val AzuxGray = Color(0xFF8C9096)
val AzuxSilver = Color(0xFFC4C8CC)
val AzuxIce = Color(0xFFDCEBFA)
val AzuxWhite = Color(0xFFFFFFFF)

private val Scheme = darkColorScheme(
    primary = AzuxIce,
    onPrimary = AzuxBlack,
    secondary = AzuxSilver,
    onSecondary = AzuxBlack,
    background = AzuxBlack,
    onBackground = AzuxWhite,
    surface = AzuxPanel,
    onSurface = AzuxWhite,
    surfaceVariant = AzuxPanel2,
    onSurfaceVariant = AzuxGray,
    outline = AzuxLine
)

@Composable
fun AzuxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
