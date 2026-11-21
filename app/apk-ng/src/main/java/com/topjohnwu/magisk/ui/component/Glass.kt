package com.topjohnwu.magisk.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.topjohnwu.magisk.core.Config.Value.GLASS_ANDROID12
import com.topjohnwu.magisk.core.Config.Value.GLASS_LEGACY
import com.topjohnwu.magisk.ui.ThemeState

/**
 * Visual parameters of one liquid glass style.
 *
 * [scrimAlpha] dims the background bitmap so that text stays readable; [blurRadiusDp] is the
 * real blur radius and is only used when the platform can blur a layer at render time.
 */
data class GlassSpec(
    val cardTopDark: Float,
    val cardBottomDark: Float,
    val cardTopLight: Float,
    val cardBottomLight: Float,
    val surfaceDarkAlpha: Float,
    val surfaceLightAlpha: Float,
    val scrimAlpha: Float,
    val blurRadiusDp: Float,
)

private val AppleSpec =
    GlassSpec(0.32f, 0.16f, 0.72f, 0.52f, 0.62f, 0.88f, 0.06f, 28f)
private val Android12Spec =
    GlassSpec(0.26f, 0.12f, 0.60f, 0.40f, 0.52f, 0.80f, 0.05f, 20f)
private val LegacySpec =
    GlassSpec(0.20f, 0.08f, 0.80f, 0.60f, 0.74f, 0.92f, 0.20f, 0f)

/** Resolves the parameters of the currently selected glass style. */
fun glassSpec(style: Int): GlassSpec = when (style) {
    GLASS_ANDROID12 -> Android12Spec
    GLASS_LEGACY -> LegacySpec
    else -> AppleSpec
}

/**
 * Card container with the YuYu glass look: translucent vertical gradient plus a highlight border.
 * The blurred backdrop itself is rendered by the theme, so cards stay cheap to draw.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ThemeState.cardRadius.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val enabled = ThemeState.glassEnabled
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val surface = if (!enabled) {
        val container = CardDefaults.cardColors().containerColor
        Brush.verticalGradient(listOf(container, container))
    } else {
        val spec = glassSpec(ThemeState.glassStyle)
        val top = if (dark) spec.cardTopDark else spec.cardTopLight
        val bottom = if (dark) spec.cardBottomDark else spec.cardBottomLight
        Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom)))
    }

    val highlight = if (dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.45f))
        )
    }

    Box(
        modifier = modifier
            .then(
                if (!enabled) Modifier
                else Modifier.shadow(
                    elevation = 6.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.20f)
                )
            )
            .clip(shape)
            .background(surface)
            .then(
                if (!enabled) Modifier
                else Modifier.border(width = 1.dp, brush = highlight, shape = shape)
            )
    ) {
        content()
    }
}
