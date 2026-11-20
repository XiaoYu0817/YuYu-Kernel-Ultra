package com.topjohnwu.magisk.ui.dev

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.ui.Tab
import com.topjohnwu.magisk.ui.ThemeState
import com.topjohnwu.magisk.ui.YuYuThemeBlue
import com.topjohnwu.magisk.ui.component.GlassCard
import com.topjohnwu.magisk.ui.component.SettingsArrow
import com.topjohnwu.magisk.ui.component.SettingsSwitch
import com.topjohnwu.magisk.ui.component.SmallTitle
import kotlin.math.roundToInt
import com.topjohnwu.magisk.core.R as CoreR

/** Hard-coded developer password. Change this constant and rebuild to change it. */
private const val DEV_PASSWORD = "yuyu2026"

/** Unlock state lives in memory only, so a cold start locks the page again. */
private object DeveloperGate {
    var unlocked by mutableStateOf(false)
}

/** Preset accent colors offered in the developer options. */
private val ACCENT_PRESETS = listOf(
    0xFF91D5FF, 0xFF7CB7FF, 0xFF4C9AFF, 0xFF6C63FF,
    0xFF9C6BFF, 0xFFFF6B9D, 0xFFFF7A45, 0xFF32C48D,
    0xFF00C2C7, 0xFFB0BEC5
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.settings_developer_options)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (!DeveloperGate.unlocked) {
            PasswordGate(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 32.dp)
            ) {
                AppearanceSection()
                Spacer(Modifier.height(12.dp))
                GlassSection()
                Spacer(Modifier.height(12.dp))
                NavigationSection()
                Spacer(Modifier.height(12.dp))
                ResetSection()
            }
        }
    }
}

// --- Password gate ---

@Composable
private fun PasswordGate(modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(CoreR.string.dev_password_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(CoreR.string.dev_password_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error,
            label = { Text(stringResource(CoreR.string.dev_password_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            supportingText = if (error) {
                { Text(stringResource(CoreR.string.dev_password_error)) }
            } else null
        )
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (input == DEV_PASSWORD) DeveloperGate.unlocked = true else error = true
            }
        ) {
            Text(stringResource(CoreR.string.dev_password_confirm))
        }
    }
}

// --- Appearance ---

@Composable
private fun AppearanceSection() {
    SmallTitle(text = stringResource(CoreR.string.dev_appearance))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(CoreR.string.dev_primary_color),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AccentColorRow()
            SliderRow(
                title = stringResource(CoreR.string.dev_card_radius),
                value = ThemeState.cardRadius,
                range = 0f..48f,
                valueText = "${ThemeState.cardRadius} dp",
                onChange = {
                    Config.devCardRadius = it
                    ThemeState.cardRadius = it
                }
            )
            SliderRow(
                title = stringResource(CoreR.string.dev_font_scale),
                value = ThemeState.fontScale,
                range = 70f..150f,
                valueText = "${ThemeState.fontScale}%",
                onChange = {
                    Config.devFontScale = it
                    ThemeState.fontScale = it
                }
            )
        }
    }
}

@Composable
private fun AccentColorRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentSwatch(
            color = YuYuThemeBlue,
            selected = ThemeState.primaryColor == 0,
            onClick = {
                Config.devPrimaryColor = 0
                ThemeState.primaryColor = 0
            }
        )
        ACCENT_PRESETS.forEach { argb ->
            AccentSwatch(
                color = Color(argb),
                selected = ThemeState.primaryColor == argb,
                onClick = {
                    Config.devPrimaryColor = argb
                    ThemeState.primaryColor = argb
                }
            )
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

// --- Glass ---

@Composable
private fun GlassSection() {
    SmallTitle(text = stringResource(CoreR.string.settings_glass_title))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            SliderRow(
                title = stringResource(CoreR.string.dev_glass_alpha_dark),
                value = ThemeState.glassAlphaDark,
                range = 0f..100f,
                valueText = "${ThemeState.glassAlphaDark}%",
                onChange = {
                    Config.devGlassAlphaDark = it
                    ThemeState.glassAlphaDark = it
                }
            )
            SliderRow(
                title = stringResource(CoreR.string.dev_glass_alpha_light),
                value = ThemeState.glassAlphaLight,
                range = 0f..100f,
                valueText = "${ThemeState.glassAlphaLight}%",
                onChange = {
                    Config.devGlassAlphaLight = it
                    ThemeState.glassAlphaLight = it
                }
            )
            SliderRow(
                title = stringResource(CoreR.string.dev_scrim_dark),
                value = ThemeState.scrimDark,
                range = 0f..100f,
                valueText = "${ThemeState.scrimDark}%",
                onChange = {
                    Config.devScrimDark = it
                    ThemeState.scrimDark = it
                }
            )
            SliderRow(
                title = stringResource(CoreR.string.dev_scrim_light),
                value = ThemeState.scrimLight,
                range = 0f..100f,
                valueText = "${ThemeState.scrimLight}%",
                onChange = {
                    Config.devScrimLight = it
                    ThemeState.scrimLight = it
                }
            )
        }
    }
}

// --- Navigation bar ---

@Composable
private fun NavigationSection() {
    SmallTitle(text = stringResource(CoreR.string.dev_navigation))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            SliderRow(
                title = stringResource(CoreR.string.dev_nav_height),
                value = ThemeState.navHeight,
                range = 48f..96f,
                valueText = "${ThemeState.navHeight} dp",
                onChange = {
                    Config.devNavHeight = it
                    ThemeState.navHeight = it
                }
            )
            SettingsSwitch(
                title = stringResource(CoreR.string.dev_nav_labels),
                checked = ThemeState.navLabels,
                onCheckedChange = {
                    Config.devNavLabels = it
                    ThemeState.navLabels = it
                }
            )
            Text(
                text = stringResource(CoreR.string.dev_nav_order),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            TabOrderEditor()
        }
    }
}

@Composable
private fun TabOrderEditor() {
    var order by remember { mutableStateOf(initialTabOrder()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        order.forEachIndexed { index, tab ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(tab.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                )
                IconButton(
                    enabled = index > 0,
                    onClick = {
                        val moved = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                        order = moved
                        applyTabOrder(moved)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(CoreR.string.dev_move_up)
                    )
                }
                IconButton(
                    enabled = index < order.lastIndex,
                    onClick = {
                        val moved = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                        order = moved
                        applyTabOrder(moved)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(CoreR.string.dev_move_down)
                    )
                }
            }
        }
    }
}

private fun initialTabOrder(): List<Tab> {
    val saved = ThemeState.navOrder.split(",").filter { it.isNotBlank() }
    val known = saved.mapNotNull { name -> Tab.entries.firstOrNull { it.name == name } }
    return known + Tab.entries.filterNot { it in known }
}

private fun applyTabOrder(order: List<Tab>) {
    val value = order.joinToString(",") { it.name }
    Config.devNavOrder = value
    ThemeState.navOrder = value
}

// --- Factory reset ---

@Composable
private fun ResetSection() {
    val activity = LocalActivity.current
    var confirm by remember { mutableStateOf(false) }

    SmallTitle(text = stringResource(CoreR.string.settings_developer_section))
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        SettingsArrow(
            title = stringResource(CoreR.string.dev_reset_title),
            summary = stringResource(CoreR.string.dev_reset_summary),
            onClick = { confirm = true }
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(CoreR.string.dev_reset_title)) },
            text = { Text(stringResource(CoreR.string.dev_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        Config.resetUiCustomization()
                        ThemeState.resetToDefaults()
                        activity?.recreate()
                    }
                ) {
                    Text(stringResource(CoreR.string.dev_reset_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

// --- Shared widgets ---

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range
        )
    }
}
