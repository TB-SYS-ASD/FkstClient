package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.data.Buddy
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LoadingBox

/**
 * 关注 / 粉丝列表。
 *
 * `GetSTBuddies` 是分页接口（一页 10 条，`over=true` 才表示取完），
 * 早先只拉第一页，所以关注几十个人也只看得到 10 个。现在滚到底自动续拉下一页，
 * 并且 tab 上的数字直接用资料里的总数，而不是已加载的条数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadSocialIfNeeded() }

    val feed = vm.socialFeed
    val list = feed.items
    val listState = rememberLazyListState()
    val latest by rememberUpdatedState(feed)

    // 滚到底自动续拉
    LaunchedEffect(listState, vm.socialTab) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                val s = latest
                if (last >= 0 && s.loaded && s.hasMore && !s.loading && !s.loadingMore &&
                    last >= s.items.size - 2
                ) {
                    vm.loadMoreSocial()
                }
            }
    }

    val profile = vm.myProfile
    val followTotal = profile?.followCount ?: list.size
    val fansTotal = profile?.fansCount ?: list.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关注与粉丝") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadSocial(reset = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = vm.socialTab) {
                Tab(
                    selected = vm.socialTab == 0,
                    onClick = { vm.selectSocialTab(0) },
                    text = { Text("关注 $followTotal") },
                )
                Tab(
                    selected = vm.socialTab == 1,
                    onClick = { vm.selectSocialTab(1) },
                    text = { Text("粉丝 $fansTotal") },
                )
            }

            when {
                feed.loading && list.isEmpty() -> LoadingBox()
                feed.error != null && list.isEmpty() ->
                    ErrorBox(feed.error!!, onRetry = { vm.loadSocial(reset = true) })
                list.isEmpty() ->
                    EmptyBox(if (vm.socialTab == 0) "还没有关注任何人" else "还没有粉丝")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.size) { i ->
                        val b = list[i]
                        BuddyRow(
                            buddy = b,
                            onOpen = {
                                vm.userTarget = b.homeId
                                nav.navigate(Routes.USER)
                            },
                            onChat = {
                                vm.openLetter(b.homeId, b)
                                nav.navigate(Routes.LETTER_CHAT)
                            },
                        )
                    }
                    item {
                        when {
                            feed.loadingMore -> LoadingBox()
                            feed.hasMore -> TextButton(
                                onClick = { vm.loadMoreSocial() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("加载更多（已加载 ${list.size} / $followTotal）") }
                            else -> Text(
                                text = "已全部加载（共 ${list.size} 人）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuddyRow(buddy: Buddy, onOpen: () -> Unit, onChat: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FkstAvatar(buddy.avatar, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buddy.displayName.ifBlank { "用户 ${buddy.homeId}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("mid ${buddy.homeId}")
                        if (buddy.fans > 0) append(" · 粉丝 ${buddy.fans}")
                        if (buddy.isMutual) append(" · 互关")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (buddy.isMutual) {
                TextButton(onClick = onChat) {
                    Icon(
                        Icons.Filled.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("私信")
                }
            }
        }
    }
}
