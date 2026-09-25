package com.tb.fkst.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tb.fkst.data.ThemeMode
import com.tb.fkst.data.ThemeStyle

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 「不透明底色」——玻璃主题下 background / surface 都是半透明的，
 * 需要这个来铺一层实底，免得透明色叠到窗口的黑色背景上。
 */
val LocalSolidBackground = staticCompositionLocalOf { Color(0xFFFEF7FF) }

/** 玻璃（透明卡片）主题下卡片容器的不透明度 */
private const val GLASS_CONTAINER_ALPHA = 0.42f
private const val GLASS_SURFACE_ALPHA = 0.30f
private const val GLASS_TINTED_ALPHA = 0.62f

/**
 * 把配色方案「玻璃化」：底色与容器都调成半透明，
 * 这样卡片就会透出后面的壁纸，文字保持不透明保证可读性。
 */
private fun ColorScheme.glassy(): ColorScheme = copy(
    background = background.copy(alpha = 0.36f),
    surface = surface.copy(alpha = GLASS_SURFACE_ALPHA),
    surfaceContainerLowest = surfaceContainerLowest.copy(alpha = 0.26f),
    surfaceContainerLow = surfaceContainerLow.copy(alpha = 0.32f),
    surfaceContainer = surfaceContainer.copy(alpha = GLASS_CONTAINER_ALPHA),
    surfaceContainerHigh = surfaceContainerHigh.copy(alpha = 0.50f),
    surfaceContainerHighest = surfaceContainerHighest.copy(alpha = 0.58f),
    surfaceVariant = surfaceVariant.copy(alpha = 0.46f),
    surfaceDim = surfaceDim.copy(alpha = 0.55f),
    surfaceBright = surfaceBright.copy(alpha = 0.55f),
    inverseSurface = inverseSurface.copy(alpha = 0.85f),
    primaryContainer = primaryContainer.copy(alpha = GLASS_TINTED_ALPHA),
    secondaryContainer = secondaryContainer.copy(alpha = GLASS_TINTED_ALPHA),
    tertiaryContainer = tertiaryContainer.copy(alpha = GLASS_TINTED_ALPHA),
    surfaceTint = Color.Transparent,
)

/**
 * 主题：
 * - Android 12+ 且开启动态取色时，用系统壁纸主色生成 ColorScheme（莫奈）
 * - 否则回落到内置的 Material 3 蓝色方案
 * - [themeStyle] = GLASS 时所有卡片容器透明化，配合自定义壁纸使用
 */
@Composable
fun FkstTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    themeStyle: ThemeStyle = ThemeStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base: ColorScheme = when {
        dynamicColor && supportsMonet ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }

    val colorScheme = if (themeStyle == ThemeStyle.GLASS) base.glassy() else base

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalSolidBackground provides base.background) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
