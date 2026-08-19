package com.auroraeq.app.presentation.stages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassLabeledSlider
import com.auroraeq.app.presentation.theme.formatDb

@Composable
fun CompressorScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val compressor = state.chain.compressor

    StageScreen(
        title = "Compressor",
        subtitle =
            "Evens out dynamic range — quiet passages come up and loud peaks come " +
                "down — for a more consistent, easier-to-hear level before the final Limiter.",
        enabled = compressor.enabled,
        onEnabledChange = viewModel::setCompressorEnabled,
        onReset = viewModel::resetCompressor,
    ) {
        GlassLabeledSlider(
            label = "Threshold",
            value = compressor.thresholdDb,
            range = -60f..0f,
            onValueChange = viewModel::setCompressorThreshold,
            valueFormatter = ::formatDb,
            enabled = compressor.enabled,
            step = 0.1f,
        )
        GlassLabeledSlider(
            label = "Ratio",
            value = compressor.ratio,
            range = 1f..20f,
            onValueChange = viewModel::setCompressorRatio,
            valueFormatter = { "%.1f:1".format(it) },
            enabled = compressor.enabled,
            step = 0.1f,
        )
        GlassLabeledSlider(
            label = "Attack",
            value = compressor.attackMs,
            range = 1f..100f,
            onValueChange = viewModel::setCompressorAttack,
            valueFormatter = { "%.1f ms".format(it) },
            enabled = compressor.enabled,
            step = 0.1f,
        )
        GlassLabeledSlider(
            label = "Release",
            value = compressor.releaseMs,
            range = 10f..500f,
            onValueChange = viewModel::setCompressorRelease,
            valueFormatter = { "%.1f ms".format(it) },
            enabled = compressor.enabled,
            step = 0.1f,
        )
    }
}
