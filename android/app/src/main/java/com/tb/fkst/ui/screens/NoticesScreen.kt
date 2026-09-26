package com.tb.fkst.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.data.NoticeKind
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LoadingBox
import com.tb.fkst.ui.components.timeAgo

/**
 * 消息通知页。
 *
 * 不再干巴巴显示「标题」，而是按动作类型渲染成：
 *
 * ```
 * [头像] 拾秋  评论了你的笔记
 *        「这个作业帮也是扫的那个二维码了」
 *        《如何一个月背完3500词》   ·  2 小时前
 * ```
 *
 * - `actor` 是发起人昵称，`action` 是动作（评论 / 点赞 / 投币 / 系统）
 * - `content` 是评论/回复的原文
 * - `noteTitle` 是相关文章标题，整卡点击跳到该文章详情
 *   （系统消息没有 `object_id` → 不可跳，卡片不高亮、不响应点击）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(vm: AppViewModel, nav: NavHostController) {
    val state = vm.noticesFeed

    LaunchedEffect(Unit) { if (!state.loaded) vm.loadNotices(true) }

    // 顶部筛选：全部 / 评论 / 点赞 / 投币 / 系统（与 GetSTNotices 的 type 对应）
    val tabs = listOf(
        "0" to "全部",
        "2" to "评论",
        "3" to "点赞",
        "4" to "投币",
        "1" to "系统",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息通知") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 分类筛选条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { (type, label) ->
                    FilterChip(
                        selected = vm.noticesType == type,
                        onClick = { vm.selectNoticesType(type) },
                        label = { Text(label) },
                    )
                }
            }

            val listState = rememberLazyListState()
            val latest by rememberUpdatedState(state)
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .collect { last ->
                        val s = latest
                        if (last >= 0 && s.loaded && s.hasMore && !s.loading && !s.loadingMore &&
                            last >= s.items.size - 2
                        ) {
                            vm.loadNotices(false)
                        }
                    }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.loading && state.items.isEmpty()) item { LoadingBox() }
                if (state.error != null && state.items.isEmpty()) {
                    item { ErrorBox(state.error, onRetry = { vm.loadNotices(true) }) }
                }
                if (state.loaded && state.items.isEmpty() && state.error == null) {
                    item { EmptyBox("暂时没有新通知") }
                }

                items(state.items.size) { i ->
                    val n = state.items[i]
                    val jumpable = n.targetNoteId.isNotBlank()
                    Card(
                        onClick = {
                            if (jumpable) {
                                vm.openNoteById(n.targetNoteId)
                                nav.navigate(Routes.NOTE)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            FkstAvatar(n.logo, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                // 第一行：头像 + 动作
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val icon = when (n.kind) {
                                        NoticeKind.COMMENT -> Icons.Filled.ChatBubble
                                        NoticeKind.LIKE -> Icons.Filled.Favorite
                                        NoticeKind.COIN -> Icons.Filled.Paid
                                        else -> Icons.Filled.Notifications
                                    }
                                    val tint = when (n.kind) {
                                        NoticeKind.LIKE -> MaterialTheme.colorScheme.error
                                        NoticeKind.COIN -> MaterialTheme.colorScheme.primary
                                        NoticeKind.COMMENT -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (n.actor.isNotBlank()) "${n.actor} ${n.action}" else n.action,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                // 评论/回复的原文
                                if (n.content.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "「${n.content}」",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // 相关文章标题
                                if (n.noteTitle.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "《${n.noteTitle}》",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (jumpable) {
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                Icons.Filled.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = timeAgo(n.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }

                if (state.loadingMore) item { LoadingBox() }
            }
        }
    }
}
