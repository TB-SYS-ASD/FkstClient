package com.tb.fkst.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(vm: AppViewModel, nav: NavHostController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) { vm.refreshMe() }

    val profile = vm.myProfile
    val stats = vm.myStats

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("我的") },
                scrollBehavior = scrollBehavior,
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
            // 资料卡：点整块进入「我的主页」
            Card(
                onClick = { nav.navigate(Routes.MY_HOME) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FkstAvatar(profile?.avatar ?: "", 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile?.nickName?.ifBlank { null } ?: "加载中…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(
                                profile?.province?.takeIf { it.isNotBlank() },
                                "mid ${vm.repo.currentMid()}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (stats != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "积分 ${stats.coinCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "查看我的主页 ›",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 统计
            if (profile != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatCell("关注", profile.followCount, onClick = { nav.navigate(Routes.SOCIAL) })
                        StatCell("粉丝", profile.fansCount, onClick = { nav.navigate(Routes.SOCIAL) })
                        StatCell("发布", profile.releaseCount)
                        StatCell("答题", profile.answerCount)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 签到：连续 3 天解锁私信
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = vm.coinState?.let {
                                if (it.canSendLetter) "私信已解锁"
                                else "签到 ${it.signDays.coerceAtMost(3)}/3 天解锁私信"
                            } ?: "每日签到",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = vm.coinState?.let {
                                val sign = if (it.signedToday) " · 今日已签" else ""
                                val auto = if (vm.autoCheckIn) " · 自动签到开" else ""
                                "连续 ${it.signDays} 天 · 积分 ${it.coinCount}$sign$auto"
                            } ?: "签到可领积分，连续 3 天解锁私信",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { vm.doCheckIn() },
                        enabled = !vm.checkingIn && vm.coinState?.signedToday != true,
                    ) {
                        Text(if (vm.coinState?.signedToday == true) "已签到" else "签到")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 功能入口
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingRow(
                    icon = Icons.Filled.Person,
                    title = "我的主页",
                    subtitle = "发布 / 赞过 / 收藏",
                    onClick = { nav.navigate(Routes.MY_HOME) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.EditNote,
                    title = "发布笔记",
                    subtitle = "要标题 + 可配 9 张图，两篇间隔至少 5 分钟",
                    onClick = { nav.navigate(Routes.PUBLISH) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "我的笔记",
                    onClick = { nav.navigate(Routes.MY_NOTES) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.Favorite,
                    title = "我赞过的",
                    onClick = { nav.navigate(Routes.LIKED) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.Star,
                    title = "我的收藏",
                    subtitle = if (vm.collectionFeed.loaded && vm.collectionFeed.items.isNotEmpty())
                        "${vm.collectionFeed.items.size} 篇"
                    else "在文章页点⭐收藏",
                    onClick = { nav.navigate(Routes.COLLECTION) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.Groups,
                    title = "关注与粉丝",
                    subtitle = profile?.let { "关注 ${it.followCount} · 粉丝 ${it.fansCount}" },
                    onClick = { nav.navigate(Routes.SOCIAL) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.MarkChatUnread,
                    title = "私信",
                    subtitle = vm.coinState?.let {
                        if (it.canSendLetter) "互关好友之间聊天" else it.signHint
                    } ?: "互关好友之间聊天",
                    onClick = { nav.navigate(Routes.LETTERS) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.Notifications,
                    title = "消息通知",
                    subtitle = stats?.let { if (it.unread > 0) "${it.unread} 条未读" else null },
                    onClick = { nav.navigate(Routes.NOTICES) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = "外观与设置",
                    subtitle = "莫奈取色 · 深浅色 · 关于",
                    onClick = { nav.navigate(Routes.SETTINGS) },
                    trailing = { Icon(Icons.Filled.ChevronRight, null) },
                )
                HorizontalDivider(Modifier.padding(start = 54.dp))
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    onClick = { vm.logout() },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
