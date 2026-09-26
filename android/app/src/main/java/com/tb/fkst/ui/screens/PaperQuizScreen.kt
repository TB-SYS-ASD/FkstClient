package com.tb.fkst.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tb.fkst.data.PaperDetail
import com.tb.fkst.data.PaperQuestion
import com.tb.fkst.ui.components.EmptyBox

/**
 * 原生刷题（官方 H5 不给题面时的兜底）。
 *
 * 背景：paperExercises 页面对**部分试卷**会返回「降级版」—— 页面里只有题目 ID、
 * 不带 shuati-fun 渲染引擎，题干数据要宿主端通过 `getCacheData()` 注入。官方 App
 * 之外任何客户端进去都是空壳（我们实测：那张「答案暂缺」的仁爱版 Unit8 卷只有
 * 54KB，而正常卷是 32 万+ 字节）。
 *
 * 好在题目内容从 GetZJPaperById5 能完整拿到（题干 / 选项 / 答案 / 解析），所以这里
 * 自己做一套交互刷题：选题、对答案、看解析、翻页。数据来源和「试卷详情」同一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperQuizScreen(
    detail: PaperDetail,
    onBack: () -> Unit,
    onSwitchToH5: (() -> Unit)? = null,
    banner: String? = null,
    modifier: Modifier = Modifier,
) {
    // 拍平：把「大题 → 小题」摊成一条线，方便上一题/下一题
    val flat = remember(detail) {
        detail.groups.flatMap { g -> g.questions.map { q -> g.qtype to q } }
    }
    var index by remember(detail) { mutableStateOf(0) }
    val picks = remember(detail) { mutableStateMapOf<String, List<String>>() }
    val revealed = remember(detail) { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    if (flat.isEmpty()) {
        Box(modifier.fillMaxSize()) { EmptyBox("这份试卷没有可作答的题目") }
        return
    }

    val safeIndex = index.coerceIn(0, flat.lastIndex)
    val (qtype, q) = flat[safeIndex]
    val myPicks = picks[q.id].orEmpty()
    val isRevealed = revealed.contains(q.id)

    // 换题时回到顶部
    LaunchedEffect(safeIndex) { listState.scrollToItem(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("原生刷题", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${detail.paper.title.take(28)} · 共 ${flat.size} 题",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (onSwitchToH5 != null) {
                        TextButton(onClick = onSwitchToH5) { Text("官方 H5") }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { if (index > 0) index-- },
                        enabled = index > 0,
                    ) { Text("上一题") }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "${safeIndex + 1} / ${flat.size}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { if (index < flat.lastIndex) index++ },
                        enabled = index < flat.lastIndex,
                    ) { Text("下一题") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    LinearProgressIndicator(
                        progress = { (safeIndex + 1f) / flat.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (banner != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${safeIndex + 1}.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = qtype.ifBlank { q.type.ifBlank { "题目" } },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (q.isMultiple) {
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = "多选",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = q.stem.ifBlank { "（题干为空）" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            // 选项（填空题没有 options，就直接看答案）
            if (q.optionItems.isNotEmpty()) {
                items(q.optionItems.size) { oi ->
                    val opt = q.optionItems[oi]
                    val chosen = myPicks.contains(opt.label)
                    val correct = isAnswerCorrect(q, opt.label)
                    val container = when {
                        !isRevealed && chosen -> MaterialTheme.colorScheme.primaryContainer
                        !isRevealed -> MaterialTheme.colorScheme.surfaceContainer
                        chosen && correct -> MaterialTheme.colorScheme.primaryContainer
                        chosen && !correct -> MaterialTheme.colorScheme.errorContainer
                        correct -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRevealed) {
                                val cur = picks[q.id].orEmpty()
                                picks[q.id] = when {
                                    q.isMultiple && cur.contains(opt.label) -> cur - opt.label
                                    q.isMultiple -> cur + opt.label
                                    cur.contains(opt.label) -> emptyList()
                                    else -> listOf(opt.label)
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = container),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(text = opt.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val canCheck = q.optionItems.isEmpty() || myPicks.isNotEmpty()
                    OutlinedButton(
                        onClick = { if (!revealed.contains(q.id)) revealed.add(q.id) },
                        enabled = canCheck && !isRevealed,
                    ) { Text(if (isRevealed) "已对答案" else "对答案") }
                    if (myPicks.isNotEmpty() && !isRevealed) {
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "我的答案：${myPicks.sorted().joinToString("")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isRevealed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "答案：${q.answer.ifBlank { "（服务端没给答案）" }}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (q.optionItems.isNotEmpty() && myPicks.isNotEmpty()) {
                                    Spacer(Modifier.size(8.dp))
                                    val ok = isCorrect(q, myPicks)
                                    Text(
                                        text = if (ok) "✓ 答对" else "✗ 答错",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ok) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (q.explanation.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = q.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { if (index < flat.lastIndex) index++ }) {
                        Text(if (index < flat.lastIndex) "下一题 →" else "已是最后一题")
                    }
                }
            }

            item {
                Text(
                    text = "原生刷题只做题目练习与对答案（答案来自试卷接口）。作答不会计入服务端记录，" +
                        "要留记录请用「官方 H5」；官方 H5 没给题面的卷子就只能在本地练。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** 答案文本里可能带 A/B/C 等多字母（多选），拆成单字母集合比较 */
private fun answerLabels(answer: String): Set<String> =
    Regex("[A-Ha-h]").findAll(answer.replace(" ", "")).map { it.value.uppercase() }.toSet()

private fun isCorrect(q: PaperQuestion, picks: List<String>): Boolean {
    val right = answerLabels(q.answer)
    return right.isNotEmpty() && picks.map { it.uppercase() }.toSet() == right
}

private fun isAnswerCorrect(q: PaperQuestion, label: String): Boolean =
    answerLabels(q.answer).contains(label.uppercase())
