package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.collection.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewAssetLoader
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference

class WebUIViewModel : ViewModel(), FileSystemService.Listener {

    sealed interface Status {
        data object Loading : Status
        data object NoRoot : Status
        data object Ready : Status
    }

    val status: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            FileSystemService.start(this@WebUIViewModel)
        }
    }

    val packageInfoList: StateFlow<List<PackageInfo>>
        field = MutableStateFlow(listOf())

    val packageInfoListLoaded: StateFlow<Boolean>
        field = MutableStateFlow(false)

    suspend fun refreshPackageInfoList() {
        if (packageInfoListLoaded.value) {
            viewModelScope.launch(Dispatchers.IO) {
                refreshPackageInfoList(flags = 0)
            }
        } else {
            refreshPackageInfoList(flags = 0)
            packageInfoListLoaded.emit(true)
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private suspend fun refreshPackageInfoList(flags: Int): Unit = withContext(Dispatchers.IO) {
        val pm = App.packageManager
        val packageList = pm.getInstalledApplications(flags).map { it.packageName }
        val newPackageInfoList = packageList.sorted().mapNotNull {
            try {
                pm.getPackageInfo(it, 0)
            } catch (_: Exception) {
                null
            }
        }
        packageInfoList.emit(newPackageInfoList)
        packageInfos = newPackageInfoList
    }

    private val iconCache = LruCache<String, Bitmap>(200)

    fun loadAppIcon(packageManager: PackageManager, packageName: String, sizePx: Int): Bitmap? {
        val cached = iconCache[packageName]
        if (cached != null) return cached

        try {
            val packageInfo = packageInfoList.value.find { it.packageName == packageName }
                ?: return null
            val drawable = packageInfo.applicationInfo?.loadIcon(packageManager)
                ?: return null
            val raw = drawable.toBitmap(sizePx)
            val icon = raw.scale(sizePx, sizePx)
            iconCache.put(packageName, icon)
            return icon
        } catch (_: Exception) {
            return null
        }
    }

    val isInsetsEnabled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun enableInsets(enabled: Boolean) {
        isInsetsEnabled.tryEmit(enabled)
    }

    val event: SharedFlow<WebViewEvent>
        field = MutableSharedFlow<WebViewEvent>(extraBufferCapacity = 64)

    fun sendEvent(e: WebViewEvent) {
        viewModelScope.launch {
            event.emit(e)
        }
    }

    val filePathCallback: StateFlow<ValueCallback<Array<Uri>>?>
        field = MutableStateFlow<ValueCallback<Array<Uri>>?>(null)

    fun updateFilePathCallback(callback: ValueCallback<Array<Uri>>?) {
        filePathCallback.value?.onReceiveValue(null)
        filePathCallback.tryEmit(callback)
    }

    val canGoBack: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun updateCanGoBack(canGoBack: Boolean) {
        this.canGoBack.tryEmit(canGoBack)
    }

    var fs: WeakReference<FileSystemManager> = WeakReference(null)

    override fun onServiceAvailable(fs: FileSystemManager) {
        this.fs = WeakReference(fs)
        viewModelScope.launch(Dispatchers.IO) {
            refreshPackageInfoList()
            status.emit(Status.Ready)
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

    @SuppressLint("SetJavaScriptEnabled")
    fun initializeWebView(webView: WebView, moduleId: String) {

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
                    webRoot, fs.get()!!,
                    { insets.css },
                    { enableInsets(it) },
                    { colorScheme.css }
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
                        val icon = loadAppIcon(App.packageManager, packageName, 512)
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
                updateCanGoBack(view?.canGoBack() ?: false)
                if (isInsetsEnabled.value) {
                    view?.evaluateJavascript(insets.js, null)
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
                sendEvent(WebViewEvent.ShowAlert(message, result))
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (message == null || result == null) return false
                sendEvent(WebViewEvent.ShowConfirm(message, result))
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
                sendEvent(WebViewEvent.ShowPrompt(message, defaultValue, result))
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
                sendEvent(WebViewEvent.ShowFileChooser(intent))
                return true
            }
        }

        // JS Interface
        val webviewInterface = WebViewInterfaceImpl(this)
        webView.addJavascriptInterface(webviewInterface, "ksu")

        val homePage = "https://mui.kernelsu.org/index.html"
        if (webView.width > 0 && webView.height > 0) {
            webView.loadUrl(homePage)
        } else {
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    if (v.width > 0 && v.height > 0) {
                        (v as WebView).loadUrl(homePage)
                        v.removeOnLayoutChangeListener(this)
                    }
                }
            }
            webView.addOnLayoutChangeListener(listener)
        }
    }

    companion object {
        var insets: Insets = Insets(0, 0, 0, 0)
        var colorScheme: ColorScheme = lightColorScheme()

        var packageInfos: List<PackageInfo> = listOf()
            private set
    }
}
