package com.trio.today.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Paper,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    secondary = AccentBright,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkMuted,
    outline = Edge,
    outlineVariant = Edge,
    // Nothing in the task UI uses the error role; it is set only so Material
    // components that require it do not fall back to something jarring.
    error = MutedError,
    onError = Paper,
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = PaperDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = InkDark,
    secondary = AccentDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = PaperRaisedDark,
    onSurfaceVariant = InkMutedDark,
    outline = EdgeDark,
    outlineVariant = EdgeDark,
    error = MutedError,
    onError = PaperDark,
)

/**
 * Larger and heavier than Material's defaults across the board.
 *
 * Reading a task list should never require effort, and generous type is one of
 * the cheapest accessibility wins available. Sizes stay in sp so the system
 * font-scale setting is honoured.
 */
private val TrioTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun TrioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Material You is off by default. The palette here is tuned to stay calm;
     * a user's wallpaper-derived scheme could easily produce the loud reds the
     * design specifically avoids.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrioTypography,
        content = content,
    )
}
