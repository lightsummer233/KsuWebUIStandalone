package io.github.a13e300.ksuwebui

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.topjohnwu.superuser.Shell

class App : Application(), ViewModelStoreOwner {

    companion object {
        lateinit var instance: App
            private set
        val prefs: SharedPreferences by lazy {
            instance.getSharedPreferences("settings", MODE_PRIVATE)
        }
        val packageManager: PackageManager by lazy {
            instance.packageManager
        }
    }

    override fun onCreate() {
        instance = this
        super.onCreate()

        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        val viewModelProvider = ViewModelProvider(this)
        val viewModel = viewModelProvider[WebUIViewModel::class]

        viewModel.initialize()
    }

    override val viewModelStore: ViewModelStore by lazy { ViewModelStore() }
}
