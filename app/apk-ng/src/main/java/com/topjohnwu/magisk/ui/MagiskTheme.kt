package com.topjohnwu.magisk.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Config.Value.GLASS_BLUR
import com.topjohnwu.magisk.core.Config.Value.GLASS_NONE
import com.topjohnwu.magisk.ui.component.GlassBackdrop
import com.topjohnwu.magisk.ui.component.LocalGlassBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

val YuYuThemeBlue = Color(0xFF91D5FF)

object ThemeState {
    var colorMode by mutableIntStateOf(Config.colorMode)
    var glassMode by mutableIntStateOf(Config.glassMode)
}

@Composable
fun MagiskTheme(
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val mode = ThemeState.colorMode
    val glassMode = ThemeState.glassMode
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val isDarkTheme = when (mode) {
        1 -> false
        2 -> true
        3 -> isDark
        4 -> false
        5 -> true
        else -> isDark
    }

    val useDynamicColor = mode in listOf(3, 4, 5) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseColorScheme = when {
        useDynamicColor && isDarkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !isDarkTheme -> dynamicLightColorScheme(context)
        isDarkTheme -> darkColorScheme(primary = YuYuThemeBlue)
        else -> lightColorScheme(primary = YuYuThemeBlue)
    }

    val finalColorScheme = if (glassMode != GLASS_NONE) {
        val a = if (isDarkTheme) 0.55f else 0.80f
        baseColorScheme.copy(
            surface = baseColorScheme.surface.copy(alpha = a),
            surfaceVariant = baseColorScheme.surfaceVariant.copy(alpha = a),
            surfaceContainer = baseColorScheme.surfaceContainer.copy(alpha = a),
            surfaceContainerLow = baseColorScheme.surfaceContainerLow.copy(alpha = a),
            surfaceContainerLowest = baseColorScheme.surfaceContainerLowest.copy(alpha = a),
            surfaceContainerHigh = baseColorScheme.surfaceContainerHigh.copy(alpha = a),
            surfaceContainerHighest = baseColorScheme.surfaceContainerHighest.copy(alpha = a)
        )
    } else baseColorScheme

    val bgUri = Config.backgroundImage
    var background by remember(bgUri) { mutableStateOf<Bitmap?>(null) }
    var backdrop by remember(bgUri, glassMode) { mutableStateOf<ImageBitmap?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(bgUri) {
        background = if (bgUri.isBlank()) null else withContext(Dispatchers.IO) {
            decodeSampledBitmap(
                context.contentResolver,
                Uri.parse(bgUri),
                configuration.screenWidthDp * 2,
                configuration.screenHeightDp * 2
            )
        }
    }

    LaunchedEffect(background, glassMode) {
        val src = background
        backdrop = if (glassMode != GLASS_BLUR || src == null) null else withContext(Dispatchers.IO) {
            blurBitmap(src).asImageBitmap()
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rootSize = it.size }
            ) {
                val bmp = background
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Scrim to keep content readable regardless of the image brightness
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isDarkTheme) 0.35f else 0.10f))
                    )
                }
                val currentBackdrop = backdrop
                CompositionLocalProvider(
                    LocalGlassBackdrop provides if (currentBackdrop == null) null
                    else GlassBackdrop(currentBackdrop, rootSize.width, rootSize.height)
                ) {
                    content()
                }
            }
        }
    )
}

/**
 * Cheap pseudo blur: downscale, then upscale back while keeping the aspect ratio.
 * Works on every API level, so the blur mode is not limited to Android 12+.
 */
private fun blurBitmap(src: Bitmap): Bitmap {
    val smallWidth = max(1, src.width / 12)
    val smallHeight = max(1, src.height / 12)
    val small = Bitmap.createScaledBitmap(src, smallWidth, smallHeight, true)
    val outHeight = max(1, src.height / 3)
    val outWidth = max(1, (outHeight.toLong() * src.width / src.height).toInt())
    val out = Bitmap.createScaledBitmap(small, outWidth, outHeight, true)
    if (small !== out) small.recycle()
    return out
}

private fun decodeSampledBitmap(
    cr: ContentResolver,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqWidth &&
        bounds.outHeight / (sample * 2) >= reqHeight
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()
