package com.auroraeq.app.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Liquid Glass" palette — dark-first, vibrant violet/cyan/magenta accents
// refracted through translucent panels.
object GlassColors {
    val Background = Color(0xFF0A0714)
    val BackgroundDeep = Color(0xFF120B26)
    val PanelTint = Color(0xFFFFFFFF)
    val Violet = Color(0xFF9C6BFF)
    val Cyan = Color(0xFF3DE0FF)
    val Magenta = Color(0xFFFF5CE0)
    val TextPrimary = Color(0xFFF4F1FF)
    val TextSecondary = Color(0xFFB3A8D9)
    val Danger = Color(0xFFFF6B7A)

    val backgroundGradient =
        Brush.radialGradient(colors = listOf(BackgroundDeep, Background, Color(0xFF060412)))

    val accentGradient = Brush.linearGradient(colors = listOf(Violet, Cyan))
    val accentGradientWarm = Brush.linearGradient(colors = listOf(Magenta, Violet))

    val specularStroke =
        Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.02f))
        )
}
