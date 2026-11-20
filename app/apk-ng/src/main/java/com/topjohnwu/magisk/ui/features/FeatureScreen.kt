package com.topjohnwu.magisk.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.ui.component.GlassCard
import com.topjohnwu.magisk.ui.component.rememberExternalStoragePermissionLauncher
import com.topjohnwu.magisk.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScreen(viewModel: FeatureViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val packs by viewModel.packs.collectAsState()
    val packsLoading by viewModel.packsLoading.collectAsState()
    val runningPack by viewModel.runningPack.collectAsState()
    val packOutput by viewModel.packOutput.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showAppManagerNotice by rememberSaveable { mutableStateOf(false) }

    val launchBackup = rememberExternalStoragePermissionLauncher(
        onGranted = { viewModel.backupBoot() }
    )

    LaunchedEffect(Unit) { viewModel.loadPacks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.features_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.loadPacks() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(CoreR.string.features_packs_refresh)
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackupBootCard(
                backingUp = uiState.backingUp,
                enabled = Info.isRooted,
                onBackup = { launchBackup() }
            )

            VolumeRescueCard(
                installing = uiState.installingRescue,
                onInstall = viewModel::installVolumeRescue,
            )

            AppManagerCard(
                onOpen = { showAppManagerNotice = true }
            )

            PacksCard(
                packs = packs,
                loading = packsLoading,
                runningId = runningPack,
                onRun = viewModel::runPack
            )

            MoreFeaturesCard()
        }
    }

    if (showAppManagerNotice) {
        AlertDialog(
            onDismissRequest = { showAppManagerNotice = false },
            title = { Text(stringResource(CoreR.string.features_port_notice_title)) },
            text = { Text(stringResource(CoreR.string.features_port_notice_content)) },
            confirmButton = {
                TextButton(onClick = {
                    showAppManagerNotice = false
                    viewModel.openAppManager()
                }) {
                    Text(stringResource(CoreR.string.features_app_manager_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppManagerNotice = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    packOutput?.let { output ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPackOutput,
            title = {
                Text(
                    output.title + " · " + stringResource(
                        if (output.success) CoreR.string.features_packs_output
                        else CoreR.string.features_packs_failed
                    )
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = output.text.ifBlank {
                            stringResource(CoreR.string.features_packs_output_empty)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPackOutput) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun PacksCard(
    packs: List<FeatureViewModel.FeaturePack>,
    loading: Boolean,
    runningId: String?,
    onRun: (FeatureViewModel.FeaturePack) -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(CoreR.string.features_packs_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(CoreR.string.features_packs_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(CoreR.string.features_packs_refresh),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                packs.isEmpty() -> Text(
                    text = stringResource(CoreR.string.features_packs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> packs.forEachIndexed { index, pack ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pack.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (pack.desc.isNotEmpty()) {
                                Text(
                                    text = pack.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (pack.source == "local")
                                    stringResource(CoreR.string.features_packs_local)
                                else pack.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onRun(pack) },
                            enabled = runningId == null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (runningId == pack.id) stringResource(CoreR.string.features_packs_running)
                                else pack.button.ifEmpty { stringResource(CoreR.string.features_packs_run) }
                            )
                        }
                    }
                    if (index < packs.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppManagerCard(onOpen: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(CoreR.string.features_app_manager),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(CoreR.string.features_app_manager_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_hs),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreR.string.features_app_manager_open))
            }
        }
    }
}

@Composable
private fun BackupBootCard(
    backingUp: Boolean,
    enabled: Boolean,
    onBackup: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(CoreR.string.features_backup_boot),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(CoreR.string.features_backup_boot_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBackup,
                enabled = enabled && !backingUp,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (backingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreR.string.features_backup_boot_button))
            }
        }
    }
}

@Composable
private fun VolumeRescueCard(
    installing: Boolean,
    onInstall: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(CoreR.string.features_rescue_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(CoreR.string.features_rescue_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onInstall,
                enabled = !installing,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (installing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_hm),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CoreR.string.features_rescue_button))
            }
        }
    }
}

@Composable
private fun MoreFeaturesCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_extension),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(CoreR.string.features_more_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(CoreR.string.features_more_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
