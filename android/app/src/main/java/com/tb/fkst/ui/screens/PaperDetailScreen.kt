package com.tb.fkst.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.data.PaperDetail
import com.tb.fkst.ui.Routes
import com.tb.fkst.data.PaperQuestion
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox

/**
 * 试卷详情：元信息 + 题目 + 答案 + 解析。
 *
 * 题目走 GetZJPaperById5（pid + type + aid + paperid），**只对 type=1 的同步卷有效**。
 * 答案和解析默认收起，用顶部开关一次性展开 —— 这样能先自己做题再对答案，
 * 算是把这个接口能给的「刷题体验」尽量补回来一点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailScreen(vm: AppViewModel, nav: NavHostController) {
    val paper = vm.currentPaper
    val detail = vm.paperDetail

    // 「显示答案」：默认关，展开后所有题目一起显示答案 + 解析
    var showAnswer by remember { mutableStateOf(false) }
    val collected = paper?.let { it.id in vm.collectedPaperIds } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("试卷详情") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (paper != null) {
                        IconButton(
                            onClick = { nav.navigate(Routes.EXERCISE) },
                            enabled = vm.loggedIn,
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "实时练习",
                                tint = if (vm.loggedIn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.togglePaperCollection(paper) }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = if (collected) "取消收藏" else "收藏",
                                tint = if (collected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            paper == null -> EmptyBox("试卷不存在或已被删除")
            vm.paperDetailLoading -> PaperDetailLoading()
            vm.paperDetailError != null -> Column(Modifier.fillMaxSize().padding(padding)) {
                ErrorBox(vm.paperDetailError ?: "加载失败")
            }
            detail == null -> Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyBox("这份试卷没有可显示的题目")
            }
            else -> PaperDetailBody(
                detail = detail,
                showAnswer = showAnswer,
                onToggleAnswer = { showAnswer = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun PaperDetailBody(
    detail: PaperDetail,
    showAnswer: Boolean,
    onToggleAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = detail.paper.title.ifBlank { "（无标题试卷）" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append(detail.paper.subtitle)
                            append(" · 共 ").append(detail.questionCount).append(" 题")
                            if (detail.paper.isAnswer) append(" · 含答案")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "显示答案与解析",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = showAnswer, onCheckedChange = onToggleAnswer)
                    }
                }
            }
        }

        detail.groups.forEachIndexed { gi, group ->
            item {
                Text(
                    text = "第 ${gi + 1} 大题 · ${group.qtype}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            items(group.questions.size) { qi ->
                QuestionCard(index = qi + 1, q = group.questions[qi], showAnswer = showAnswer)
            }
        }

        item {
            Text(
                text = "本页只做看题与对答案。想真正答题、交卷，点右上角 ▶ 进入官方 H5 实时练习" +
                    "（带本账号合法会话，能正常提交作答）。答题记录可在「我的 → 答题记录」里回看。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun QuestionCard(index: Int, q: PaperQuestion, showAnswer: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "$index.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                Column(Modifier.weight(1f)) {
                    if (q.type.isNotBlank()) {
                        Text(
                            text = q.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = q.stem.ifBlank { "（题干为空）" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (q.options.isNotBlank()) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                        Text(
                            text = q.options,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (showAnswer) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(10.dp),
                ) {
                    Text(
                        text = "答案：${q.answer.ifBlank { "（服务端没给答案）" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (q.explanation.isNotBlank()) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                        Text(
                            text = q.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
