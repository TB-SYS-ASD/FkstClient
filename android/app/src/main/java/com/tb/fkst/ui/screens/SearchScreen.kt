package com.tb.fkst.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.LayoutToggleButton
import com.tb.fkst.ui.components.LoadingBox
import com.tb.fkst.ui.components.timeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, nav: NavHostController) {
    var input by remember { mutableStateOf(vm.searchKeyword) }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                actions = {
                    LayoutToggleButton(columns = vm.listColumns) { vm.toggleListColumns() }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("搜文章 / 题目，回车开始") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = {
                            input = ""
                            vm.search("")
                        }) { Icon(Icons.Filled.Clear, contentDescription = "清空") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    vm.search(input)
                }),
            )

            Spacer(Modifier.height(4.dp))

            val state = vm.searchFeed
            val listState = rememberLazyListState()
            val latest by rememberUpdatedState(state)

            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .collect { last ->
                        val s = latest
                        if (last >= 0 && s.loaded && s.hasMore && !s.loading &&
                            !s.loadingMore && last >= s.items.size - 2
                        ) {
                            vm.loadMoreSearch()
                        }
                    }
            }

            if (vm.listColumns >= 2) {
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .collect { last ->
                            val s = latest
                            if (last >= 0 && s.loaded && s.hasMore && !s.loading &&
                                !s.loadingMore && last >= s.items.size - 3
                            ) {
                                vm.loadMoreSearch()
                            }
                        }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!state.loaded && state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "输入关键词开始搜索",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.error != null && state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorBox(state.error, onRetry = { vm.search(vm.searchKeyword) })
                        }
                    }
                    if (state.loaded && state.items.isEmpty() && state.error == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) { EmptyBox("没有搜到相关内容") }
                    }
                    items(state.items) { item ->
                        SearchResultTile(
                            item = item,
                            onClick = {
                                vm.openNote(item.toNote())
                                nav.navigate(Routes.NOTE)
                            },
                        )
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }
                    }
                    if (!state.hasMore && state.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { NoMoreHint() }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        if (!state.loaded && state.items.isEmpty()) {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "输入关键词开始搜索",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (state.loading && state.items.isEmpty()) {
                            LoadingBox()
                        } else if (state.error != null && state.items.isEmpty()) {
                            ErrorBox(state.error, onRetry = { vm.search(vm.searchKeyword) })
                        } else if (state.loaded && state.items.isEmpty()) {
                            EmptyBox("没有搜到相关内容")
                        }
                    }

                    items(state.items.size) { i ->
                        val item = state.items[i]
                        SearchResultCard(
                            item = item,
                            onClick = {
                                vm.openNote(item.toNote())
                                nav.navigate(Routes.NOTE)
                            },
                        )
                    }

                    if (state.loadingMore) item { LoadingBox() }
                    if (!state.hasMore && state.items.isNotEmpty()) item { NoMoreHint() }
                }
            }
        }
    }
}

/** 双列搜索结果时的空/错/载入占位 */
@Composable
private fun NoMoreHint() {
    Text(
        text = "没有更多了",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

/** 双列排版下的搜索结果小卡：缩略图在上，标题在下 */
@Composable
private fun SearchResultTile(
    item: com.tb.fkst.data.SearchItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            if (item.thumb.isNotBlank()) {
                AsyncImage(
                    model = item.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = item.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = timeAgo(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: com.tb.fkst.data.SearchItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.thumb.isNotBlank()) {
                AsyncImage(
                    model = item.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "nid ${item.nid} · ${timeAgo(item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
