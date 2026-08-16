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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.topjohnwu.magisk.core.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val YuYuThemeBlue = Color(0xFF91D5FF)

object ThemeState {
    var colorMode by mutableIntStateOf(Config.colorMode)
    var glassEffect by mutableStateOf(Config.glassEffect)
}

@Composable
fun MagiskTheme(
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val mode = ThemeState.colorMode
    val context = LocalContext.current

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

    val finalColorScheme = if (ThemeState.glassEffect) {
        val a = 0.55f
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

    MaterialTheme(
        colorScheme = finalColorScheme,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                CustomBackgroundImage(isDarkTheme = isDarkTheme)
                content()
            }
        }
    )
}

@Composable
private fun CustomBackgroundImage(isDarkTheme: Boolean) {
    val uriString = Config.backgroundImage
    if (uriString.isBlank()) return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uri = remember(uriString) { Uri.parse(uriString) }
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            decodeSampledBitmap(
                context.contentResolver,
                uri,
                configuration.screenWidthDp * 2,
                configuration.screenHeightDp * 2
            )
        }
    }

    val bmp = bitmap
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
