package hu.lextronic.radio.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB300)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1300),
    primaryContainer = Color(0xFF3A2C00),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD8C4A0),
    secondaryContainer = Color(0xFF2E2A22),
    onSecondaryContainer = Color(0xFFF2E2C2),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF1B1C1F),
    surfaceContainerHigh = Color(0xFF232428),
    surfaceVariant = Color(0xFF2A2B2F),
    onSurfaceVariant = Color(0xFFB9B9C0),
)

@Composable
fun LextronicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
