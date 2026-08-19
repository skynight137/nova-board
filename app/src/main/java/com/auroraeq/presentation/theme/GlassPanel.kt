package com.auroraeq.app.presentation.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Corner radius shared by every "sheet of glass" surface in the app. */
val GlassCornerRadius = 28.dp

/**
 * The core "Liquid Glass" surface: a translucent, softly tinted panel with a specular highlight
 * along the top edge and a squircle-ish rounded corner, optionally reacting to drag with a subtle
 * elastic tilt (graphicsLayer rotation) to sell the "pane of glass over content" illusion.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = GlassCornerRadius,
    tintAlpha: Float = 0.16f,
    interactiveTilt: Boolean = false,
    content: @Composable () -> Unit,
) {
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val animatedTiltX by
        animateFloatAsState(
            tiltX,
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "tiltX",
        )
    val animatedTiltY by
        animateFloatAsState(
            tiltY,
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "tiltY",
        )

    val shape = RoundedCornerShape(cornerRadius)
    var base =
        modifier
            .then(
                if (interactiveTilt) {
                    Modifier.graphicsLayer {
                            rotationX = animatedTiltX
                            rotationY = animatedTiltY
                            cameraDistance = 24f
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    tiltX = (tiltX - dragAmount.y * 0.05f).coerceIn(-6f, 6f)
                                    tiltY = (tiltY + dragAmount.x * 0.05f).coerceIn(-6f, 6f)
                                },
                                onDragEnd = {
                                    tiltX = 0f
                                    tiltY = 0f
                                },
                                onDragCancel = {
                                    tiltX = 0f
                                    tiltY = 0f
                                },
                            )
                        }
                } else Modifier
            )
            .clip(shape)
            .background(Color.White.copy(alpha = tintAlpha))
            .border(1.dp, GlassColors.specularStroke, shape)

    androidx.compose.foundation.layout.Box(modifier = base.padding(1.dp)) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

/** A thin gradient accent bar, used as a highlight edge or progress fill. */
fun glassAccentBrush(): Brush = GlassColors.accentGradient
