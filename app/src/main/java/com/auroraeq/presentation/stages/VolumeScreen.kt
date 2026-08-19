package com.auroraeq.app.presentation.stages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auroraeq.app.presentation.eq.EqViewModel
import com.auroraeq.app.presentation.theme.GlassColors
import com.auroraeq.app.presentation.theme.GlassLabeledSlider
import com.auroraeq.app.presentation.theme.GlassToggle

/**
 * Earliest step in the Audio Management chain — controls the device's real system media volume
 * (STREAM_MUSIC) directly via AudioManager, ahead of Preamp. This is not a DynamicsProcessing stage
 * and has no bypass flag (it's real volume, not an effect to enable/disable); the standardized
 * "toggle" slot instead holds a Mute switch. Meant as a stand-in for the hardware volume buttons on
 * devices where they're unreliable or hard to reach.
 */
@Composable
fun VolumeScreen(viewModel: EqViewModel) {
    val volume by viewModel.volumeState.collectAsState()

    StageScreen(
        title = "Volume",
        subtitle =
            "Controls your device's system media volume directly from the app — " +
                "handy if the hardware volume buttons are unreliable or awkward to reach. " +
                "Applied first, before Preamp and the rest of the chain.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Mute",
                color = GlassColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            GlassToggle(checked = volume.isMuted, onCheckedChange = viewModel::setVolumeMuted)
        }
        GlassLabeledSlider(
            label = "Media volume",
            value = volume.percent,
            range = 0f..100f,
            onValueChange = viewModel::setVolumePercent,
            valueFormatter = { "%.0f%%".format(it) },
            // One system volume step (not a flat percent) — the system only has
            // `volume.max` discrete steps (commonly 15), so a fixed percent step
            // rounds unevenly onto that grid and used to nudge by 6% or 7% per
            // tap instead of a consistent amount. 100/max always lands exactly
            // on the next/previous step, giving the smallest, most consistent
            // nudge this control can actually make.
            step = if (volume.max > 0) 100f / volume.max else 1f,
        )
    }
}
