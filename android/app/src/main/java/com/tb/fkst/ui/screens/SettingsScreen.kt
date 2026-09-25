package com.tb.fkst.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.BuildConfig
import com.tb.fkst.core.Constants
import com.tb.fkst.core.UpdateChecker
import com.tb.fkst.data.ThemeMode
import com.tb.fkst.data.ThemeStyle
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(Unit) { vm.refreshCacheSize() }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.setWallpaper(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观与设置") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // ---------------- 外观 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = "莫奈取色",
                    subtitle = if (supportsMonet)
                        "跟随系统壁纸自动生成配色（Material You）"
                    else
                        "需要 Android 12 及以上系统",
                    trailing = {
                        Switch(
                            checked = vm.dynamicColor && supportsMonet,
                            enabled = supportsMonet,
                            onCheckedChange = { vm.updateDynamicColor(it) },
                        )
                    },
                )

                Spacer(Modifier.height(4.dp))

                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "深色模式",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeModeChip("跟随系统", ThemeMode.SYSTEM, vm)
                        ThemeModeChip("浅色", ThemeMode.LIGHT, vm)
                        ThemeModeChip("深色", ThemeMode.DARK, vm)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 卡片样式 / 壁纸 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "卡片样式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StyleChip("默认", ThemeStyle.DEFAULT, vm)
                        StyleChip("透明卡片", ThemeStyle.GLASS, vm)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (vm.themeStyle == ThemeStyle.GLASS)
                            "卡片、列表与顶栏都会透明化，透出下面的壁纸；文字保持不透明，不影响阅读。"
                        else
                            "标准的实心 Material 3 卡片。切到「透明卡片」后可再设一张壁纸。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (vm.themeStyle == ThemeStyle.GLASS) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(onClick = { wallpaperPicker.launch("image/*") }) {
                                Text(if (vm.wallpaperPath.isBlank()) "选择壁纸" else "更换壁纸")
                            }
                            if (vm.wallpaperPath.isNotBlank()) {
                                OutlinedButton(onClick = { vm.clearWallpaper() }) {
                                    Text("清除壁纸")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (vm.wallpaperPath.isBlank())
                                "当前没设壁纸，用的是主题色渐变兜底。"
                            else
                                "壁纸已设置（存在应用私有目录，卸载会一起清掉）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 列表排版 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "列表排版",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = vm.listColumns == 1,
                            onClick = { vm.applyListColumns(1) },
                            label = { Text("单列大卡") },
                        )
                        FilterChip(
                            selected = vm.listColumns == 2,
                            onClick = { vm.applyListColumns(2) },
                            label = { Text("一排两个") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "作用于发现 / 关注 / 搜索 / 我的主页这些文章列表，"
                            + "列表页顶栏右侧也有同样的切换按钮。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 签到 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingRow(
                    icon = Icons.Filled.EventAvailable,
                    title = "自动签到",
                    subtitle = vm.coinState?.let {
                        val signed = if (it.signedToday) "今日已签 · " else ""
                        val tail = if (it.canSendLetter) " · 私信已解锁" else " · 满 3 天解锁私信"
                        "${signed}连续 ${it.signDays} 天$tail"
                    } ?: "打开 App 自动完成每日签到，连续 3 天解锁私信",
                    trailing = {
                        Switch(
                            checked = vm.autoCheckIn,
                            onCheckedChange = { vm.updateAutoCheckIn(it) },
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 私信防撤回 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingRow(
                    icon = Icons.Filled.Security,
                    title = "私信防撤回",
                    subtitle = if (vm.antiRecall)
                        "对方撤回（删除）的消息会在本地保留 ${vm.letterBackupCount} 段会话的备份，" +
                            "灰显标注「已撤回」但内容还在"
                    else
                        "关闭状态：对方撤回的消息会直接从聊天里消失",
                    trailing = {
                        Switch(
                            checked = vm.antiRecall,
                            onCheckedChange = { vm.applyAntiRecall(it) },
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 存储与缓存 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "存储与缓存",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "当前占用 " + vm.cacheText.ifBlank { "计算中…" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { vm.clearCache() },
                            enabled = !vm.clearingCache,
                        ) {
                            if (vm.clearingCache) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (vm.clearingCache) "清理中…" else "清理缓存")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "清理的是图片缓存（Coil）与应用临时目录，不会动壁纸、"
                            + "备注、置顶、投币记录和账号登录状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.clearLetterBackup() },
                            enabled = vm.letterBackupCount > 0,
                        ) { Text("清空防撤回备份") }
                        OutlinedButton(onClick = { vm.clearAuthorCache() }) {
                            Text("清空作者缓存")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 配色预览 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "当前配色",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Swatch(MaterialTheme.colorScheme.primary)
                        Swatch(MaterialTheme.colorScheme.secondary)
                        Swatch(MaterialTheme.colorScheme.tertiary)
                        Swatch(MaterialTheme.colorScheme.primaryContainer)
                        Swatch(MaterialTheme.colorScheme.surfaceVariant)
                        Swatch(MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (vm.dynamicColor && supportsMonet)
                            "来源：系统壁纸（莫奈动态取色）"
                        else
                            "来源：内置 Material 3 蓝色方案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 关于 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))

                    // ---- 检查更新（手动）----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("检查更新", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text(
                                text = vm.updateHint
                                    ?: "当前 ${BuildConfig.VERSION_NAME} · 从 GitHub Releases 检查",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.checkForUpdate(silent = false) },
                            enabled = !vm.updateChecking,
                        ) {
                            if (vm.updateChecking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Text("检查")
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    AboutLine("应用版本", BuildConfig.VERSION_NAME)
                    AboutLine("更新来源", "github.com/${Constants.GITHUB_OWNER}/${Constants.GITHUB_REPO}")
                    AboutLine("接口地址", Constants.API_BASE.trimEnd('/'))
                    AboutLine("应用标识", "com.yaerxing.fkst 第三方客户端")
                    AboutLine("动态取色", if (supportsMonet) "系统支持" else "系统不支持")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "本项目为个人学习用途的非官方客户端，接口协议来自对官方客户端的分析。"
                            + "请勿用于商业用途，账号安全与使用风险请自行评估。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeModeChip(label: String, mode: ThemeMode, vm: AppViewModel) {
    FilterChip(
        selected = vm.themeMode == mode,
        onClick = { vm.updateThemeMode(mode) },
        label = { Text(label) },
    )
}

@Composable
private fun StyleChip(label: String, style: ThemeStyle, vm: AppViewModel) {
    FilterChip(
        selected = vm.themeStyle == style,
        onClick = { vm.updateThemeStyle(style) },
        label = { Text(label) },
    )
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
    )
}

@Composable
private fun AboutLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}
