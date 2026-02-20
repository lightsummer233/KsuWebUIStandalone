package io.github.a13e300.ksuwebui

import android.content.Intent
import android.webkit.JsPromptResult
import android.webkit.JsResult
import androidx.compose.runtime.Immutable

@Immutable
sealed interface WebViewEvent {

    data class EvaluateJavascript(val jsCode: String) : WebViewEvent

    data class Toast(val message: String) : WebViewEvent

    data class FullScreen(val enable: Boolean) : WebViewEvent

    data class EdgeToEdge(val enable: Boolean) : WebViewEvent

    data object Exit : WebViewEvent

    data class ShowAlert(val message: String, val result: JsResult) : WebViewEvent

    data class ShowConfirm(val message: String, val result: JsResult) : WebViewEvent

    data class ShowPrompt(
        val message: String,
        val defaultValue: String,
        val result: JsPromptResult
    ) : WebViewEvent

    data class ShowFileChooser(val intent: Intent) : WebViewEvent
}
