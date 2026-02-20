package io.github.a13e300.ksuwebui

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme

class WebUIActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeProperly()

        val moduleId: String = intent.getStringExtra("id") ?: run {
            finish()
            return
        }
        val moduleName: String = intent.getStringExtra("name") ?: moduleId
        if (moduleName.isNotEmpty()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                setTaskDescription(ActivityManager.TaskDescription(moduleName))
            } else {
                val taskDescription = ActivityManager.TaskDescription.Builder()
                    .setLabel(moduleName)
                    .build()
                setTaskDescription(taskDescription)
            }
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        WebView.setWebContentsDebuggingEnabled(
            prefs.getBoolean(
                "enable_web_debugging",
                BuildConfig.DEBUG
            )
        )

        setContent {
            KsuWebUITheme {
                WebUIScreen(moduleId)
            }
        }
    }

    companion object {
        var insets: Insets = Insets(0, 0, 0, 0)
        var colorScheme: ColorScheme = lightColorScheme()
    }
}
