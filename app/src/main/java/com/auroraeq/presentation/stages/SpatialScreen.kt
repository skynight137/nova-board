package com.auroraeq.app.presentation.stages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassLabeledSlider

/**
 * Cross-channel spatial/phase widening (native Virtualizer). Kept as its own stage — unlike Sub
 * Shelf/Air Shelf, nothing in the EQ chain replicates what Virtualizer does (refactor spec section
 * 9).
 */
@Composable
fun SpatialScreen(viewModel: EqViewModel) {
    val state by viewModel.uiState.collectAsState()
    val spatial = state.chain.spatial

    StageScreen(
        title = "Spatial",
        subtitle =
            "Widens the stereo image using cross-channel processing (Virtualizer) " +
                "for a larger, more immersive soundstage — most noticeable on headphones.",
        enabled = spatial.enabled,
        onEnabledChange = viewModel::setSpatialEnabled,
        onReset = viewModel::resetSpatial,
    ) {
        GlassLabeledSlider(
            label = "Strength",
            value = spatial.strength.toFloat(),
            range = 0f..1000f,
            onValueChange = { viewModel.setSpatialStrength(it.toInt()) },
            valueFormatter = { "%.1f%%".format(it / 10f) },
            enabled = spatial.enabled,
            // strength is stored as an Int in tenths-of-a-percent (0..1000 for
            // 0..100.0%); 1 is the smallest unit the underlying value actually
            // has, i.e. the Int-scale equivalent of Float's 0.1 step elsewhere.
            step = 1f,
        )
    }
}
