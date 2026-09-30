package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = SparkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16264C),
    onPrimaryContainer = SparkBlueBright,
    inversePrimary = SparkBlueDeep,
    secondary = CyberCyan,
    onSecondary = Color(0xFF00293A),
    secondaryContainer = Color(0xFF0E3A4F),
    onSecondaryContainer = Color(0xFFB8EAFE),
    tertiary = TerminalGreen,
    onTertiary = Color(0xFF00301F),
    tertiaryContainer = Color(0xFF0D3A29),
    onTertiaryContainer = Color(0xFF9BF2CE),
    error = TerminalRed,
    onError = Color(0xFF42090F),
    errorContainer = Color(0xFF4A1520),
    onErrorContainer = Color(0xFFFFB3BC),
    background = DarkCanvas,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceHigh,
    surfaceContainerLow = Color(0xFF0A0D15),
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    scrim = Color(0xFF000000),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SparkBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A1F4D),
    secondary = CyberCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEEFFD),
    onSecondaryContainer = Color(0xFF00344A),
    tertiary = Color(0xFF0F9B6C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDF5E3),
    onTertiaryContainer = Color(0xFF00301F),
    background = LightCanvas,
    onBackground = Color(0xFF0C1120),
    surface = LightSurface,
    onSurface = Color(0xFF0C1120),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5468),
    outline = LightBorder,
    outlineVariant = Color(0xFFEDF0F6),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Spark ships dark-first
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
      }
      window.statusBarColor = Color.Transparent.toArgb()
      window.navigationBarColor = Color.Transparent.toArgb()
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = SparkShapes,
    content = content,
  )
}
