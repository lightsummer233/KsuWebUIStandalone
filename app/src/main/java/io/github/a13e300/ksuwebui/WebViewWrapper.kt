package io.github.a13e300.ksuwebui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
fun WebViewWrapper(

) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val windowInsets = WindowInsets.safeDrawing

}
