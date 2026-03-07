package io.github.a13e300.ksuwebui

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

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
            setTaskDescription(moduleName)
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
}
