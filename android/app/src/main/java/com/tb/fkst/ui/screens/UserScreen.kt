package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.data.Api
import com.tb.fkst.data.UserProfile
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LayoutToggleButton
import com.tb.fkst.ui.components.NoteFeedList
import com.tb.fkst.ui.friendlyError

/**
 * 别人的主页：资料卡 + 关注 / 私信 + TA 的笔记。
 *
 * 资料卡放在列表头部，往下翻会随之折叠收走（顶栏也会跟着隐藏）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(vm: AppViewModel, nav: NavHostController) {
    val homeId = vm.userTarget

    var profile by remember(homeId) { mutableStateOf<UserProfile?>(null) }
    var following by remember(homeId) { mutableStateOf(false) }
    var loadError by remember(homeId) { mutableStateOf<String?>(null) }
    var busy by remember(homeId) { mutableStateOf(false) }

    val isMe = homeId.isNotBlank() && homeId == vm.repo.currentMid()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(homeId) {
        if (homeId.isBlank()) return@LaunchedEffect
        loadError = null
        try {
            val p = Api.profile(vm.client, homeId)
            profile = p
            following = p.isFollow
        } catch (t: Throwable) {
            loadError = friendlyError(t)
        }
        vm.loadUserNotes(homeId, reset = true)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.nickName?.ifBlank { null } ?: "用户",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isMe) {
                        IconButton(onClick = { nav.navigate(Routes.PUBLISH) }) {
                            Icon(Icons.Filled.EditNote, contentDescription = "发布笔记")
                        }
                    }
                    LayoutToggleButton(columns = vm.listColumns) { vm.toggleListColumns() }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NoteFeedList(
                state = vm.userFeed,
                onOpen = { note ->
                    vm.openNote(note)
                    nav.navigate(Routes.NOTE)
                },
                onRefresh = { vm.loadUserNotes(homeId, reset = true) },
                onLoadMore = { vm.loadUserNotes(homeId, reset = false) },
                modifier = Modifier.fillMaxWidth(),
                columns = vm.listColumns,
                header = {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FkstAvatar(profile?.avatar ?: "", 60.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = profile?.nickName?.ifBlank { null } ?: "…",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = buildString {
                                            append("mid ").append(homeId)
                                            profile?.province?.takeIf { it.isNotBlank() }
                                                ?.let { append(" · ").append(it) }
                                            if (following) append(" · 已关注")
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }

                            if (loadError != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = loadError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            profile?.let { p ->
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    MiniStat("关注", p.followCount)
                                    Spacer(Modifier.width(28.dp))
                                    MiniStat("粉丝", p.fansCount)
                                    Spacer(Modifier.width(28.dp))
                                    MiniStat("发布", p.releaseCount)
                                    Spacer(Modifier.width(28.dp))
                                    MiniStat("答题", p.answerCount)
                                }
                            }

                            if (!isMe && homeId.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                        .copy(alpha = 0.15f)
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            if (busy) return@Button
                                            busy = true
                                            val target = !following
                                            vm.followUser(homeId, target) { ok ->
                                                busy = false
                                                if (ok) {
                                                    following = target
                                                    vm.toast = if (target) "已关注" else "已取消关注"
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (following) Icons.Filled.PersonRemove
                                            else Icons.Filled.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (following) "已关注" else "关注")
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            if (vm.buddies.any { it.homeId == homeId }) {
                                                vm.openLetter(
                                                    homeId,
                                                    vm.buddies.firstOrNull { it.homeId == homeId },
                                                )
                                            } else {
                                                vm.openLetter(
                                                    homeId,
                                                    profile?.let {
                                                        com.tb.fkst.data.Buddy(
                                                            homeId = homeId,
                                                            nickName = it.nickName,
                                                            avatar = it.avatar,
                                                            fans = it.fansCount,
                                                            isMutual = false,
                                                        )
                                                    },
                                                )
                                            }
                                            nav.navigate(Routes.LETTER_CHAT)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Forum,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("私信")
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
