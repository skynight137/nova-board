package com.auroraeq.app.presentation.stages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraeq.app.presentation.theme.GlassColors
import com.auroraeq.app.presentation.theme.GlassPanel
import com.auroraeq.app.presentation.theme.GlassToggle

/**
 * Shared full-screen scaffold for every Audio Management child screen (Volume, Preamp, HPF, Sub
 * Shelf, EQ, Air Shelf, LPF, Compressor, Limiter, Output Gain, Spatial). Standardized layout, top
 * to bottom: title (plus an optional Reset-to-default icon button when [onReset] is provided),
 * description of what the stage does and when to use it, an optional ON/OFF toggle (this stage's
 * own independent enabled flag, bound 1:1 to its own state, never a shared master flag — refactor
 * spec section 4; a screen with no bypass concept, like Volume, simply omits it by passing `enabled
 * = null`), then the controller(s) in a glass panel.
 */
@Composable
fun StageScreen(
    title: String,
    subtitle: String? = null,
    enabled: Boolean? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val columnModifier =
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp).let {
            if (scrollable) it.verticalScroll(rememberScrollState()) else it
        }

    Column(modifier = columnModifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = GlassColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (onReset != null) {
                    IconButton(onClick = onReset) {
                        Icon(
                            Icons.Filled.RestartAlt,
                            contentDescription = "Reset to default",
                            tint = GlassColors.TextSecondary,
                        )
                    }
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = GlassColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        if (enabled != null && onEnabledChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Enabled",
                    color = GlassColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                GlassToggle(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                content()
            }
        }
    }
}
