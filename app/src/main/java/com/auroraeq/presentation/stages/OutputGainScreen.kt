package com.auroraeq.app.presentation.stages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassLabeledSlider
import com.auroraeq.app.presentation.theme.formatDb

@Composable
fun OutputGainScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val outputGain = state.chain.outputGain

    StageScreen(
        title = "Output Gain",
        subtitle =
            "Final volume trim after the Limiter — use it to match loudness against " +
                "other sources. Implemented via the Limiter's post-gain, so it only takes " +
                "effect while the Limiter stage itself is enabled.",
        enabled = outputGain.enabled,
        onEnabledChange = viewModel::setOutputGainEnabled,
        onReset = viewModel::resetOutputGain,
    ) {
        GlassLabeledSlider(
            label = "Gain",
            value = outputGain.gainDb,
            range = -20f..20f,
            onValueChange = viewModel::setOutputGain,
            valueFormatter = ::formatDb,
            enabled = outputGain.enabled,
            step = 0.1f,
        )
    }
}
