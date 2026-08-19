package com.auroraeq.app.presentation.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Same rationale as `GlassLabeledSlider`'s `DEFAULT_STEP_FRACTION` — a fraction of the 0f..1f range
 * used by [GlassVerticalSlider]'s +/- nudge buttons when a caller doesn't pass an explicit
 * [GlassVerticalSlider.step].
 */
private const val DEFAULT_VERTICAL_STEP_FRACTION = 1f / 40f

/**
 * Vertical EQ band slider styled as a glowing glass rod: a frosted track, a gradient fill from the
 * bottom to the thumb, and a soft blurred "glow" halo behind the thumb that intensifies while
 * dragging. Value changes animate with a spring so drags feel elastic rather than linear.
 *
 * The outer [modifier] sizes the *whole* component (fader + label) inside whatever parent lays it
 * out — pass `Modifier.weight(1f)` from a caller's Column so the label never gets pushed out of
 * bounds. Internally, the fader itself is the weighted child against its own label Text, not
 * `fillMaxHeight()`, precisely so the frequency label below it is always fully visible instead of
 * being silently clipped off-screen.
 */
@Composable
fun GlassVerticalSlider(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    accent: Brush = GlassColors.accentGradient,
    /**
     * Fraction-space nudge for the +/- buttons flanking the fader — pass the caller's real unit
     * step (e.g. 0.1 dB) converted to a fraction of its own range, same convention as
     * `GlassLabeledSlider.step`, so every "dot" slider in the app — vertical or horizontal — nudges
     * by the same kind of precise, discoverable amount instead of only being reachable by dragging
     * a small thumb.
     */
    step: Float? = null,
) {
    val animatedValue by
        animateFloatAsState(
            targetValue = value,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "sliderValue",
        )
    val context = LocalContext.current
    val hapticTicker = remember { HapticStepTicker() }
    // Guard against a future caller passing a non-positive or non-finite
    // step (e.g. 0f, a negative value, or NaN) — falling back to the
    // default instead of silently producing a broken or backwards nudge.
    val resolvedStep = step?.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_VERTICAL_STEP_FRACTION

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                vibrateTick(context)
                onValueChange((value + resolvedStep).coerceIn(0f, 1f))
            },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Increase ${label ?: "value"} gain",
                tint = GlassColors.TextSecondary,
            )
        }
        Box(
            modifier =
                Modifier.width(40.dp).weight(1f).pointerInput(Unit) {
                    val top = 8.dp.toPx()
                    val bottom = size.height - 8.dp.toPx()
                    fun updateFromY(y: Float) {
                        val newValue =
                            (1f - ((y - top) / (bottom - top).coerceAtLeast(0.0001f))).coerceIn(
                                0f,
                                1f,
                            )
                        hapticTicker.onFractionChanged(newValue) { vibrateTick(context) }
                        onValueChange(newValue)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        updateFromY(down.position.y)
                        down.consume()
                        drag(down.id) { change ->
                            updateFromY(change.position.y)
                            change.consume()
                        }
                    }
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 14.dp.toPx()
                val cx = size.width / 2f
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val thumbY = bottom - animatedValue * (bottom - top)

                // Frosted track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(cx - trackWidth / 2, top),
                    size = androidx.compose.ui.geometry.Size(trackWidth, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2),
                )

                // Filled portion with accent gradient
                drawRoundRect(
                    brush = accent,
                    topLeft = Offset(cx - trackWidth / 2, thumbY),
                    size = androidx.compose.ui.geometry.Size(trackWidth, bottom - thumbY),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2),
                )

                // Glow halo behind the thumb
                drawCircle(
                    brush = accent,
                    radius = 18.dp.toPx(),
                    center = Offset(cx, thumbY),
                    alpha = 0.35f,
                )

                // Thumb
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(cx, thumbY))
                drawCircle(
                    brush = accent,
                    radius = 9.dp.toPx(),
                    center = Offset(cx, thumbY),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        IconButton(
            onClick = {
                vibrateTick(context)
                onValueChange((value - resolvedStep).coerceIn(0f, 1f))
            },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "Decrease ${label ?: "value"} gain",
                tint = GlassColors.TextSecondary,
            )
        }
        if (label != null) {
            Text(
                text = label,
                color = GlassColors.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(40.dp).padding(top = 6.dp),
            )
        }
    }
}
