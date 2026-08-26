package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = EmeraldDarkPrimary,
        onPrimary = EmeraldOnPrimary,
        primaryContainer = EmeraldDarkContainer,
        onPrimaryContainer = EmeraldOnContainer,
        secondary = GoldAccent,
        secondaryContainer = GoldContainer,
        onSecondaryContainer = GoldOnContainer,
        background = SlateDarkBackground,
        surface = SlateDarkSurface,
        surfaceVariant = SlateDarkSurfaceVariant,
        onBackground = SlateDarkTextPrimary,
        onSurface = SlateDarkTextPrimary,
        onSurfaceVariant = SlateDarkTextSecondary,
        outline = SlateDarkBorder
    )

private val LightColorScheme = DarkColorScheme // Modern finance dark mode default

@Composable
fun CashFlowTheme(
    darkTheme: Boolean = true, // Force Dark Mode for sleek modern finance feel
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    CashFlowTheme(darkTheme = darkTheme, content = content)
}


