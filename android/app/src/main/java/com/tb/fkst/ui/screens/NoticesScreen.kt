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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LoadingBox
import com.tb.fkst.ui.components.timeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesScreen(vm: AppViewModel, nav: NavHostController) {
    val state = vm.noticesFeed

    LaunchedEffect(Unit) { if (!state.loaded) vm.loadNotices(true) }

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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
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
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        FkstAvatar(n.avatar, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = n.title.ifBlank { n.nickName.ifBlank { "系统通知" } },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            if (n.content.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = n.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
