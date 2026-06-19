package io.github.a13e300.ksuwebui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class FileSystemConnector(private val context: Context) {

    sealed interface State {
        data object Connecting : State
        data object Unavailable : State
        data class Available(val fs: FileSystemManager) : State
    }

    val state: StateFlow<State>
        field = MutableStateFlow<State>(State.Connecting)

    private val connectMutex = Mutex()
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            state.tryEmit(State.Available(FileSystemManager.getRemote(service)))
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            state.tryEmit(State.Unavailable)
        }
    }

    suspend fun connect() {
        if (state.value is State.Available) return
        if (!connectMutex.tryLock()) return
        try {
            state.tryEmit(State.Connecting)
            val hasRoot = withContext(Dispatchers.IO) {
                Shell.Builder.create().build().use { it.isRoot }
            }
            if (hasRoot) {
                withContext(Dispatchers.Main) {
                    RootService.bind(
                        Intent(context, FileSystemService::class.java),
                        connection
                    )
                    isBound = true
                }
            } else {
                state.tryEmit(State.Unavailable)
            }
        } finally {
            connectMutex.unlock()
        }
    }

    fun disconnect() {
        if (isBound) {
            @Suppress("SwallowedException")
            try {
                RootService.unbind(connection)
            } catch (_: Exception) {
            }
            isBound = false
        }
        state.tryEmit(State.Unavailable)
    }
}
