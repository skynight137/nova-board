package com.auroraeq.app.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A pill-shaped glass chip, used for preset carousels and nav-like selectors. */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) GlassColors.TextPrimary else GlassColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier =
            modifier
                .clickable(onClick = onClick)
                .background(
                    if (selected) Color.White.copy(alpha = 0.20f)
                    else Color.White.copy(alpha = 0.06f),
                    shape,
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** A primary call-to-action button rendered as a glowing glass capsule. */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
            modifier
                .clickable(enabled = enabled, onClick = onClick)
                .background(GlassColors.accentGradient, shape)
                .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
