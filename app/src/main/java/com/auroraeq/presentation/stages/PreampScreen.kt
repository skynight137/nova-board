package com.auroraeq.app.presentation.stages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassLabeledSlider
import com.auroraeq.app.presentation.theme.formatDb

/** First stage in the chain: a simple input trim before anything else. */
@Composable
fun PreampScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val preamp = state.chain.preamp

    StageScreen(
        title = "Preamp",
        subtitle =
            "Input trim applied before every other stage — raise it to bring up a " +
                "quiet source, lower it to leave headroom so later stages (EQ, Compressor, " +
                "Limiter) don't clip.",
        enabled = preamp.enabled,
        onEnabledChange = viewModel::setPreampEnabled,
        onReset = viewModel::resetPreamp,
    ) {
        GlassLabeledSlider(
            label = "Gain",
            value = preamp.gainDb,
            range = -20f..20f,
            onValueChange = viewModel::setPreampGain,
            valueFormatter = ::formatDb,
            enabled = preamp.enabled,
            step = 0.1f,
        )
    }
}
