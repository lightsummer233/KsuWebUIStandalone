package io.github.a13e300.ksuwebui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun KsuWebUITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable (() -> Unit)
) {
    val viewModel: SharedViewModel = viewModel(viewModelStoreOwner = App.instance)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    // Update color scheme for WebUI
    LaunchedEffect(colorScheme) {
        viewModel.updateColorScheme(colorScheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
