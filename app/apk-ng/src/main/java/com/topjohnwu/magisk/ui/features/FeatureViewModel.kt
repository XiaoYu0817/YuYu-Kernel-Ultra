package com.topjohnwu.magisk.ui.features

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.ktx.writeTo
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.inputStream
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.magisk.ui.MainActivity
import com.topjohnwu.magisk.ui.flash.FlashUtils
import com.topjohnwu.magisk.ui.navigation.Route
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils.fastCmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.topjohnwu.magisk.core.R as CoreR

class FeatureViewModel : BaseViewModel() {

    data class UiState(
        val backingUp: Boolean = false,
        val installingRescue: Boolean = false,
    )

    data class FeaturePack(
        val id: String,
        val path: String,
        val name: String,
        val desc: String,
        val button: String,
        val source: String,
        val order: Int,
    )

    data class PackOutput(
        val title: String,
        val success: Boolean,
        val text: String,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _packs = MutableStateFlow<List<FeaturePack>>(emptyList())
    val packs: StateFlow<List<FeaturePack>> = _packs.asStateFlow()

    private val _packsLoading = MutableStateFlow(true)
    val packsLoading: StateFlow<Boolean> = _packsLoading.asStateFlow()

    private val _runningPack = MutableStateFlow<String?>(null)
    val runningPack: StateFlow<String?> = _runningPack.asStateFlow()

    private val _packOutput = MutableStateFlow<PackOutput?>(null)
    val packOutput: StateFlow<PackOutput?> = _packOutput.asStateFlow()

    fun loadPacks() {
        viewModelScope.launch {
            _packsLoading.value = true
            _packs.value = withContext(Dispatchers.IO) {
                runCatching { scanPacks() }.getOrDefault(emptyList())
            }
            _packsLoading.value = false
        }
    }

    fun runPack(pack: FeaturePack) {
        if (_runningPack.value != null) return
        viewModelScope.launch {
            _runningPack.value = pack.id
            val result = withContext(Dispatchers.IO) {
                Shell.cmd("cd '${pack.path}' && sh run.sh 2>&1").exec()
            }
            _runningPack.value = null
            val text = (result.out + result.err).filter { it.isNotBlank() }.joinToString("\n")
            _packOutput.value = PackOutput(pack.name, result.isSuccess, text)
            if (result.isSuccess) loadPacks()
        }
    }

    fun dismissPackOutput() {
        _packOutput.value = null
    }

    private fun scanPacks(): List<FeaturePack> {
        val dirs = fastCmd(
            "ls -d /data/adb/yuyu/features/* /data/adb/modules/*/yuyu/features/* 2>/dev/null"
        ).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return dirs.mapNotNull { dir ->
            val prop = fastCmd("cat '$dir/feature.prop' 2>/dev/null")
            if (prop.isBlank()) return@mapNotNull null
            val map = prop.lineSequence()
                .mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                }
                .toMap()
            val segments = dir.split("/")
            val source = if (dir.startsWith("/data/adb/yuyu/features/")) {
                "local"
            } else {
                segments.getOrNull(4).orEmpty()
            }
            FeaturePack(
                id = "$source/${segments.last()}",
                path = dir,
                name = map["name"] ?: segments.last(),
                desc = map["desc"].orEmpty(),
                button = map["button"].orEmpty(),
                source = source,
                order = map["order"]?.toIntOrNull() ?: 100,
            )
        }.sortedWith(compareBy({ it.order }, { it.name }))
    }

    fun backupBoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(backingUp = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching { backupBootInternal() }
            }
            _uiState.update { it.copy(backingUp = false) }
            result
                .onSuccess { showSnackbar(CoreR.string.features_backup_done) }
                .onFailure { e ->
                    showSnackbar(
                        AppContext.getString(CoreR.string.features_backup_fail, e.message ?: "")
                    )
                }
        }
    }

    fun installVolumeRescue() {
        viewModelScope.launch {
            _uiState.update { it.copy(installingRescue = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching { copyRescueZip() }
            }
            _uiState.update { it.copy(installingRescue = false) }
            result
                .onSuccess { uri -> launchFlashInstall(uri) }
                .onFailure { e ->
                    showSnackbar(
                        AppContext.getString(CoreR.string.features_rescue_fail, e.message ?: "")
                    )
                }
        }
    }

    fun openAppManager() {
        navigateTo(Route.AppManager)
    }

    private fun copyRescueZip(): Uri {
        val zip = File(AppContext.cacheDir, "volume_rescue.zip")
        AppContext.assets.open("volume_rescue.zip").use { input ->
            zip.outputStream().use { output -> input.copyTo(output) }
        }
        return zip.toUri()
    }

    private fun launchFlashInstall(uri: Uri) {
        val intent = Intent(AppContext, MainActivity::class.java).apply {
            action = FlashUtils.INTENT_FLASH
            putExtra(FlashUtils.EXTRA_FLASH_ACTION, Const.Value.FLASH_ZIP)
            putExtra(FlashUtils.EXTRA_FLASH_URI, uri.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        AppContext.startActivity(intent)
    }

    private fun backupBootInternal() {
        // Locate the current boot partition with magisk's find_boot_image utility
        val bootPath = fastCmd(
            "RECOVERYMODE=${Config.recovery} VENDORBOOT=${Info.isVendorBoot} " +
                "SLOT=${Info.slot} find_boot_image; echo \$BOOTIMAGE"
        ).lineSequence().lastOrNull { it.isNotBlank() }.orEmpty()
        check(bootPath.isNotEmpty()) { "boot partition not found" }

        // Dump the boot image into the app cache as root, then copy to Downloads
        val cacheFile = File(AppContext.cacheDir, "boot_backup.img")
        cacheFile.delete()
        fastCmd("dd if='$bootPath' of='${cacheFile.path}' bs=1M 2>/dev/null")
        check(cacheFile.length() > 0) { "failed to read $bootPath" }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val uriFile = MediaStoreUtils.getFile("boot_backup-$timestamp.img")
        uriFile.uri.outputStream().use { out ->
            cacheFile.inputStream().use { it.copyTo(out) }
        }
        cacheFile.delete()
    }
}
