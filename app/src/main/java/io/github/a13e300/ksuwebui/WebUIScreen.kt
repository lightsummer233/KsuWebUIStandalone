package io.github.a13e300.ksuwebui

import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.CompletableDeferred
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

    val webView by sessionViewModel.webView.collectAsState()

    webView?.let { currentWebView ->
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

    // Handle JavaScript dialogs

    var jsDialog: SessionViewModel.JsDialog? by remember { mutableStateOf(null) }

    jsDialog?.let { dialog ->
        when (dialog) {
            is SessionViewModel.JsDialog.Alert -> {
                AlertDialog(
                    onDismissRequest = {
                        dialog.event.result.cancel()
                        jsDialog = null
                        dialog.handled.complete(Unit)
                    },
                    text = {
                        Text(dialog.event.message)
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                dialog.event.result.confirm()
                                jsDialog = null
                                dialog.handled.complete(Unit)
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }

            is SessionViewModel.JsDialog.Confirm -> {
                AlertDialog(
                    onDismissRequest = {
                        dialog.event.result.cancel()
                        jsDialog = null
                        dialog.handled.complete(Unit)
                    },
                    text = {
                        Text(dialog.event.message)
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                dialog.event.result.confirm()
                                jsDialog = null
                                dialog.handled.complete(Unit)
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                dialog.event.result.cancel()
                                jsDialog = null
                                dialog.handled.complete(Unit)
                            }
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            is SessionViewModel.JsDialog.Prompt -> {
                var value by remember(dialog) { mutableStateOf(dialog.event.defaultValue) }

                AlertDialog(
                    onDismissRequest = {
                        dialog.event.result.cancel()
                        jsDialog = null
                        dialog.handled.complete(Unit)
                    },
                    text = {
                        Column {
                            Text(dialog.event.message)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                dialog.event.result.confirm(value)
                                jsDialog = null
                                dialog.handled.complete(Unit)
                            }
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                dialog.event.result.cancel()
                                jsDialog = null
                                dialog.handled.complete(Unit)
                            }
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }
        }
    }

    // Handle webview events

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        sessionViewModel.completeFilePathCallback(uris)
    }

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

                is WebViewEvent.ShowAlert -> {
                    val handled = CompletableDeferred<Unit>()
                    jsDialog = SessionViewModel.JsDialog.Alert(event, handled)
                    handled.await()
                }

                is WebViewEvent.ShowConfirm -> {
                    val handled = CompletableDeferred<Unit>()
                    jsDialog = SessionViewModel.JsDialog.Confirm(event, handled)
                    handled.await()
                }

                is WebViewEvent.ShowPrompt -> {
                    val handled = CompletableDeferred<Unit>()
                    jsDialog = SessionViewModel.JsDialog.Prompt(event, handled)
                    handled.await()
                }

                is WebViewEvent.ShowFileChooser -> {
                    runCatching {
                        fileChooserLauncher.launch(event.intent)
                    }.onFailure {
                        sessionViewModel.completeFilePathCallback(null)
                    }
                }
            }
        }
    }
}
