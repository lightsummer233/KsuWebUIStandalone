package io.github.a13e300.ksuwebui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel(), FileSystemService.Listener {

    @Immutable
    sealed interface Status {
        data object Loading : Status
        data object NoRoot : Status
        data object NoModule : Status
        data object Ready : Status
    }

    val status: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

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

    fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            FileSystemService.start(this@MainViewModel)
        }
    }

    private suspend fun refreshModuleList(fs: FileSystemManager) = withContext(Dispatchers.IO) {
        val newModuleList = mutableListOf<Module>()
        val showDisabled = App.prefs.getBoolean("show_disabled", false)
        fs.getFile("/data/adb/modules").listFiles()!!.forEach { f ->
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
        modules = newModuleList
        if (newModuleList.isEmpty()) {
            status.emit(Status.NoModule)
        } else {
            status.emit(Status.Ready)
        }
    }

    override fun onServiceAvailable(fs: FileSystemManager) {
        viewModelScope.launch {
            refreshModuleList(fs)
        }
    }

    override fun onLaunchFailed() {
        viewModelScope.launch {
            status.emit(Status.NoRoot)
        }
    }

    override fun onCleared() {
        FileSystemService.removeListener(this)
        super.onCleared()
    }

    companion object {
        var modules: List<Module> = listOf()
            private set
    }
}
