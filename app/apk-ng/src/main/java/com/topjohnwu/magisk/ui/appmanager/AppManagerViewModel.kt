package com.topjohnwu.magisk.ui.appmanager

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.AsyncLoadViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.ktx.writeTo
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.inputStream
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import com.topjohnwu.magisk.core.R as CoreR

data class AppComponentInfo(
    val name: String,
    val kind: String,
    val isEnabled: Boolean,
)

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val apkSize: Long,
    val uid: Int,
    val signature: String,
    val isBootReceiver: Boolean,
    val isAdSuspect: Boolean,
    val sourceDir: String,
    val components: List<AppComponentInfo>,
)

class AppManagerViewModel : AsyncLoadViewModel() {

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<AppEntry?>(null)
    val selected: StateFlow<AppEntry?> = _selected.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val filteredApps: StateFlow<List<AppEntry>> = combine(
        _allApps, _query
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val apps = args[0] as List<AppEntry>
        val q = args[1] as String
        val filtered = if (q.isBlank()) apps else apps.filter {
            it.label.contains(q, true) || it.packageName.contains(q, true)
        }
        filtered.sortedWith(
            compareBy<String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    fun select(app: AppEntry) { _selected.value = app }
    fun dismiss() { _selected.value = null }

    fun freeze(pkg: String) = runAction("pm disable-user --user 0 $pkg")
    fun unfreeze(pkg: String) = runAction("pm enable $pkg")
    fun forceStop(pkg: String) = runAction("am force-stop $pkg")
    fun clearData(pkg: String) = runAction("pm clear $pkg")
    fun uninstall(pkg: String) = runAction("pm uninstall --user 0 $pkg")
    fun setComponent(pkg: String, name: String, enabled: Boolean) =
        runAction("pm ${if (enabled) "enable" else "disable"} $pkg/$name")
    fun installApk(path: String) = runAction("pm install -r -d \"$path\"")

    fun installApkFromUri(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(AppContext.cacheDir, "install.apk")
                    uri.inputStream().use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                    f.path
                }.getOrNull()
            }
            _busy.value = false
            if (path != null) {
                installApk(path)
            } else {
                AppContext.toast(CoreR.string.app_manager_install_fail, Toast.LENGTH_SHORT)
            }
        }
    }

    fun extractApk(entry: AppEntry) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { extractInternal(entry) }
            }
            _busy.value = false
            result
                .onSuccess { AppContext.toast(CoreR.string.app_manager_extract_done, Toast.LENGTH_SHORT) }
                .onFailure { e ->
                    AppContext.toast(
                        AppContext.getString(CoreR.string.app_manager_extract_fail, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    )
                }
        }
    }

    private fun extractInternal(entry: AppEntry) {
        val src = File(entry.sourceDir)
        check(src.exists()) { "APK not found" }
        val name = "${entry.label}-${entry.versionName}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val uriFile = MediaStoreUtils.getFile("$name.apk")
        uriFile.uri.outputStream().use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
    }

    private fun runAction(cmd: String) {
        viewModelScope.launch {
            _busy.value = true
            val ok = withContext(Dispatchers.IO) { Shell.cmd(cmd).exec().isSuccess }
            _busy.value = false
            AppContext.toast(
                if (ok) CoreR.string.app_manager_done else CoreR.string.app_manager_fail,
                Toast.LENGTH_SHORT
            )
            if (ok) startLoading()
        }
    }

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        _loading.value = true
        val apps = withContext(Dispatchers.Default) {
            val pm = AppContext.packageManager
            val infos = pm.getInstalledApplications(
                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    PackageManager.MATCH_DISABLED_COMPONENTS
            )
            infos.mapNotNull { ai ->
                runCatching { buildEntry(ai, pm) }.getOrNull()
            }
        }
        _allApps.value = apps
        _loading.value = false
    }

    private fun buildEntry(ai: ApplicationInfo, pm: PackageManager): AppEntry {
        val info = pm.getPackageInfo(
            ai.packageName,
            PackageManager.GET_SIGNATURES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions.orEmpty()
        val hasBootReceiver = requested.contains("android.permission.RECEIVE_BOOT_COMPLETED")
        val isAdSuspect = !ai.isSystemAppCompat() &&
            requested.contains("android.permission.INTERNET") &&
            requested.contains("android.permission.READ_PHONE_STATE") &&
            requested.contains("android.permission.SYSTEM_ALERT_WINDOW")
        val components = buildList {
            info.activities.orEmpty().forEach { add(AppComponentInfo(it.name, "A", compEnabled(pm, ai.packageName, it.name))) }
            info.receivers.orEmpty().forEach { add(AppComponentInfo(it.name, "R", compEnabled(pm, ai.packageName, it.name))) }
            info.services.orEmpty().forEach { add(AppComponentInfo(it.name, "S", compEnabled(pm, ai.packageName, it.name))) }
        }
        return AppEntry(
            label = ai.loadLabel(pm).toString(),
            packageName = ai.packageName,
            icon = ai.loadIcon(pm),
            versionName = info.versionName ?: "",
            versionCode = info.versionCodeCompat(),
            targetSdk = ai.targetSdkVersion,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            isSystem = ai.isSystemAppCompat(),
            isEnabled = ai.enabled,
            apkSize = File(ai.sourceDir).length(),
            uid = ai.uid,
            signature = info.signatures?.firstOrNull()?.toByteArray()?.let { sha1(it).take(16) } ?: "",
            isBootReceiver = hasBootReceiver,
            isAdSuspect = isAdSuspect,
            sourceDir = ai.sourceDir,
            components = components,
        )
    }

    private fun compEnabled(pm: PackageManager, pkg: String, name: String): Boolean {
        return pm.getComponentEnabledSetting(android.content.ComponentName(pkg, name)) !=
            COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02X".format(it) }
    }
}

private fun ApplicationInfo.isSystemAppCompat(): Boolean =
    (flags and ApplicationInfo.FLAG_SYSTEM) != 0

private fun PackageInfo.versionCodeCompat(): Long {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        versionCodeLong else versionCode.toLong()
}
