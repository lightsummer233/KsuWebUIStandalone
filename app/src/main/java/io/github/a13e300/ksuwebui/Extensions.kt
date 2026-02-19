package io.github.a13e300.ksuwebui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color as AndroidColor

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

fun Drawable.toBitmap(size: Int): Bitmap {
    if (this is BitmapDrawable) return this.bitmap

    val width = intrinsicWidth.takeIf { it > 0 } ?: size
    val height = intrinsicHeight.takeIf { it > 0 } ?: size

    return createBitmap(width, height).apply {
        val canvas = Canvas(this)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
    }
}

fun Window.hideSystemUI() {
    WindowInsetsControllerCompat(this, decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun Window.showSystemUI() {
    WindowInsetsControllerCompat(this, decorView).show(WindowInsetsCompat.Type.systemBars())
}
