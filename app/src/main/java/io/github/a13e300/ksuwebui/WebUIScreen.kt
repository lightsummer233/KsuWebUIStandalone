package io.github.a13e300.ksuwebui

import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun WebUIScreen(
    moduleId: String
) {
    val mainViewModel: MainViewModel = viewModel()
    val webUIViewModel: WebUIViewModel = viewModel()

    val status by webUIViewModel.status.collectAsState()

    LaunchedEffect(Unit) {
        mainViewModel.initialize()
        webUIViewModel.initialize()
    }

    val context = LocalContext.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val scope = rememberCoroutineScope()

    val webView: CompletableDeferred<WeakReference<WebView>> = remember {
        CompletableDeferred()
    }

    val safeDrawingInsets = WindowInsets.safeDrawing

    val isInsetsEnabled by webUIViewModel.isInsetsEnabled.collectAsState()

    LaunchedEffect(density, layoutDirection, safeDrawingInsets, isInsetsEnabled) {
        if (!isInsetsEnabled) return@LaunchedEffect
        snapshotFlow {
            val top = (safeDrawingInsets.getTop(density) / density.density).toInt()
            val bottom = (safeDrawingInsets.getBottom(density) / density.density).toInt()
            val left =
                (safeDrawingInsets.getLeft(density, layoutDirection) / density.density).toInt()
            val right =
                (safeDrawingInsets.getRight(density, layoutDirection) / density.density).toInt()
            Insets(top, bottom, left, right)
        }.collect { newInsets ->
            if (WebUIViewModel.insets != newInsets) {
                WebUIViewModel.insets = newInsets
                webView.await().get()?.let {
                    it.post { it.evaluateJavascript(newInsets.js, null) }
                }
            }
        }
    }

    val windowInsets by remember {
        derivedStateOf { if (isInsetsEnabled) WindowInsets() else safeDrawingInsets }
    }

    LaunchedEffect(Unit) {
        webUIViewModel.event.collectLatest { event ->
            when (event) {
                is WebViewEvent.LoadUrl -> {
                    webView.await().get()?.let {
                        it.post { it.evaluateJavascript(event.jsCode, null) }
                    }
                }

                is WebViewEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is WebViewEvent.FullScreen -> {
                    if (event.enable) {
                        activity?.window?.hideSystemUI()
                    } else {
                        activity?.window?.showSystemUI()
                    }
                }

                is WebViewEvent.EdgeToEdge -> {
                    webUIViewModel.enableInsets(event.enable)
                }

                is WebViewEvent.Exit -> {
                    activity?.finish()
                }

                else -> {}
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(colorScheme) {
        WebUIViewModel.colorScheme = colorScheme
    }

    val webCanGoBack by webUIViewModel.canGoBack.collectAsState()

    BackHandler(enabled = webCanGoBack) {
        scope.launch(Dispatchers.IO) {
            webView.await().get()?.let {
                it.post { it.goBack() }
            }
        }
    }

    Crossfade(
        targetState = status
    ) { status ->
        when (status) {
            WebUIViewModel.Status.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(safeDrawingInsets)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            WebUIViewModel.Status.NoRoot -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(safeDrawingInsets)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.please_grant_root),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                }
            }

            is WebUIViewModel.Status.Ready -> {
                WebViewWrapper(
                    key = moduleId,
                    windowInsets = windowInsets,
                    factory = {
                        webUIViewModel.initializeWebView(this, moduleId)
                        webView.complete(WeakReference(this))
                    }
                )
            }
        }
    }
}
