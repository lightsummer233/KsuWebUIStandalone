package io.github.a13e300.ksuwebui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun WebUIScreen(
    moduleId: String
) {
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = App.instance)
    val sessionViewModel: SessionViewModel = viewModel()

    val status by sharedViewModel.status.collectAsState()

    val context = LocalContext.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val scope = rememberCoroutineScope()

    val webViewReference by sessionViewModel.webViewReference.collectAsState()

    // Handle insets

    val isInsetsEnabled by sessionViewModel.isInsetsEnabled.collectAsState()
    val safeDrawingInsets = WindowInsets.safeDrawing

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
            sessionViewModel.updateInsets(newInsets)
            sessionViewModel.postToWebView {
                evaluateJavascript(newInsets.js, null)
            }
        }
    }

    // Handle webview events

    LaunchedEffect(Unit) {
        sessionViewModel.webViewEvents.collect { event ->
            when (event) {
                is WebViewEvent.EvaluateJavascript -> {
                    sessionViewModel.postToWebView {
                        evaluateJavascript(event.jsCode, null)
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
                    sessionViewModel.enableInsets(event.enable)
                }

                is WebViewEvent.Exit -> {
                    activity?.finish()
                }

                else -> {}
            }
        }
    }

    // Handle back press

    val webCanGoBack by sessionViewModel.webCanGoBack.collectAsState()

    BackHandler(enabled = webCanGoBack) {
        scope.launch(Dispatchers.IO) {
            sessionViewModel.postToWebView {
                goBack()
            }
        }
    }

    // Handle lifecycle events

    webViewReference?.get()?.let { currentWebView ->
        LifecycleResumeEffect(currentWebView) {
            currentWebView.post { currentWebView.onResume() }

            onPauseOrDispose {
                currentWebView.post { currentWebView.onPause() }
            }
        }
    }

    Surface {
        Crossfade(
            targetState = status
        ) { status ->
            when (status) {
                SharedViewModel.Status.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(safeDrawingInsets)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                SharedViewModel.Status.Unavailable -> {
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

                SharedViewModel.Status.Ready, SharedViewModel.Status.Updating -> {
                    WebViewWrapper(
                        factory = {
                            sessionViewModel.attachWebView(
                                webView = this,
                                moduleId = moduleId,
                                sharedViewModel = sharedViewModel
                            )
                        },
                        onRelease = {
                            sessionViewModel.detachWebView()
                        }
                    )
                }
            }
        }
    }
}
