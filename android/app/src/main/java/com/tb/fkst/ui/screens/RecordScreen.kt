package com.tb.fkst.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.EmptyBox

/**
 * 我的答题记录（GetRecordShuatiQuestion1）：按「上一题 / 下一题」逐条回看。
 *
 * 只显示题干 + 选项（服务端记录里没有对错）；滚到最后一条且还有更多时自动续拉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vm: AppViewModel, nav: NavHostController) {
    val feed = vm.recordFeed
    var index by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!feed.loaded) vm.loadRecords(reset = true)
    }

    val items = feed.items
    val total = items.size

    // 滚到最后一题时自动续拉（记录 over 恒 False，靠短页停）
    LaunchedEffect(index, total, feed.hasMore, feed.loadingMore) {
        if (total > 0 && index >= total - 1 && feed.hasMore && !feed.loadingMore) {
            vm.loadRecords(reset = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的答题记录") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            feed.loading && total == 0 -> BoxCenter(padding) { CircularProgressIndicator() }
            feed.loaded && total == 0 -> BoxCenter(padding) {
                EmptyBox(feed.error ?: "还没有答题记录")
            }
            total == 0 -> BoxCenter(padding) { CircularProgressIndicator() }
            else -> {
                val q = items[index]
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (index > 0) index-- },
                            enabled = index > 0,
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一题") }
                        Text(
                            "第 ${index + 1} / $total 题",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { if (index < total - 1) index++ },
                            enabled = index < total - 1,
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一题") }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = q.questionText.ifBlank { "（题干为空）" },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (q.options.isNotBlank()) {
                                Spacer8()
                                q.options.lines().forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                            if (q.hasSublist) {
                                Spacer8()
                                Text(
                                    text = "含子题",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            if (q.subject.isNotBlank()) {
                                Spacer8()
                                Text(
                                    text = "学科：${q.subject}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }

                    if (feed.loadingMore) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxCenter(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
