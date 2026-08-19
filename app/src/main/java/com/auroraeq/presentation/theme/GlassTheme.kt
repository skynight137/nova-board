package com.auroraeq.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val AuroraColorScheme =
    darkColorScheme(
        primary = GlassColors.Violet,
        secondary = GlassColors.Cyan,
        tertiary = GlassColors.Magenta,
        background = GlassColors.Background,
        surface = GlassColors.BackgroundDeep,
        onBackground = GlassColors.TextPrimary,
        onSurface = GlassColors.TextPrimary,
        error = GlassColors.Danger,
    )

@Composable
fun AuroraEqTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AuroraColorScheme, typography = MaterialTheme.typography) {
        Box(modifier = Modifier.fillMaxSize().background(GlassColors.backgroundGradient)) {
            content()
        }
    }
}
