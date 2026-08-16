package com.topjohnwu.magisk.ui.features

import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.inputStream
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
