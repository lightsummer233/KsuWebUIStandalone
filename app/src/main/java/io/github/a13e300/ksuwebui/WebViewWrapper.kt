package io.github.a13e300.ksuwebui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.NoOpUpdate

@Composable
fun WebViewWrapper(
    key: String,
    windowInsets: WindowInsets,
    factory: WebView.() -> Unit,
    update: WebView.() -> Unit = NoOpUpdate
) {
    Surface(
        modifier = Modifier.windowInsetsPadding(windowInsets)
    ) {
        key(key) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        factory()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = update
            )
        }
    }
}
