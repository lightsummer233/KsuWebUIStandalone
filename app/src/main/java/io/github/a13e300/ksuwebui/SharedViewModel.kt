package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SharedViewModel : ViewModel() {

    sealed interface Status {
        data object Loading : Status
        data object Updating : Status
        data object Unavailable : Status
        data object Ready : Status
    }

    val moduleListStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val packageInfoListStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val colorSchemeStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    private val connector = FileSystemConnector(App.instance)

    val fsState: StateFlow<FileSystemConnector.State> = connector.state

    val fsStatus: StateFlow<Status> = connector.state.map {
        when (it) {
            FileSystemConnector.State.Connecting -> Status.Loading
            FileSystemConnector.State.Unavailable -> Status.Unavailable
            is FileSystemConnector.State.Available -> Status.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Status.Loading)

    val status: StateFlow<Status> = combine(
        moduleListStatus,
        packageInfoListStatus,
        fsStatus,
        colorSchemeStatus
    ) { moduleStatus, packageStatus, fsStatus, colorSchemeStatus ->
        if (fsStatus == Status.Unavailable || moduleStatus == Status.Unavailable || packageStatus == Status.Unavailable) {
            Status.Unavailable
        } else if (moduleStatus == Status.Loading || packageStatus == Status.Loading || fsStatus == Status.Loading || colorSchemeStatus == Status.Loading) {
            Status.Loading
        } else {
            Status.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Status.Loading)

    fun initialize() {
        viewModelScope.launch { refreshPackageInfoList() }

        viewModelScope.launch { connector.connect() }

        viewModelScope.launch {
            connector.state.filterIsInstance<FileSystemConnector.State.Available>().collect { available ->
                moduleListStatus.getAndUpdate { if (it is Status.Ready) Status.Updating else it }
                refreshModuleList(available.fs)
                moduleListStatus.emit(Status.Ready)
            }
        }
    }

    @Immutable
    data class Module(
        val name: String,
        val id: String,
        val desc: String,
        val author: String,
        val version: String
    )

    val moduleList: StateFlow<List<Module>>
        field = MutableStateFlow(listOf())

    fun refreshModuleListWithStatus() {
        viewModelScope.launch {
            moduleListStatus.getAndUpdate { if (it is Status.Ready) Status.Updating else it }

            val state = connector.state.value
            if (state is FileSystemConnector.State.Available) {
                refreshModuleList(state.fs)
                moduleListStatus.emit(Status.Ready)
            }
        }
    }

    private suspend fun refreshModuleList(fs: FileSystemManager) = withContext(Dispatchers.IO) {
        val newModuleList = mutableListOf<Module>()
        val showDisabled = App.prefs.getBoolean("show_disabled", false)
        val files = fs.getFile("/data/adb/modules").listFiles() ?: return@withContext
        files.forEach { f ->
            if (!f.isDirectory) return@forEach
            if (!fs.getFile(f, "webroot").isDirectory) return@forEach
            if (fs.getFile(f, "disable").exists() && !showDisabled) return@forEach
            var name = f.name
            val id = f.name
            var author = "?"
            var version = "?"
            var desc = ""
            fs.getFile(f, "module.prop").newInputStream().bufferedReader().use {
                it.lines().forEach { line ->
                    val ls = line.split("=", limit = 2)
                    if (ls.size == 2) {
                        when (ls[0]) {
                            "name" -> name = ls[1]
                            "description" -> desc = ls[1]
                            "author" -> author = ls[1]
                            "version" -> version = ls[1]
                        }
                    }
                }
            }
            newModuleList.add(Module(name, id, desc, author, version))
        }
        moduleList.emit(newModuleList)
    }

    val packageInfoList: StateFlow<List<PackageInfo>>
        field = MutableStateFlow(listOf())

    private fun refreshPackageInfoList() {
        viewModelScope.launch {
            packageInfoListStatus.getAndUpdate { if (it is Status.Ready) Status.Updating else it }
            refreshPackageInfoList(flags = 0)
            packageInfoListStatus.emit(Status.Ready)
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private suspend fun refreshPackageInfoList(flags: Int): Unit = withContext(Dispatchers.IO) {
        val pm = App.packageManager
        val packageList = pm.getInstalledApplications(flags).map { it.packageName }
        val newPackageInfoList = packageList.sorted().mapNotNull {
            try {
                pm.getPackageInfo(it, 0)
            } catch (_: Exception) {
                null
            }
        }
        packageInfoList.emit(newPackageInfoList)
    }

    private val iconCache = LruCache<String, Bitmap>(200)

    fun loadAppIcon(
        packageManager: PackageManager,
        packageName: String,
        sizePx: Int
    ): Bitmap? {
        val cacheKey = "$packageName:$sizePx"
        val cachedIcon = iconCache[cacheKey]
        if (cachedIcon != null) return cachedIcon

        try {
            val packageInfo = packageInfoList.value.find { it.packageName == packageName }
                ?: return null
            val drawable = packageInfo.applicationInfo?.loadIcon(packageManager)
                ?: return null
            val raw = drawable.toBitmap(sizePx)
            val icon = raw.scale(sizePx, sizePx)
            iconCache.put(cacheKey, icon)
            return icon
        } catch (_: Exception) {
            return null
        }
    }

    val colorScheme: StateFlow<ColorScheme>
        field = MutableStateFlow(lightColorScheme())

    fun updateColorScheme(colorScheme: ColorScheme) {
        this.colorScheme.tryEmit(colorScheme)
        colorSchemeStatus.tryEmit(Status.Ready)
    }

    override fun onCleared() {
        connector.disconnect()
    }
}
