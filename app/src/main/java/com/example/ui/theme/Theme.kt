package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.data.preferences.AccentColorOption
import com.example.data.preferences.AppThemeMode

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.OCEAN_NAVY,
    accentColor: AccentColorOption = AccentColorOption.SKY_BLUE,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED, AppThemeMode.OCEAN_NAVY -> true
        AppThemeMode.SYSTEM -> systemDark
    }
    val isAmoled = themeMode == AppThemeMode.AMOLED
    val isOceanNavy = themeMode == AppThemeMode.OCEAN_NAVY

    val primaryBase = Color(accentColor.primaryHex)
    val secondaryBase = Color(accentColor.secondaryHex)

    val colorScheme = if (isOceanNavy) {
        val primary = lerp(primaryBase, Color.White, 0.22f)
        val secondary = lerp(secondaryBase, Color.White, 0.28f)
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF041C32),
            primaryContainer = OceanNavySegmentedActive,
            onPrimaryContainer = Color.White,
            secondary = secondary,
            onSecondary = Color(0xFF041C32),
            secondaryContainer = Color(0xFF164A75),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFFFF6B8B),
            onTertiary = Color.White,
            background = OceanNavyBg,
            onBackground = OceanNavyTextPrimary,
            surface = OceanNavyTopBar,
            onSurface = OceanNavyTextPrimary,
            surfaceVariant = OceanNavyMiniPlayer,
            onSurfaceVariant = OceanNavyTextSecondary,
            outline = OceanNavyOutline,
            outlineVariant = OceanNavyDivider
        )
    } else if (useDark) {
        val primary = lerp(primaryBase, Color.White, 0.2f)
        val secondary = lerp(secondaryBase, Color.White, 0.25f)
        val primaryContainer = lerp(primaryBase, Color.Black, 0.62f)
        val secondaryContainer = lerp(secondaryBase, Color.Black, 0.65f)
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF0D0818),
            primaryContainer = primaryContainer,
            onPrimaryContainer = lerp(primaryBase, Color.White, 0.85f),
            secondary = secondary,
            onSecondary = Color(0xFF002026),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = lerp(secondaryBase, Color.White, 0.85f),
            tertiary = NeonPink,
            onTertiary = Color.White,
            background = if (isAmoled) AmoledBlack else DeepSpace,
            onBackground = TextPrimaryDark,
            surface = if (isAmoled) AmoledSurface else DarkSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = if (isAmoled) AmoledSurfaceVariant else DarkSurfaceVariant,
            onSurfaceVariant = TextSecondaryDark,
            outline = if (isAmoled) Color(0xFF2D2D3A) else Color(0xFF474360),
            outlineVariant = if (isAmoled) Color(0xFF1A1A24) else Color(0xFF2C264D)
        )
    } else {
        val primary = lerp(primaryBase, Color.Black, 0.15f)
        val secondary = lerp(secondaryBase, Color.Black, 0.15f)
        val primaryContainer = lerp(primaryBase, Color.White, 0.82f)
        val secondaryContainer = lerp(secondaryBase, Color.White, 0.82f)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = lerp(primaryBase, Color.Black, 0.55f),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = lerp(secondaryBase, Color.Black, 0.55f),
            tertiary = Color(0xFFE11D48),
            onTertiary = Color.White,
            background = LightBackground,
            onBackground = TextPrimaryLight,
            surface = LightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = TextSecondaryLight,
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
