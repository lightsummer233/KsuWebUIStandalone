package io.github.a13e300.ksuwebui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeProperly()

        setContent {
            KsuWebUITheme {
                MainScreen()
            }
        }
    }
}
