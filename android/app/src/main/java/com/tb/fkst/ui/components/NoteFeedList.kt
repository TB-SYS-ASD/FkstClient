package com.tb.fkst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tb.fkst.data.Note
import com.tb.fkst.ui.FeedState

/**
 * 通用的笔记列表：自动分页、下拉到底部加载更多、错误/空态。
 *
 * @param columns 1 = 单列大卡（默认），2 = 双列（一排两个），由「排版按钮」切换。
 * @param header  列表顶部的整块内容（个人主页的资料卡就是这么做成「随翻页折叠」的：
 *                放进列表第一项，往下滚就自然被划走，而不是一直钉在顶部）。
 */
@Composable
fun NoteFeedList(
    state: FeedState<Note>,
    onOpen: (Note) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    header: (@Composable () -> Unit)? = null,
) {
    if (columns >= 2) {
        NoteGridFeed(state, onOpen, onRefresh, onLoadMore, modifier, header)
    } else {
        NoteColumnFeed(state, onOpen, onRefresh, onLoadMore, modifier, header)
    }
}

// ------------------------------------------------------------------ 单列

@Composable
private fun NoteColumnFeed(
    state: FeedState<Note>,
    onOpen: (Note) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
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
        header?.let { item { it() } }

        if (state.loading && state.items.isEmpty()) {
            item { LoadingBox() }
        }
        if (state.error != null && state.items.isEmpty()) {
            item { ErrorBox(state.error, onRetry = onRefresh) }
        }
        if (state.loaded && state.items.isEmpty() && state.error == null) {
            item { EmptyBox() }
        }

        items(state.items.size) { i ->
            val note = state.items[i]
            NoteCard(note = note, onClick = { onOpen(note) })
        }

        if (state.loadingMore) {
            item { LoadingBox() }
        }
        if (!state.hasMore && state.items.isNotEmpty()) {
            item { FeedEndHint() }
        }
    }
}

// ------------------------------------------------------------------ 双列

@Composable
private fun NoteGridFeed(
    state: FeedState<Note>,
    onOpen: (Note) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val gridState = rememberLazyGridState()
    val latest by rememberUpdatedState(state)

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                val s = latest
                if (last >= 0 && s.loaded && s.hasMore && !s.loading && !s.loadingMore &&
                    last >= s.items.size - 3
                ) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header?.let { item(span = { GridItemSpan(maxLineSpan) }) { it() } }

        if (state.loading && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }
        }
        if (state.error != null && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBox(state.error, onRetry = onRefresh)
            }
        }
        if (state.loaded && state.items.isEmpty() && state.error == null) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyBox() }
        }

        items(state.items) { note ->
            NoteGridCard(note = note, onClick = { onOpen(note) })
        }

        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }
        }
        if (!state.hasMore && state.items.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { FeedEndHint() }
        }
    }
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
