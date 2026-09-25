package com.tb.fkst

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tb.fkst.ui.AppNavHost
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.WallpaperHost
import com.tb.fkst.ui.theme.FkstTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels {
        viewModelFactory {
            initializer { AppViewModel((application as FkstApp).repo) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            FkstTheme(
                themeMode = vm.themeMode,
                dynamicColor = vm.dynamicColor,
                themeStyle = vm.themeStyle,
            ) {
                // 玻璃主题时这里会露出壁纸，卡片本身是半透明的
                WallpaperHost(style = vm.themeStyle, path = vm.wallpaperPath) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        AppNavHost(vm)
                    }
                }
            }
        }
    }
}
