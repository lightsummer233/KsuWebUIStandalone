package io.github.a13e300.ksuwebui

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet

class FileSystemService : RootService() {

    override fun onBind(intent: Intent): IBinder {
        return FileSystemManager.getService()
    }

    interface Listener {
        fun onServiceAvailable(fs: FileSystemManager)
        fun onLaunchFailed()
    }

    companion object {

        private sealed interface Status {
            data object Uninitialized : Status
            data object CheckRoot : Status
            data class ServiceAvailable(val fs: FileSystemManager) : Status
        }

        private var status: Status = Status.Uninitialized
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val fs = FileSystemManager.getRemote(service)
                status = Status.ServiceAvailable(fs)
                pendingListeners.forEach { l ->
                    l.onServiceAvailable(fs)
                    pendingListeners.remove(l)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                status = Status.Uninitialized
            }
        }
        private val pendingListeners = CopyOnWriteArraySet<Listener>()

        private val isRoot
            get() = Shell.Builder.create().build().use { it.isRoot }

        suspend fun start(listener: Listener) {
            if (status is Status.ServiceAvailable) {
                val fs = (status as Status.ServiceAvailable).fs
                listener.onServiceAvailable(fs)
                return
            }
            pendingListeners.add(listener)
            if (status == Status.Uninitialized) {
                checkRoot()
            }
        }

        private suspend fun checkRoot() {
            status = Status.CheckRoot
            if (isRoot) {
                withContext(Dispatchers.Main) {
                    launchService()
                }
            } else {
                status = Status.Uninitialized
                pendingListeners.forEach { l ->
                    l.onLaunchFailed()
                }
                pendingListeners.clear()
            }
        }

        private fun launchService() {
            bind(Intent(App.instance, FileSystemService::class.java), connection)
        }

        fun removeListener(listener: Listener) {
            pendingListeners.remove(listener)
        }
    }
}
