package com.topjohnwu.magisk.ui.appmanager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import com.topjohnwu.magisk.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel, onBack: () -> Unit) {
    val loading by viewModel.loading.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()
    val query by viewModel.query.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val busy by viewModel.busy.collectAsState()

    val installPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.installApkFromUri(it) } }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.app_manager_title)) },
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
                },
                actions = {
                    IconButton(onClick = { installPicker.launch(arrayOf("application/vnd.android.package-archive")) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(CoreR.string.app_manager_install_apk),
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(CoreR.string.app_manager_search_hint)) },
                singleLine = true
            )

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(app = app, onClick = { viewModel.select(app) })
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AppDetailDialog(app = app, busy = busy, viewModel = viewModel)
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.icon?.let {
            Icon(
                painter = rememberDrawablePainter(it),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = app.packageName + if (app.isSystem) " · ${stringResource(CoreR.string.app_manager_system)}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (app.isAdSuspect) {
            Text(
                text = stringResource(CoreR.string.app_manager_ad),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AppDetailDialog(
    app: AppEntry,
    busy: Boolean,
    viewModel: AppManagerViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismiss() },
        title = { Text(app.label) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DetailRow(stringResource(CoreR.string.app_manager_package), app.packageName)
                DetailRow(
                    stringResource(CoreR.string.app_manager_version),
                    "${app.versionName} (${app.versionCode})"
                )
                DetailRow(stringResource(CoreR.string.app_manager_size), formatSize(app.apkSize))
                DetailRow(stringResource(CoreR.string.app_manager_uid), app.uid.toString())
                DetailRow(stringResource(CoreR.string.app_manager_target_sdk), app.targetSdk.toString())
                DetailRow(stringResource(CoreR.string.app_manager_signature), app.signature)
                DetailRow(
                    stringResource(CoreR.string.app_manager_system),
                    stringResource(if (app.isSystem) CoreR.string.app_manager_yes else CoreR.string.app_manager_no)
                )
                DetailRow(
                    stringResource(CoreR.string.app_manager_status),
                    stringResource(if (app.isEnabled) CoreR.string.app_manager_enabled else CoreR.string.app_manager_disabled)
                )
                DetailRow(
                    stringResource(CoreR.string.app_manager_boot),
                    stringResource(if (app.isBootReceiver) CoreR.string.app_manager_yes else CoreR.string.app_manager_no)
                )
                DetailRow(
                    stringResource(CoreR.string.app_manager_ad),
                    stringResource(if (app.isAdSuspect) CoreR.string.app_manager_yes else CoreR.string.app_manager_no)
                )
                DetailRow(stringResource(CoreR.string.app_manager_install_time), formatDate(app.firstInstallTime))
                DetailRow(stringResource(CoreR.string.app_manager_update_time), formatDate(app.lastUpdateTime))

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (app.isEnabled) viewModel.freeze(app.packageName)
                            else viewModel.unfreeze(app.packageName)
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                if (app.isEnabled) CoreR.string.app_manager_freeze
                                else CoreR.string.app_manager_unfreeze
                            )
                        )
                    }
                    Button(
                        onClick = { viewModel.forceStop(app.packageName) },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(CoreR.string.app_manager_stop))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.clearData(app.packageName) },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(CoreR.string.app_manager_clear_data))
                    }
                    Button(
                        onClick = { viewModel.extractApk(app) },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(CoreR.string.app_manager_extract))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.uninstall(app.packageName) },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(CoreR.string.app_manager_uninstall))
                }

                if (app.components.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(CoreR.string.app_manager_components),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    app.components.forEachIndexed { index, comp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${comp.kind} · ${comp.name.substringAfterLast('.')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                Text(
                                    text = comp.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    viewModel.setComponent(
                                        app.packageName, comp.name, !comp.isEnabled
                                    )
                                }
                            ) {
                                Text(
                                    stringResource(
                                        if (comp.isEnabled) CoreR.string.app_manager_disable
                                        else CoreR.string.app_manager_enable
                                    ),
                                    color = if (comp.isEnabled) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                        if (index < app.components.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.dismiss() }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return String.format(Locale.US, "%.1f %s", value, units[idx])
}

private fun formatDate(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}
