package com.auroraeq.app.presentation.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default step size used by the +/- nudge buttons when a caller doesn't specify one explicitly — a
 * fraction of the slider's total range, so every "dot" slider gets a precise, discoverable nudge
 * control instead of relying solely on dragging a small thumb to hit an exact value.
 */
private const val DEFAULT_STEP_FRACTION = 1f / 40f

/**
 * A horizontal "glass rod" slider for single-value gain/frequency/time controls (Preamp gain,
 * filter cutoff, shelf freq/gain, compressor/limiter parameters, output gain, spatial strength).
 * Always paired with a live numeric readout of the current value (refactor spec section 8 — every
 * gain-style control gets a live readout, not just the 31-band EQ). Flanked by +/- buttons that
 * nudge the value by [step] — dragging the thumb precisely is hard on a small "dot", so every
 * slider also has a discrete, repeatable way to reach an exact value.
 */
@Composable
fun GlassLabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { "%.1f".format(it) },
    accent: Brush = GlassColors.accentGradient,
    enabled: Boolean = true,
    step: Float? = null,
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val resolvedStep = step ?: (span * DEFAULT_STEP_FRACTION)
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val animatedFraction by
        animateFloatAsState(
            targetValue = fraction,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "hSliderValue",
        )
    val trackAlpha = if (enabled) 1f else 0.4f
    val context = LocalContext.current
    val hapticTicker = remember { HapticStepTicker() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = GlassColors.TextSecondary, fontSize = 13.sp)
            Text(
                valueFormatter(value),
                color = GlassColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    vibrateTick(context)
                    onValueChange((value - resolvedStep).coerceIn(range))
                },
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = "Decrease $label",
                    tint = GlassColors.TextSecondary,
                )
            }
            Canvas(
                modifier =
                    Modifier.weight(1f).height(28.dp).pointerInput(enabled, range) {
                        if (!enabled) return@pointerInput
                        val left = 4.dp.toPx()
                        val right = size.width - 4.dp.toPx()
                        fun updateFromX(x: Float) {
                            val newFraction =
                                ((x - left) / (right - left).coerceAtLeast(0.0001f)).coerceIn(
                                    0f,
                                    1f,
                                )
                            hapticTicker.onFractionChanged(newFraction) { vibrateTick(context) }
                            onValueChange(range.start + newFraction * span)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            updateFromX(down.position.x)
                            down.consume()
                            drag(down.id) { change ->
                                updateFromX(change.position.x)
                                change.consume()
                            }
                        }
                    }
            ) {
                val trackHeight = 10.dp.toPx()
                val cy = size.height / 2f
                val left = 4.dp.toPx()
                val right = size.width - 4.dp.toPx()
                val thumbX = left + animatedFraction * (right - left)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f * trackAlpha),
                    topLeft = Offset(left, cy - trackHeight / 2),
                    size = Size(right - left, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2),
                )
                drawRoundRect(
                    brush = accent,
                    topLeft = Offset(left, cy - trackHeight / 2),
                    size = Size((thumbX - left).coerceAtLeast(0f), trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2),
                    alpha = trackAlpha,
                )
                drawCircle(
                    brush = accent,
                    radius = 16.dp.toPx(),
                    center = Offset(thumbX, cy),
                    alpha = 0.30f * trackAlpha,
                )
                drawCircle(
                    color = Color.White.copy(alpha = trackAlpha),
                    radius = 9.dp.toPx(),
                    center = Offset(thumbX, cy),
                )
                drawCircle(
                    brush = accent,
                    radius = 9.dp.toPx(),
                    center = Offset(thumbX, cy),
                    style = Stroke(width = 2.dp.toPx()),
                    alpha = trackAlpha,
                )
            }
            IconButton(
                onClick = {
                    vibrateTick(context)
                    onValueChange((value + resolvedStep).coerceIn(range))
                },
                enabled = enabled,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Increase $label",
                    tint = GlassColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * Maps a 0f..1f slider fraction onto a logarithmic frequency range — used for
 * cutoff/shelf-frequency sliders so the whole audible band isn't squeezed into the low end of a
 * linear control.
 */
fun logFrequencyFromFraction(fraction: Float, minHz: Float, maxHz: Float): Float =
    minHz * Math.pow((maxHz / minHz).toDouble(), fraction.toDouble()).toFloat()

fun fractionFromLogFrequency(hz: Float, minHz: Float, maxHz: Float): Float {
    val clamped = hz.coerceIn(minHz, maxHz)
    return (Math.log(clamped.toDouble() / minHz) / Math.log((maxHz / minHz).toDouble())).toFloat()
}

/**
 * A frequency slider on a log scale — wraps [GlassLabeledSlider] but drags across an internal
 * 0f..1f fraction mapped exponentially onto [minHz]..[maxHz].
 */
@Composable
fun GlassFrequencySlider(
    label: String,
    hz: Float,
    minHz: Float,
    maxHz: Float,
    onHzChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassLabeledSlider(
        label = label,
        value = fractionFromLogFrequency(hz, minHz, maxHz),
        range = 0f..1f,
        onValueChange = { onHzChange(logFrequencyFromFraction(it, minHz, maxHz)) },
        valueFormatter = { formatHz(logFrequencyFromFraction(it, minHz, maxHz)) },
        modifier = modifier,
        enabled = enabled,
    )
}

fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "%.0f Hz".format(hz)

fun formatDb(db: Float): String = (if (db > 0f) "+" else "") + "%.1f dB".format(db)
