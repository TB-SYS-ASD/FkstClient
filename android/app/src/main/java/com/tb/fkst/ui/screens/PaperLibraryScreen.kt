package com.tb.fkst.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tb.fkst.core.Constants
import com.tb.fkst.data.Paper
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.FeedState
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.LoadingBox
import com.tb.fkst.ui.components.timeAgo

/**
 * 试卷库（底部导航「试卷」页）。
 *
 * 能做的事：按年级 / 教材版本筛选浏览真实试卷、搜试卷、收藏，
 * 点进去能看**题目 + 答案 + 解析**（GetZJPaperById5）。
 *
 * 做不到的事：在线答题。官方的答题页是 H5，恒回「非法访问-1」，
 * 服务端也没开放提交答题记录的接口，所以这里定位是「查卷子 + 看题看解析」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperLibraryScreen(vm: AppViewModel, nav: NavHostController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!vm.paperFeed.loaded) vm.loadPapers(reset = true)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("试卷库") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.PAPER_COLLECTION) }) {
                        Icon(Icons.Filled.Star, contentDescription = "我收藏的试卷")
                    }
                    IconButton(onClick = { vm.loadPapers(reset = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            OutlinedSearchField(
                value = query,
                onValueChange = { query = it },
                onSubmit = { vm.searchPapers(query.trim()) },
                onClear = { query = ""; vm.searchPapers("") },
            )

            if (vm.paperSearchKeyword.isBlank()) {
                // 年级筛选：PAPER_GRADES 第一项是「最新」（不筛选）
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(Constants.PAPER_GRADES) { g ->
                        FilterChip(
                            selected = vm.paperGrade.id == g.id,
                            onClick = { vm.selectPaperGrade(g) },
                            label = { Text(g.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                // 教材版本：跟着年级走，「最新」没有版本可筛
                if (vm.paperVersions.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        item {
                            FilterChip(
                                selected = vm.paperVersionId.isBlank(),
                                onClick = { vm.selectPaperVersion("") },
                                label = { Text("全部版本") },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        items(vm.paperVersions) { v ->
                            FilterChip(
                                selected = vm.paperVersionId == v.id,
                                onClick = { vm.selectPaperVersion(v.id) },
                                label = { Text(v.name) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }

                PaperFeedList(
                    state = vm.paperFeed,
                    collectedIds = vm.collectedPaperIds,
                    onOpen = { paper ->
                        vm.openPaper(paper)
                        nav.navigate(Routes.PAPER_DETAIL)
                    },
                    onCollect = { vm.togglePaperCollection(it) },
                    onRefresh = { vm.loadPapers(reset = true) },
                    onLoadMore = { vm.loadPapers(reset = false) },
                    emptyText = "这个筛选条件下还没有试卷",
                )
            } else {
                PaperFeedList(
                    state = vm.paperSearchFeed,
                    collectedIds = vm.collectedPaperIds,
                    onOpen = { paper ->
                        vm.openPaper(paper)
                        nav.navigate(Routes.PAPER_DETAIL)
                    },
                    onCollect = { vm.togglePaperCollection(it) },
                    onRefresh = { vm.searchPapers(vm.paperSearchKeyword) },
                    onLoadMore = { vm.loadMorePaperSearch() },
                    emptyText = "没搜到试卷，换个关键词试试",
                )
            }
        }
    }
}

/** 我收藏的试卷 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperCollectionScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) {
        vm.loadPaperCollections(reset = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我收藏的试卷") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadPaperCollections(reset = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PaperFeedList(
                state = vm.paperCollectionFeed,
                collectedIds = vm.collectedPaperIds,
                onOpen = { paper ->
                    vm.openPaper(paper)
                    nav.navigate(Routes.PAPER_DETAIL)
                },
                onCollect = { vm.togglePaperCollection(it) },
                onRefresh = { vm.loadPaperCollections(reset = true) },
                onLoadMore = { vm.loadPaperCollections(reset = false) },
                emptyText = "还没有收藏试卷",
            )
        }
    }
}

// ------------------------------------------------------------------ 组件

@Composable
private fun OutlinedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("搜试卷标题，例如「八年级」") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "清空")
                }
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() }),
    )
}

/** 试卷列表：自动分页 + 空态/错误态 */
@Composable
private fun PaperFeedList(
    state: FeedState<Paper>,
    collectedIds: Set<String>,
    onOpen: (Paper) -> Unit,
    onCollect: (Paper) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latest by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                val s = latest
                if (last >= 0 && s.loaded && s.hasMore && !s.loading && !s.loadingMore &&
                    last >= s.items.size - 2
                ) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.loading && state.items.isEmpty()) {
            item { LoadingBox() }
        }
        if (state.error != null && state.items.isEmpty()) {
            item { ErrorBox(state.error, onRetry = onRefresh) }
        }
        if (state.loaded && state.items.isEmpty() && state.error == null && !state.loading) {
            item { EmptyBox(emptyText) }
        }

        items(state.items, key = { it.id }) { paper ->
            PaperCard(
                paper = paper,
                collected = paper.id in collectedIds,
                onCollect = { onCollect(paper) },
                onClick = { onOpen(paper) },
            )
        }

        if (state.loadingMore) {
            item { LoadingBox() }
        }
        if (!state.hasMore && state.items.isNotEmpty()) {
            item { FeedEndHint() }
        }
    }
}

@Composable
private fun PaperCard(
    paper: Paper,
    collected: Boolean,
    onCollect: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            // 封面（服务端给的是一张小图标，尺寸固定即可）
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 68.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (paper.logo.isNotBlank()) {
                    AsyncImage(
                        model = paper.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            SpacerWidth(12.dp)

            Column(Modifier.weight(1f)) {
                Text(
                    text = paper.title.ifBlank { "（无标题试卷）" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SpacerHeight(4.dp)
                Text(
                    text = buildString {
                        append(paper.subtitle)
                        if (paper.isAnswer) append(" · 含答案")
                        val t = timeAgo(paper.createdAt)
                        if (t.isNotBlank()) append(" · ").append(t)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!paper.canOpen) {
                    SpacerHeight(4.dp)
                    Text(
                        text = "暂不支持在线看题",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (collected) "取消收藏" else "收藏",
                    tint = if (collected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onCollect() }
                        .padding(4.dp),
                )
                if (paper.canOpen) {
                    SpacerHeight(2.dp)
                    Text(
                        text = "看题",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpacerWidth(w: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.width(w))
}

@Composable
private fun SpacerHeight(h: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(h))
}

@Composable
private fun FeedEndHint() {
    Text(
        text = "没有更多了",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}

/** 详情页加载中的占位 */
@Composable
fun PaperDetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}
