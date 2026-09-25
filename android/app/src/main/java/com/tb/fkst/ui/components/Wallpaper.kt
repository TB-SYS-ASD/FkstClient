package com.tb.fkst.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.tb.fkst.data.ThemeStyle
import com.tb.fkst.ui.theme.LocalSolidBackground
import java.io.File

/**
 * 玻璃主题的最底层：先铺一块实底，再放壁纸（没设壁纸就用主色渐变兜底），
 * 最后盖一层很淡的遮罩保证文字对比度。
 *
 * 上层内容里的卡片因为配色是半透明的，就会透出这里的壁纸。
 */
@Composable
fun WallpaperHost(
    style: ThemeStyle,
    path: String,
    content: @Composable () -> Unit,
) {
    val solid = LocalSolidBackground.current
    val darkBase = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(Modifier.fillMaxSize().background(solid)) {
        if (style == ThemeStyle.GLASS) {
            val file = remember(path) {
                path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
            }
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.secondary,
                                )
                            )
                        )
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (darkBase) Color.Black.copy(alpha = 0.34f)
                        else Color.White.copy(alpha = 0.10f)
                    )
            )
        }
        content()
    }
}
