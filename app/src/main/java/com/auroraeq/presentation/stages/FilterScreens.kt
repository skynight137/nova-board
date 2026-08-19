package com.auroraeq.app.presentation.stages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.auroraeq.app.domain.model.FilterSlope
import com.auroraeq.app.domain.model.FilterState
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassChip
import com.auroraeq.app.presentation.theme.GlassFrequencySlider

/**
 * Shared body for HPF and LPF — both are a cutoff frequency plus a slope choice, approximated as a
 * gain roll-off blended into the nearby 31-band EQ (see DynamicsEngineManager). Not a true
 * steep-slope filter; that caveat is surfaced on the Settings screen.
 */
@Composable
private fun FilterStageBody(
    filter: FilterState,
    minHz: Float,
    maxHz: Float,
    onCutoffChange: (Float) -> Unit,
    onSlopeChange: (FilterSlope) -> Unit,
) {
    GlassFrequencySlider(
        label = "Cutoff",
        hz = filter.cutoffHz,
        minHz = minHz,
        maxHz = maxHz,
        onHzChange = onCutoffChange,
        enabled = filter.enabled,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterSlope.entries.forEach { slope ->
            GlassChip(
                label = slope.label,
                selected = filter.slope == slope,
                onClick = { onSlopeChange(slope) },
            )
        }
    }
}

@Composable
fun HpfScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val hpf = state.chain.hpf

    StageScreen(
        title = "HPF",
        subtitle =
            "High-pass filter — gradually reduces content below the cutoff frequency, " +
                "useful for cutting rumble, handling noise, or muddiness. Approximated as an EQ " +
                "gain roll-off rather than a true steep-slope filter — see Settings for why.",
        enabled = hpf.enabled,
        onEnabledChange = viewModel::setHpfEnabled,
        onReset = viewModel::resetHpf,
    ) {
        FilterStageBody(
            filter = hpf,
            minHz = 20f,
            maxHz = 2000f,
            onCutoffChange = viewModel::setHpfCutoff,
            onSlopeChange = viewModel::setHpfSlope,
        )
    }
}

@Composable
fun LpfScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val lpf = state.chain.lpf

    StageScreen(
        title = "LPF",
        subtitle =
            "Low-pass filter — gradually reduces content above the cutoff frequency, " +
                "useful for taming harshness or sibilance up top. Approximated as an EQ gain " +
                "roll-off rather than a true steep-slope filter — see Settings for why.",
        enabled = lpf.enabled,
        onEnabledChange = viewModel::setLpfEnabled,
        onReset = viewModel::resetLpf,
    ) {
        FilterStageBody(
            filter = lpf,
            minHz = 2000f,
            maxHz = 20000f,
            onCutoffChange = viewModel::setLpfCutoff,
            onSlopeChange = viewModel::setLpfSlope,
        )
    }
}
