package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.core.Constants
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.LayoutToggleButton
import com.tb.fkst.ui.components.NoteFeedList

/**
 * 发现页：顶部「推荐 / 关注」二级切换。
 * 关注流原本是底部导航的独立 tab，现在并进这里，导航栏留给私信。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(vm: AppViewModel, nav: NavHostController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sub by remember { mutableIntStateOf(0) }

    LaunchedEffect(sub) {
        if (sub == 0) {
            if (!vm.discoverFeed.loaded) vm.loadDiscover(reset = true)
        } else {
            if (!vm.followFeed.loaded) vm.loadFollow(reset = true)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (sub == 0) "发现" else "关注") },
                actions = {
                    IconButton(onClick = {
                        if (sub == 0) vm.loadDiscover(reset = true) else vm.loadFollow(reset = true)
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    LayoutToggleButton(columns = vm.listColumns) { vm.toggleListColumns() }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = sub == 0,
                    onClick = { sub = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("推荐") },
                )
                SegmentedButton(
                    selected = sub == 1,
                    onClick = { sub = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("关注") },
                )
            }

            if (sub == 0) {
                val chipState = rememberLazyListState()
                LazyRow(
                    state = chipState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(Constants.CATEGORIES) { index, cat ->
                        FilterChip(
                            selected = vm.category.key == cat.key,
                            onClick = { vm.selectCategory(index) },
                            label = { Text(cat.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                NoteFeedList(
                    state = vm.discoverFeed,
                    onOpen = { note ->
                        vm.openNote(note)
                        nav.navigate(Routes.NOTE)
                    },
                    onRefresh = { vm.loadDiscover(reset = true) },
                    onLoadMore = { vm.loadDiscover(reset = false) },
                    modifier = Modifier.fillMaxWidth(),
                    columns = vm.listColumns,
                )
            } else {
                NoteFeedList(
                    state = vm.followFeed,
                    onOpen = { note ->
                        vm.openNote(note)
                        nav.navigate(Routes.NOTE)
                    },
                    onRefresh = { vm.loadFollow(reset = true) },
                    onLoadMore = { vm.loadFollow(reset = false) },
                    modifier = Modifier.fillMaxWidth(),
                    columns = vm.listColumns,
                )
            }
        }
    }
}
