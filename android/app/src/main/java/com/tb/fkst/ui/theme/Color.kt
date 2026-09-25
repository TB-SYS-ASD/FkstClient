package com.tb.fkst.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 备用配色（Material 3 蓝色系）。
 * Android 12+ 会优先走「莫奈」动态取色（取系统壁纸主色），
 * 这里的方案用于 Android 11 及以下，或者在设置里关掉动态取色时使用。
 */

// ---------------------------------------------------------------- Light
private val LPrimary = Color(0xFF4355B9)
private val LOnPrimary = Color(0xFFFFFFFF)
private val LPrimaryContainer = Color(0xFFDEE0FF)
private val LOnPrimaryContainer = Color(0xFF00105C)

private val LSecondary = Color(0xFF5B5D72)
private val LOnSecondary = Color(0xFFFFFFFF)
private val LSecondaryContainer = Color(0xFFE0E1F9)
private val LOnSecondaryContainer = Color(0xFF181A2C)

private val LTertiary = Color(0xFF77536D)
private val LOnTertiary = Color(0xFFFFFFFF)
private val LTertiaryContainer = Color(0xFFFFD7F0)
private val LOnTertiaryContainer = Color(0xFF2D1228)

private val LError = Color(0xFFBA1A1A)
private val LOnError = Color(0xFFFFFFFF)
private val LErrorContainer = Color(0xFFFFDAD6)
private val LOnErrorContainer = Color(0xFF410002)

private val LBackground = Color(0xFFFBF8FF)
private val LOnBackground = Color(0xFF1B1B21)
private val LSurface = Color(0xFFFBF8FF)
private val LOnSurface = Color(0xFF1B1B21)
private val LSurfaceVariant = Color(0xFFE3E1EC)
private val LOnSurfaceVariant = Color(0xFF46464F)
private val LOutline = Color(0xFF767680)
private val LOutlineVariant = Color(0xFFC7C5D0)

private val LSurfaceDim = Color(0xFFDBD9E0)
private val LSurfaceBright = Color(0xFFFBF8FF)
private val LSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LSurfaceContainerLow = Color(0xFFF5F2FA)
private val LSurfaceContainer = Color(0xFFEFEDF4)
private val LSurfaceContainerHigh = Color(0xFFE9E7EF)
private val LSurfaceContainerHighest = Color(0xFFE3E1E9)

private val LInverseSurface = Color(0xFF303036)
private val LInverseOnSurface = Color(0xFFF3F0F7)
private val LInversePrimary = Color(0xFFB9C3FF)

// ---------------------------------------------------------------- Dark
private val DPrimary = Color(0xFFB9C3FF)
private val DOnPrimary = Color(0xFF08218A)
private val DPrimaryContainer = Color(0xFF293CA0)
private val DOnPrimaryContainer = Color(0xFFDEE0FF)

private val DSecondary = Color(0xFFC4C5DD)
private val DOnSecondary = Color(0xFF2D2F42)
private val DSecondaryContainer = Color(0xFF434559)
private val DOnSecondaryContainer = Color(0xFFE0E1F9)

private val DTertiary = Color(0xFFE7B9DB)
private val DOnTertiary = Color(0xFF45263D)
private val DTertiaryContainer = Color(0xFF5E3C55)
private val DOnTertiaryContainer = Color(0xFFFFD7F0)

private val DError = Color(0xFFFFB4AB)
private val DOnError = Color(0xFF690005)
private val DErrorContainer = Color(0xFF93000A)
private val DOnErrorContainer = Color(0xFFFFDAD6)

private val DBackground = Color(0xFF131318)
private val DOnBackground = Color(0xFFE4E1E9)
private val DSurface = Color(0xFF131318)
private val DOnSurface = Color(0xFFE4E1E9)
private val DSurfaceVariant = Color(0xFF46464F)
private val DOnSurfaceVariant = Color(0xFFC7C5D0)
private val DOutline = Color(0xFF90909A)
private val DOutlineVariant = Color(0xFF46464F)

private val DSurfaceDim = Color(0xFF131318)
private val DSurfaceBright = Color(0xFF39383F)
private val DSurfaceContainerLowest = Color(0xFF0E0E13)
private val DSurfaceContainerLow = Color(0xFF1B1B21)
private val DSurfaceContainer = Color(0xFF1F1F25)
private val DSurfaceContainerHigh = Color(0xFF29292F)
private val DSurfaceContainerHighest = Color(0xFF34343A)

private val DInverseSurface = Color(0xFFE4E1E9)
private val DInverseOnSurface = Color(0xFF303036)
private val DInversePrimary = Color(0xFF4355B9)

val LightColors = lightColorScheme(
    primary = LPrimary,
    onPrimary = LOnPrimary,
    primaryContainer = LPrimaryContainer,
    onPrimaryContainer = LOnPrimaryContainer,
    secondary = LSecondary,
    onSecondary = LOnSecondary,
    secondaryContainer = LSecondaryContainer,
    onSecondaryContainer = LOnSecondaryContainer,
    tertiary = LTertiary,
    onTertiary = LOnTertiary,
    tertiaryContainer = LTertiaryContainer,
    onTertiaryContainer = LOnTertiaryContainer,
    error = LError,
    onError = LOnError,
    errorContainer = LErrorContainer,
    onErrorContainer = LOnErrorContainer,
    background = LBackground,
    onBackground = LOnBackground,
    surface = LSurface,
    onSurface = LOnSurface,
    surfaceVariant = LSurfaceVariant,
    onSurfaceVariant = LOnSurfaceVariant,
    outline = LOutline,
    outlineVariant = LOutlineVariant,
    surfaceDim = LSurfaceDim,
    surfaceBright = LSurfaceBright,
    surfaceContainerLowest = LSurfaceContainerLowest,
    surfaceContainerLow = LSurfaceContainerLow,
    surfaceContainer = LSurfaceContainer,
    surfaceContainerHigh = LSurfaceContainerHigh,
    surfaceContainerHighest = LSurfaceContainerHighest,
    inverseSurface = LInverseSurface,
    inverseOnSurface = LInverseOnSurface,
    inversePrimary = LInversePrimary,
)

val DarkColors = darkColorScheme(
    primary = DPrimary,
    onPrimary = DOnPrimary,
    primaryContainer = DPrimaryContainer,
    onPrimaryContainer = DOnPrimaryContainer,
    secondary = DSecondary,
    onSecondary = DOnSecondary,
    secondaryContainer = DSecondaryContainer,
    onSecondaryContainer = DOnSecondaryContainer,
    tertiary = DTertiary,
    onTertiary = DOnTertiary,
    tertiaryContainer = DTertiaryContainer,
    onTertiaryContainer = DOnTertiaryContainer,
    error = DError,
    onError = DOnError,
    errorContainer = DErrorContainer,
    onErrorContainer = DOnErrorContainer,
    background = DBackground,
    onBackground = DOnBackground,
    surface = DSurface,
    onSurface = DOnSurface,
    surfaceVariant = DSurfaceVariant,
    onSurfaceVariant = DOnSurfaceVariant,
    outline = DOutline,
    outlineVariant = DOutlineVariant,
    surfaceDim = DSurfaceDim,
    surfaceBright = DSurfaceBright,
    surfaceContainerLowest = DSurfaceContainerLowest,
    surfaceContainerLow = DSurfaceContainerLow,
    surfaceContainer = DSurfaceContainer,
    surfaceContainerHigh = DSurfaceContainerHigh,
    surfaceContainerHighest = DSurfaceContainerHighest,
    inverseSurface = DInverseSurface,
    inverseOnSurface = DInverseOnSurface,
    inversePrimary = DInversePrimary,
)
