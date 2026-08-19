package com.auroraeq.app.presentation.stages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auroraeq.app.domain.model.ShelfState
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassColors
import com.auroraeq.app.presentation.theme.GlassFrequencySlider
import com.auroraeq.app.presentation.theme.GlassLabeledSlider
import com.auroraeq.app.presentation.theme.GlassToggle
import com.auroraeq.app.presentation.theme.formatDb

/**
 * Shared body for Sub Shelf and Air Shelf — a broad low/high-shelf boost/cut with independent
 * per-channel gain (replaces the old native Bass Boost / Loudness Enhancer effects, refactor spec
 * section 9).
 */
@Composable
private fun ShelfStageBody(
    shelf: ShelfState,
    minHz: Float,
    maxHz: Float,
    onFreqChange: (Float) -> Unit,
    onLinkedChange: (Boolean) -> Unit,
    onGainChange: (channelIsLeft: Boolean, Float) -> Unit,
) {
    GlassFrequencySlider(
        label = "Frequency",
        hz = shelf.freqHz,
        minHz = minHz,
        maxHz = maxHz,
        onHzChange = onFreqChange,
        enabled = shelf.enabled,
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "Link L/R",
            color = GlassColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        GlassToggle(checked = shelf.linked, onCheckedChange = onLinkedChange)
    }

    GlassLabeledSlider(
        label = if (shelf.linked) "Gain (L/R)" else "Gain (L)",
        value = shelf.leftGainDb,
        range = -12f..12f,
        onValueChange = { onGainChange(true, it) },
        valueFormatter = ::formatDb,
        enabled = shelf.enabled,
        step = 0.1f,
    )
    if (!shelf.linked) {
        GlassLabeledSlider(
            label = "Gain (R)",
            value = shelf.rightGainDb,
            range = -12f..12f,
            onValueChange = { onGainChange(false, it) },
            valueFormatter = ::formatDb,
            enabled = shelf.enabled,
            step = 0.1f,
        )
    }
}

@Composable
fun SubShelfScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val subShelf = state.chain.subShelf

    StageScreen(
        title = "Sub Shelf",
        subtitle =
            "Broad low-frequency boost or cut centered below the chosen frequency — " +
                "adds warmth and weight or tames boominess, without the on/off cutoff feel of " +
                "the HPF. Replaces the native Bass Boost effect with real per-channel control.",
        enabled = subShelf.enabled,
        onEnabledChange = viewModel::setSubShelfEnabled,
        onReset = viewModel::resetSubShelf,
    ) {
        ShelfStageBody(
            shelf = subShelf,
            minHz = 40f,
            maxHz = 500f,
            onFreqChange = viewModel::setSubShelfFreq,
            onLinkedChange = viewModel::setSubShelfLinked,
            onGainChange = viewModel::setSubShelfGain,
        )
    }
}

@Composable
fun AirShelfScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val airShelf = state.chain.airShelf

    StageScreen(
        title = "Air Shelf",
        subtitle =
            "Broad high-frequency boost or cut centered above the chosen frequency — " +
                "adds sparkle/openness or softens brightness, just before the LPF. Broadly " +
                "shapes the top end rather than cutting it off like a hard filter.",
        enabled = airShelf.enabled,
        onEnabledChange = viewModel::setAirShelfEnabled,
        onReset = viewModel::resetAirShelf,
    ) {
        ShelfStageBody(
            shelf = airShelf,
            minHz = 2000f,
            maxHz = 16000f,
            onFreqChange = viewModel::setAirShelfFreq,
            onLinkedChange = viewModel::setAirShelfLinked,
            onGainChange = viewModel::setAirShelfGain,
        )
    }
}
