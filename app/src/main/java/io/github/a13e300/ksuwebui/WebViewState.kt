package io.github.a13e300.ksuwebui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalLayoutApi::class)
@Stable
data class WebViewState(
    val moduleId : String
) {
    var fullScreenEnabled: Boolean by mutableStateOf(false)

    var isInsetsEnabled: Boolean by mutableStateOf(false)
    var currentInsets: MutableWindowInsets = MutableWindowInsets()
}
