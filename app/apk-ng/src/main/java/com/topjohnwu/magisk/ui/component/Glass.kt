package com.topjohnwu.magisk.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.topjohnwu.magisk.core.Config.Value.GLASS_NONE
import com.topjohnwu.magisk.ui.ThemeState
import kotlin.math.max
import kotlin.math.roundToInt

/** Blurred copy of the app background image, used to fake backdrop blur inside cards. */
class GlassBackdrop(
    val bitmap: ImageBitmap,
    val rootWidth: Int,
    val rootHeight: Int,
)

val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Card container with the YuYu glass look: translucent vertical gradient, a highlight
 * border and, in blur mode, the blurred background sampled at this card's position.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ThemeState.cardRadius.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val mode = ThemeState.glassMode
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backdrop = LocalGlassBackdrop.current
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    val surface = if (mode == GLASS_NONE) {
        val container = CardDefaults.cardColors().containerColor
        Brush.verticalGradient(listOf(container, container))
    } else if (dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.55f))
        )
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
            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
            .then(
                if (mode == GLASS_NONE) Modifier
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
                if (mode == GLASS_NONE) Modifier
                else Modifier.border(width = 1.dp, brush = highlight, shape = shape)
            )
    ) {
        if (mode != GLASS_NONE && backdrop != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val image = backdrop.bitmap
                val scale = max(
                    backdrop.rootWidth.toFloat() / image.width,
                    backdrop.rootHeight.toFloat() / image.height
                )
                val drawWidth = image.width * scale
                val drawHeight = image.height * scale
                val originX = (backdrop.rootWidth - drawWidth) / 2f
                val originY = (backdrop.rootHeight - drawHeight) / 2f
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(
                        (originX - positionInRoot.x).roundToInt(),
                        (originY - positionInRoot.y).roundToInt()
                    ),
                    dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
                    filterQuality = FilterQuality.Low
                )
            }
        }
        content()
    }
}
