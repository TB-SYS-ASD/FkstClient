package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.tb.fkst.data.Note
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.FeedState
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.LayoutToggleButton
import com.tb.fkst.ui.components.NoteFeedList

/** 通用笔记列表页：我的笔记 / 我赞过的 / 某人的笔记 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    title: String,
    state: FeedState<Note>,
    vm: AppViewModel,
    nav: NavHostController,
    onLoad: (Boolean) -> Unit,
) {
    LaunchedEffect(title) {
        if (!state.loaded) onLoad(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onLoad(true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    LayoutToggleButton(columns = vm.listColumns) { vm.toggleListColumns() }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NoteFeedList(
                state = state,
                onOpen = { note ->
                    vm.openNote(note)
                    nav.navigate(Routes.NOTE)
                },
                onRefresh = { onLoad(true) },
                onLoadMore = { onLoad(false) },
                columns = vm.listColumns,
            )
        }
    }
}
