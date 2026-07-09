package com.linuxhost

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LinuxGreen = Color(0xFF3FB950)
val LinuxGreenDark = Color(0xFF238636)
val LinuxBg = Color(0xFF0D1117)
val LinuxSurface = Color(0xFF161B22)
val LinuxBorder = Color(0xFF30363D)
val LinuxTextPrimary = Color(0xFFE6EDF3)
val LinuxTextSecondary = Color(0xFF8B949E)
val LinuxCardBg = Color(0xFF161B22)
val LinuxDanger = Color(0xFFF85149)
val LinuxWarning = Color(0xFFD29922)
val LinuxInfo = Color(0xFF1F6FEB)
val LinuxPurple = Color(0xFFA371F7)
val LinuxOrange = Color(0xFFF0883E)

private val DarkColorScheme = darkColorScheme(
    primary = LinuxGreen,
    onPrimary = Color.Black,
    primaryContainer = LinuxGreenDark,
    secondary = Color(0xFF58A6FF),
    onSecondary = Color.Black,
    background = LinuxBg,
    onBackground = LinuxTextPrimary,
    surface = LinuxSurface,
    onSurface = LinuxTextPrimary,
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = LinuxTextSecondary,
    outline = LinuxBorder,
    error = LinuxDanger,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = LinuxGreenDark,
    onPrimary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1C2128),
    surface = Color.White,
    onSurface = Color(0xFF1C2128),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
)

@Composable
fun LinuxHostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
