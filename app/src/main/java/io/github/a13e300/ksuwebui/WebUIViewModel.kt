package io.github.a13e300.ksuwebui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class WebUIViewModel : ViewModel() {

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
    }

    val status: StateFlow<Status>
        field = MutableStateFlow<Status>(Status.Loading)

    val event: StateFlow<List<WebViewEvent>>
        field = MutableStateFlow<List<WebViewEvent>>(emptyList())

    fun sendEvent(e: WebViewEvent) {
        event.update { events -> events + e }
    }
}
