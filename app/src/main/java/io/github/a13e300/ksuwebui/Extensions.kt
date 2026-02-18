package io.github.a13e300.ksuwebui

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

// enableEdgeToEdge() without enforcing contrast, magic based on androidx EdgeToEdge.kt
fun ComponentActivity.enableEdgeToEdgeProperly() {
    if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    ) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT))
    } else {
        val darkScrim = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                darkScrim
            )
        )
    }
}

val WindowInsets.css: String
    @Composable get() {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current

        val top = this.getTop(density)
        val right = this.getRight(density, layoutDirection)
        val bottom = this.getBottom(density)
        val left = this.getLeft(density, layoutDirection)

        return buildString {
            appendLine(":root {")
            appendLine("\t--safe-area-inset-top: ${top}px;")
            appendLine("\t--safe-area-inset-right: ${right}px;")
            appendLine("\t--safe-area-inset-bottom: ${bottom}px;")
            appendLine("\t--safe-area-inset-left: ${left}px;")
            appendLine("\t--window-inset-top: var(--safe-area-inset-top, 0px);")
            appendLine("\t--window-inset-bottom: var(--safe-area-inset-bottom, 0px);")
            appendLine("\t--window-inset-left: var(--safe-area-inset-left, 0px);")
            appendLine("\t--window-inset-right: var(--safe-area-inset-right, 0px);")
            appendLine("\t--f7-safe-area-top: var(--window-inset-top, 0px) !important;")
            appendLine("\t--f7-safe-area-bottom: var(--window-inset-bottom, 0px) !important;")
            appendLine("\t--f7-safe-area-left: var(--window-inset-left, 0px) !important;")
            appendLine("\t--f7-safe-area-right: var(--window-inset-right, 0px) !important;")
            append("}")
        }
    }

val WindowInsets.js: String
    @Composable get() {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current

        val top = this.getTop(density)
        val right = this.getRight(density, layoutDirection)
        val bottom = this.getBottom(density)
        val left = this.getLeft(density, layoutDirection)

        return buildString {
            append("(function() {")
            append(" var s = document.documentElement.style;")
            append(" s.setProperty('--safe-area-inset-top', '${top}px');")
            append(" s.setProperty('--safe-area-inset-right', '${right}px');")
            append(" s.setProperty('--safe-area-inset-bottom', '${bottom}px');")
            append(" s.setProperty('--safe-area-inset-left', '${left}px');")
            append("})();")
        }
    }

private fun Float.toHex(): String {
    return (this * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
}

private fun Color.toCssValue(): String {
    return if (alpha == 1f) {
        "#${red.toHex()}${green.toHex()}${blue.toHex()}"
    } else {
        "#${red.toHex()}${green.toHex()}${blue.toHex()}${alpha.toHex()}"
    }
}

val ColorScheme.css: String
    get() {
        return buildString {
            appendLine(":root {")
            appendLine("  --primary: ${primary.toCssValue()};")
            appendLine("  --onPrimary: ${onPrimary.toCssValue()};")
            appendLine("  --primaryContainer: ${primaryContainer.toCssValue()};")
            appendLine("  --inversePrimary: ${inversePrimary.toCssValue()};")
            appendLine("  --secondary: ${secondary.toCssValue()};")
            appendLine("  --onSecondary: ${onSecondary.toCssValue()};")
            appendLine("  --secondaryContainer: ${secondaryContainer.toCssValue()};")
            appendLine("  --onSecondaryContainer: ${onSecondaryContainer.toCssValue()};")
            appendLine("  --tertiary: ${tertiary.toCssValue()};")
            appendLine("  --onTertiary: ${onTertiary.toCssValue()};")
            appendLine("  --tertiaryContainer: ${tertiaryContainer.toCssValue()};")
            appendLine("  --onTertiaryContainer: ${onTertiaryContainer.toCssValue()};")
            appendLine("  --background: ${background.toCssValue()};")
            appendLine("  --onBackground: ${onBackground.toCssValue()};")
            appendLine("  --surface: ${surface.toCssValue()};")
            appendLine("  --tonalSurface: ${surfaceContainer.toCssValue()};")
            appendLine("  --onSurface: ${onSurface.toCssValue()};")
            appendLine("  --surfaceVariant: ${surfaceVariant.toCssValue()};")
            appendLine("  --onSurfaceVariant: ${onSurfaceVariant.toCssValue()};")
            appendLine("  --surfaceTint: ${surfaceTint.toCssValue()};")
            appendLine("  --inverseSurface: ${inverseSurface.toCssValue()};")
            appendLine("  --inverseOnSurface: ${inverseOnSurface.toCssValue()};")
            appendLine("  --error: ${error.toCssValue()};")
            appendLine("  --onError: ${onError.toCssValue()};")
            appendLine("  --errorContainer: ${errorContainer.toCssValue()};")
            appendLine("  --onErrorContainer: ${onErrorContainer.toCssValue()};")
            appendLine("  --outline: ${outline.toCssValue()};")
            appendLine("  --outlineVariant: ${outlineVariant.toCssValue()};")
            appendLine("  --scrim: ${scrim.toCssValue()};")
            appendLine("  --surfaceBright: ${surfaceBright.toCssValue()};")
            appendLine("  --surfaceDim: ${surfaceDim.toCssValue()};")
            appendLine("  --surfaceContainer: ${surfaceContainer.toCssValue()};")
            appendLine("  --surfaceContainerHigh: ${surfaceContainerHigh.toCssValue()};")
            appendLine("  --surfaceContainerHighest: ${surfaceContainerHighest.toCssValue()};")
            appendLine("  --surfaceContainerLow: ${surfaceContainerLow.toCssValue()};")
            appendLine("  --surfaceContainerLowest: ${surfaceContainerLowest.toCssValue()};")
            appendLine("  --filledTonalButtonContentColor: ${onPrimaryContainer.toCssValue()};")
            appendLine("  --filledTonalButtonContainerColor: ${secondaryContainer.toCssValue()};")
            appendLine("  --filledTonalButtonDisabledContentColor: ${onSurfaceVariant.toCssValue()};")
            appendLine("  --filledTonalButtonDisabledContainerColor: ${surfaceVariant.toCssValue()};")
            appendLine("  --filledCardContentColor: ${onPrimaryContainer.toCssValue()};")
            appendLine("  --filledCardContainerColor: ${primaryContainer.toCssValue()};")
            appendLine("  --filledCardDisabledContentColor: ${onSurfaceVariant.toCssValue()};")
            appendLine("  --filledCardDisabledContainerColor: ${surfaceVariant.toCssValue()};")
            appendLine("}")
        }
    }
