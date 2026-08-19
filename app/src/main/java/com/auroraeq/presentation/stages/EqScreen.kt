package com.auroraeq.app.presentation.stages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraeq.app.domain.model.EQ_BAND_COUNT
import com.auroraeq.app.domain.model.EQ_BAND_FREQUENCIES_HZ
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassChip
import com.auroraeq.app.presentation.theme.GlassColors
import com.auroraeq.app.presentation.theme.GlassToggle
import com.auroraeq.app.presentation.theme.GlassVerticalSlider
import com.auroraeq.app.presentation.theme.HapticStepTicker
import com.auroraeq.app.presentation.theme.formatDb
import com.auroraeq.app.presentation.theme.formatHz
import com.auroraeq.app.presentation.theme.vibrateTick
import kotlinx.coroutines.launch

private const val BAND_GAIN_MIN = -12f
private const val BAND_GAIN_MAX = 12f
private const val BAND_GAIN_SPAN = BAND_GAIN_MAX - BAND_GAIN_MIN

/**
 * 31-band graphic EQ, per-channel(L/R) with an optional Link toggle. Every band shows a live
 * numeric dB readout above its fader as it's dragged, plus its center frequency in Hz below the
 * fader at all times, so it's always clear which band is being adjusted. Only a handful of bands
 * fit on screen at once; since dragging a fader consumes the touch gesture (the normal
 * swipe-to-scroll on the row itself can't compete with that), a dedicated drag handle below the row
 * scrubs through all 31 bands.
 */
@Composable
fun EqScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val eq = state.chain.eq

    StageScreen(
        title = "EQ",
        subtitle =
            "31-band 1/3-octave graphic EQ for precise per-frequency shaping, " +
                "independent for the left and right channels. Drag the handle below the " +
                "bands to swipe through all 31 — dragging a fader itself takes over the " +
                "touch gesture, so swiping the row directly won't scroll it.",
        enabled = eq.enabled,
        onEnabledChange = viewModel::setEqEnabled,
        onReset = viewModel::resetEq,
        scrollable = false,
    ) {
        var editingLeft by remember { mutableStateOf(true) }
        val listState = rememberLazyListState()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Link L/R",
                color = GlassColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            GlassToggle(checked = eq.linked, onCheckedChange = viewModel::setEqLinked)
        }

        if (!eq.linked) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassChip(label = "Left", selected = editingLeft, onClick = { editingLeft = true })
                GlassChip(
                    label = "Right",
                    selected = !editingLeft,
                    onClick = { editingLeft = false },
                )
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(300.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(EQ_BAND_FREQUENCIES_HZ) { band, freqHz ->
                val channel = if (eq.linked || editingLeft) eq.left else eq.right
                val displayedGain = channel.bandGainsDb.getOrElse(band) { 0f }

                Column(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        formatDb(displayedGain),
                        color = GlassColors.TextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    GlassVerticalSlider(
                        value = (displayedGain - BAND_GAIN_MIN) / BAND_GAIN_SPAN,
                        onValueChange = { fraction ->
                            val gainDb = BAND_GAIN_MIN + fraction * BAND_GAIN_SPAN
                            viewModel.setBandGain(editingLeft, band, gainDb)
                        },
                        label = formatHz(freqHz.toFloat()),
                        modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                        // Same micro-step convention as every other Float
                        // control (see slider-micro-step.md): 0.1 dB per tap.
                        step = 0.1f / BAND_GAIN_SPAN,
                    )
                }
            }
        }

        BandScrubber(
            listState = listState,
            totalBands = EQ_BAND_COUNT,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/**
 * A draggable horizontal scrubber that pages the 31-band [LazyRow] — the thumb width reflects how
 * many bands are currently visible, and dragging anywhere scrolls proportionally, replacing the old
 * prev/next arrow buttons (dragging a band's own fader consumes touch, so the row itself can't be
 * swiped directly).
 */
@Composable
private fun BandScrubber(
    listState: androidx.compose.foundation.lazy.LazyListState,
    totalBands: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val firstVisible = listState.firstVisibleItemIndex
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceIn(1, totalBands)
    val context = LocalContext.current
    val hapticTicker =
        remember(totalBands) { HapticStepTicker(steps = (totalBands - 1).coerceAtLeast(1)) }

    Canvas(
        modifier =
            modifier.height(20.dp).pointerInput(totalBands) {
                val left = 4.dp.toPx()
                val right = size.width - 4.dp.toPx()
                fun updateFromX(x: Float) {
                    val fraction =
                        ((x - left) / (right - left).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                    val target = (fraction * (totalBands - 1)).toInt().coerceIn(0, totalBands - 1)
                    hapticTicker.onFractionChanged(fraction) { vibrateTick(context) }
                    scope.launch { listState.scrollToItem(target) }
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
        val trackHeight = 6.dp.toPx()
        val cy = size.height / 2f
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val trackWidth = right - left

        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(left, cy - trackHeight / 2),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )

        val thumbWidth = (trackWidth * visibleCount / totalBands).coerceAtLeast(trackHeight * 3)
        val thumbLeft =
            left +
                (trackWidth - thumbWidth) *
                    (firstVisible.toFloat() / (totalBands - visibleCount).coerceAtLeast(1))

        drawRoundRect(
            brush = GlassColors.accentGradient,
            topLeft = Offset(thumbLeft.coerceIn(left, right - thumbWidth), cy - trackHeight),
            size = Size(thumbWidth, trackHeight * 2),
            cornerRadius = CornerRadius(trackHeight),
        )
    }
}
