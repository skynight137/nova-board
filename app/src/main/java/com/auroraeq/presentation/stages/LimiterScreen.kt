package com.auroraeq.app.presentation.stages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassLabeledSlider
import com.auroraeq.app.presentation.theme.formatDb

@Composable
fun LimiterScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val limiter = state.chain.limiter

    StageScreen(
        title = "Limiter",
        subtitle =
            "Brick-wall ceiling that stops the signal from exceeding the set level — " +
                "the last line of defense against clipping and distortion. Output Gain's trim " +
                "is also applied here, so it only takes effect while this stage is enabled.",
        enabled = limiter.enabled,
        onEnabledChange = viewModel::setLimiterEnabled,
        onReset = viewModel::resetLimiter,
    ) {
        GlassLabeledSlider(
            label = "Ceiling",
            value = limiter.ceilingDb,
            range = -12f..0f,
            onValueChange = viewModel::setLimiterCeiling,
            valueFormatter = ::formatDb,
            enabled = limiter.enabled,
            step = 0.1f,
        )
        GlassLabeledSlider(
            label = "Attack",
            value = limiter.attackMs,
            range = 0.1f..50f,
            onValueChange = viewModel::setLimiterAttack,
            valueFormatter = { "%.1f ms".format(it) },
            enabled = limiter.enabled,
            step = 0.1f,
        )
        GlassLabeledSlider(
            label = "Release",
            value = limiter.releaseMs,
            range = 10f..200f,
            onValueChange = viewModel::setLimiterRelease,
            valueFormatter = { "%.1f ms".format(it) },
            enabled = limiter.enabled,
            step = 0.1f,
        )
    }
}
