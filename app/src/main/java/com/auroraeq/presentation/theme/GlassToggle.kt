package com.auroraeq.app.presentation.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A glowing glass switch used for master toggles (EQ enabled, Global Mode, etc). */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset by
        animateDpAsState(
            targetValue = if (checked) 22.dp else 2.dp,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "toggleOffset",
        )
    val trackColor =
        if (checked) GlassColors.Violet.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)

    Box(
        modifier =
            modifier
                .width(48.dp)
                .height(28.dp)
                .background(trackColor, CircleShape)
                .clickable { onCheckedChange(!checked) }
                .padding(2.dp)
    ) {
        Box(
            modifier =
                Modifier.offset(x = offset)
                    .size(24.dp)
                    .background(
                        if (checked) GlassColors.accentGradient.let { Color.White }
                        else Color.White.copy(alpha = 0.8f),
                        CircleShape,
                    )
        )
    }
}
