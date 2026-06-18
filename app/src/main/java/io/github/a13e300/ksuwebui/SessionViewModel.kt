package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class SessionViewModel : ViewModel() {

    val webView: StateFlow<WebView?>
        field = MutableStateFlow<WebView?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(
        webView: WebView,
        moduleId: String,
        sharedViewModel: SharedViewModel
    ) {
        // Attach WebView
        this.webView.tryEmit(webView)

        // WebView Settings
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false

        // Asset Loader
        val moduleDir = "/data/adb/modules/$moduleId"
        val webRoot = File("$moduleDir/webroot")
        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain("mui.kernelsu.org")
            .addPathHandler(
                "/",
                RemoteFsPathHandler(
                    webRoot,
                    sharedViewModel.fs.get()!!,
                    { insets.value.css },
                    { enableInsets(it) },
                    { sharedViewModel.colorScheme.value.css }
                )
            )
            .build()

        // WebViewClient
        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                if (url.scheme.equals("ksu", ignoreCase = true)
                    && url.host.equals("icon", ignoreCase = true)
                ) {
                    val packageName = url.path?.substring(1)
                    if (!packageName.isNullOrEmpty()) {
                        val icon = sharedViewModel.loadAppIcon(App.packageManager, packageName, 512)
                        if (icon != null) {
                            val stream = ByteArrayOutputStream()
                            icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            return WebResourceResponse(
                                "image/png",
                                null,
                                ByteArrayInputStream(stream.toByteArray())
                            )
                        }
                    }
                }
                return webViewAssetLoader.shouldInterceptRequest(url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                updateWebCanGoBack(view?.canGoBack() ?: false)
                if (isInsetsEnabled.value) {
                    view?.evaluateJavascript(insets.value.js, null)
                }
                super.doUpdateVisitedHistory(view, url, isReload)
            }
        }

        // WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (message == null || result == null) return false
                sendWebViewEvent(WebViewEvent.ShowAlert(message, result))
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (message == null || result == null) return false
                sendWebViewEvent(WebViewEvent.ShowConfirm(message, result))
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                if (message == null || result == null || defaultValue == null) return false
                sendWebViewEvent(WebViewEvent.ShowPrompt(message, defaultValue, result))
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                updateFilePathCallback(filePathCallback)

                val intent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                sendWebViewEvent(WebViewEvent.ShowFileChooser(intent))
                return true
            }
        }

        // JS Interface
        val webviewInterface = WebViewInterfaceImpl(
            sharedViewModel = sharedViewModel,
            sessionViewModel = this
        )
        webView.addJavascriptInterface(webviewInterface, "ksu")

        val homePage = "https://mui.kernelsu.org/index.html"
        webView.loadUrl(homePage)
    }

    fun detachWebView() {
        enableInsets(false)
        updateFilePathCallback(null)
        updateWebCanGoBack(false)
        webView.tryEmit(null)
    }

    suspend fun postToWebView(block: WebView.() -> Unit) {
        val webView = webView.value ?: webView.filterNotNull().first()
        webView.post { webView.block() }
    }

    val insets: StateFlow<Insets>
        field = MutableStateFlow<Insets>(EmptyInsets())

    fun updateInsets(insets: Insets) {
        this.insets.tryEmit(insets)
    }

    private val webViewEventChannel = Channel<WebViewEvent>(capacity = 64)

    val webViewEvents: Flow<WebViewEvent> = webViewEventChannel.receiveAsFlow()

    fun sendWebViewEvent(event: WebViewEvent) {
        viewModelScope.launch {
            webViewEventChannel.send(event)
        }
    }

    val isInsetsEnabled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun enableInsets(enabled: Boolean) {
        isInsetsEnabled.tryEmit(enabled)
    }

    val filePathCallback: StateFlow<ValueCallback<Array<Uri>>?>
        field = MutableStateFlow<ValueCallback<Array<Uri>>?>(null)

    fun updateFilePathCallback(callback: ValueCallback<Array<Uri>>?) {
        filePathCallback.value?.onReceiveValue(null)
        filePathCallback.tryEmit(callback)
    }

    fun completeFilePathCallback(uris: Array<Uri>?) {
        filePathCallback.value?.onReceiveValue(uris)
        filePathCallback.tryEmit(null)
    }

    val webCanGoBack: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun updateWebCanGoBack(canGoBack: Boolean) {
        webCanGoBack.tryEmit(canGoBack)
    }

    sealed interface JsDialog {
        val handled: CompletableDeferred<Unit>

        data class Alert(
            val event: WebViewEvent.ShowAlert,
            override val handled: CompletableDeferred<Unit>
        ) : JsDialog

        data class Confirm(
            val event: WebViewEvent.ShowConfirm,
            override val handled: CompletableDeferred<Unit>
        ) : JsDialog

        data class Prompt(
            val event: WebViewEvent.ShowPrompt,
            override val handled: CompletableDeferred<Unit>
        ) : JsDialog
    }

    override fun onCleared(): Unit = detachWebView()
}
