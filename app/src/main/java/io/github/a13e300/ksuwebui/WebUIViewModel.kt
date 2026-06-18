package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import androidx.collection.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.WebViewAssetLoader
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.ref.WeakReference

class WebUIViewModel : ViewModel(), FileSystemService.Listener {

    sealed interface Status {
        data object Loading : Status
        data object Updating : Status
        data object Unavailable : Status
        data object Ready : Status
    }

    val moduleListStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val packageInfoListStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val fsStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val colorSchemeStatus: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val status: StateFlow<Status> = combine(
        moduleListStatus,
        packageInfoListStatus,
        fsStatus,
        colorSchemeStatus
    ) { moduleStatus, packageStatus, fsStatus, colorSchemeStatus ->
        if (fsStatus == Status.Unavailable || moduleStatus == Status.Unavailable || packageStatus == Status.Unavailable) {
            Status.Unavailable
        } else if (moduleStatus == Status.Loading || packageStatus == Status.Loading || fsStatus == Status.Loading || colorSchemeStatus == Status.Loading) {
            Status.Loading
        } else {
            Status.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Status.Loading)

    fun initialize() {
        viewModelScope.launch {
            FileSystemService.start(this@WebUIViewModel)
            refreshPackageInfoList()
        }
    }

    @Immutable
    data class Module(
        val name: String,
        val id: String,
        val desc: String,
        val author: String,
        val version: String
    )

    val moduleList: StateFlow<List<Module>>
        field = MutableStateFlow(listOf())

    fun refreshModuleListWithStatus() {
        viewModelScope.launch {
            moduleListStatus.getAndUpdate { if (it is Status.Ready) Status.Updating else it }

            val fs = fs.get()
            if (fs != null) {
                refreshModuleListWithStatus(fs)
            } else {
                FileSystemService.start(this@WebUIViewModel)
            }
        }
    }

    private fun refreshModuleListWithStatus(
        fs: FileSystemManager
    ) {
        viewModelScope.launch {
            moduleListStatus.getAndUpdate { if (it is Status.Ready) Status.Updating else it }
            refreshModuleList(fs)
            moduleListStatus.emit(Status.Ready)
        }
    }

    private suspend fun refreshModuleList(fs: FileSystemManager) = withContext(Dispatchers.IO) {
        val newModuleList = mutableListOf<Module>()
        val showDisabled = App.prefs.getBoolean("show_disabled", false)
        fs.getFile("/data/adb/modules").listFiles()!!.forEach { f ->
            if (!f.isDirectory) return@forEach
            if (!fs.getFile(f, "webroot").isDirectory) return@forEach
            if (fs.getFile(f, "disable").exists() && !showDisabled) return@forEach
            var name = f.name
            val id = f.name
            var author = "?"
            var version = "?"
            var desc = ""
            fs.getFile(f, "module.prop").newInputStream().bufferedReader().use {
                it.lines().forEach { line ->
                    val ls = line.split("=", limit = 2)
                    if (ls.size == 2) {
                        when (ls[0]) {
                            "name" -> name = ls[1]
                            "description" -> desc = ls[1]
                            "author" -> author = ls[1]
                            "version" -> version = ls[1]
                        }
                    }
                }
            }
            newModuleList.add(Module(name, id, desc, author, version))
        }
        moduleList.emit(newModuleList)
    }

    val packageInfoList: StateFlow<List<PackageInfo>>
        field = MutableStateFlow(listOf())

    private fun refreshPackageInfoList() {
        viewModelScope.launch {
            packageInfoListStatus.getAndUpdate { if (it is Status.Ready) Status.Updating else it }
            refreshPackageInfoList(flags = 0)
            packageInfoListStatus.emit(Status.Ready)
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
    }

    private val iconCache = LruCache<String, Bitmap>(200)

    private fun loadAppIcon(
        packageManager: PackageManager,
        packageName: String,
        sizePx: Int
    ): Bitmap? {
        val cachedIcon = iconCache[packageName]
        if (cachedIcon != null) return cachedIcon

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

    var fs: WeakReference<FileSystemManager> = WeakReference(null)

    override fun onServiceAvailable(fs: FileSystemManager) {
        this.fs = WeakReference(fs)
        viewModelScope.launch {
            fsStatus.emit(Status.Ready)
            refreshModuleListWithStatus(fs)
        }
    }

    override fun onLaunchFailed() {
        viewModelScope.launch {
            fsStatus.emit(Status.Unavailable)
            moduleListStatus.emit(Status.Unavailable)
        }
    }

    override fun onCleared() {
        FileSystemService.removeListener(this)
    }

    val colorScheme: StateFlow<ColorScheme>
        field = MutableStateFlow(lightColorScheme())

    fun updateColorScheme(colorScheme: ColorScheme) {
        this.colorScheme.tryEmit(colorScheme)
        colorSchemeStatus.tryEmit(Status.Ready)
    }

    val insets: StateFlow<Insets>
        field = MutableStateFlow(EmptyInsets())

    fun updateInsets(insets: Insets) {
        this.insets.tryEmit(insets)
    }

    val event: SharedFlow<WebViewEvent>
        field = MutableSharedFlow<WebViewEvent>(extraBufferCapacity = 64)

    fun sendEvent(e: WebViewEvent) {
        viewModelScope.launch {
            event.emit(e)
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

    val webCanGoBack: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun updateWebCanGoBack(canGoBack: Boolean) {
        webCanGoBack.tryEmit(canGoBack)
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
                    { insets.value.css },
                    { enableInsets(it) },
                    { colorScheme.value.css }
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
        webView.loadUrl(homePage)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun releaseWebViewState() {
        event.resetReplayCache()
        enableInsets(false)
        updateFilePathCallback(null)
        updateWebCanGoBack(false)
    }
}
