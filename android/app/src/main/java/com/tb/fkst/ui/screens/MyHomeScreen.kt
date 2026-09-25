package com.tb.fkst.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.FeedState
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LayoutToggleButton
import com.tb.fkst.ui.components.NoteFeedList

/**
 * 自己的主页：点「我的」页顶部那张资料卡进来。
 *
 * 布局上把「资料卡 + 切换条」放进了列表的头部（`NoteFeedList(header = …)`），
 * 所以往下翻的时候整个头部会一起折叠收走，而不是一直钉在顶上占半屏。
 * 顶栏也挂了 `enterAlwaysScrollBehavior`：下滑隐藏、上滑立刻回来。
 *
 * 看别人的主页走 [UserScreen]，这里只服务自己。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHomeScreen(vm: AppViewModel, nav: NavHostController) {
    val mid = vm.repo.currentMid()
    val profile = vm.myProfile
    val tab = vm.myHomeTab

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshMe()
        if (!vm.myNotesFeed.loaded) vm.loadMyNotes(reset = true)
    }

    // 切到哪个 tab 才加载哪个，避免一次打三个接口
    LaunchedEffect(tab) {
        when (tab) {
            1 -> if (!vm.likedFeed.loaded) vm.loadLiked(reset = true)
            2 -> if (!vm.collectionFeed.loaded) vm.loadCollection(reset = true)
            else -> if (!vm.myNotesFeed.loaded) vm.loadMyNotes(reset = true)
        }
    }

    fun reload() {
        when (tab) {
            1 -> vm.loadLiked(reset = true)
            2 -> vm.loadCollection(reset = true)
            else -> vm.loadMyNotes(reset = true)
        }
    }

    val feed: FeedState<com.tb.fkst.data.Note> = when (tab) {
        1 -> vm.likedFeed
        2 -> vm.collectionFeed
        else -> vm.myNotesFeed
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("我的主页") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.PUBLISH) }) {
                        Icon(Icons.Filled.EditNote, contentDescription = "发布笔记")
                    }
                    IconButton(onClick = {
                        vm.refreshMe()
                        reload()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    LayoutToggleButton(columns = vm.listColumns) { vm.toggleListColumns() }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NoteFeedList(
                state = feed,
                onOpen = { note ->
                    vm.openNote(note)
                    nav.navigate(Routes.NOTE)
                },
                onRefresh = { reload() },
                onLoadMore = {
                    when (tab) {
                        1 -> vm.loadLiked(reset = false)
                        2 -> vm.loadCollection(reset = false)
                        else -> vm.loadMyNotes(reset = false)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                columns = vm.listColumns,
                header = {
                    Column {
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
                                    FkstAvatar(profile?.avatar ?: "", 62.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = profile?.nickName?.ifBlank { null } ?: "加载中…",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = buildString {
                                                append("mid ").append(mid)
                                                profile?.province?.takeIf { it.isNotBlank() }
                                                    ?.let { append(" · ").append(it) }
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        vm.myStats?.let { s ->
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "积分 ${s.coinCount} · 未读 ${s.unread}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }

                                if (profile != null) {
                                    Spacer(Modifier.height(14.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        HomeStat("关注", profile.followCount) {
                                            nav.navigate(Routes.SOCIAL)
                                        }
                                        Spacer(Modifier.width(24.dp))
                                        HomeStat("粉丝", profile.fansCount) {
                                            nav.navigate(Routes.SOCIAL)
                                        }
                                        Spacer(Modifier.width(24.dp))
                                        HomeStat("发布", profile.releaseCount)
                                        Spacer(Modifier.width(24.dp))
                                        HomeStat("答题", profile.answerCount)
                                    }
                                }
                            }
                        }

                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 6.dp),
                        ) {
                            SegmentedButton(
                                selected = tab == 0,
                                onClick = { vm.selectMyHomeTab(0) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                label = { Text("发布") },
                            )
                            SegmentedButton(
                                selected = tab == 1,
                                onClick = { vm.selectMyHomeTab(1) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                label = { Text("赞过") },
                            )
                            SegmentedButton(
                                selected = tab == 2,
                                onClick = { vm.selectMyHomeTab(2) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                label = { Text("收藏") },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeStat(label: String, value: Int, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
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
