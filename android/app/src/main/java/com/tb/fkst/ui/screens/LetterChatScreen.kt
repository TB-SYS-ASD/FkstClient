package com.tb.fkst.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.tb.fkst.core.Constants
import com.tb.fkst.core.ImageUrls
import com.tb.fkst.data.Letter
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LoadingBox
import com.tb.fkst.ui.components.timeAgo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单张图最大 10MB，再大服务端也不收 */
private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

/** 单个附件最大 20MB */
private const val MAX_FILE_BYTES = 20 * 1024 * 1024

/** 与某个好友的私信对话（支持发图片） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterChatScreen(vm: AppViewModel, nav: NavHostController) {
    val partner = vm.letterPartner
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }

    val canSend = vm.coinState?.canSendLetter ?: false
    val signDays = vm.coinState?.signDays ?: 0

    val title = partner?.let { vm.nameOf(it) } ?: "私信"
    // 会话详情里的 member 有时只有 id，这里统一兜一层，保证标题和备注都拿得到 mid
    val peerMid = partner?.homeId?.ifBlank { vm.letterTargetMid } ?: vm.letterTargetMid

    // 新消息进来时滚到底部；用户如果正在往上翻历史，就不打断他
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .collect { canScroll ->
                // 能往下滚 = 不在底部；回到底部就恢复跟随
                stickToBottom = !canScroll
            }
    }
    LaunchedEffect(vm.letters.lastOrNull()?.stableKey) {
        if (vm.letters.isNotEmpty() && stickToBottom) {
            listState.animateScrollToItem(vm.letters.lastIndex)
        }
    }

    // 每 10 秒静默拉一次新消息：不亮 loading、失败也不提示，只有内容变了才刷界面
    LaunchedEffect(vm.letterTargetMid) {
        if (vm.letterTargetMid.isBlank()) return@LaunchedEffect
        while (true) {
            delay(Constants.LETTER_REFRESH_MS)
            vm.refreshLettersSilently()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    Triple(bytes, nameFor(mime), mime)
                }.getOrNull()
            }
            when {
                payload == null -> vm.toast = "读取图片失败，换一张试试"
                payload.first.size > MAX_IMAGE_BYTES -> vm.toast = "图片太大了（上限 10MB）"
                else -> vm.sendLetterImage(payload.first, payload.second, payload.third)
            }
        }
    }

    // 选任意文件（PDF / Word / txt …）发送
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val name = queryDisplayName(context, uri)
                        ?: "file_${System.currentTimeMillis()}"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    Triple(bytes, name, mime)
                }.getOrNull()
            }
            when {
                payload == null -> vm.toast = "读取文件失败，换一个试试"
                payload.first.size > MAX_FILE_BYTES -> vm.toast = "文件太大了（上限 20MB）"
                else -> vm.sendLetterFile(payload.first, payload.second, payload.third)
            }
        }
    }

    var attachMenu by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title.ifBlank { "私信" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (peerMid.isNotBlank()) {
                            val remark = vm.remarkOf(peerMid)
                            val nick = partner?.nickName.orEmpty()
                            Text(
                                text = if (remark.isNotBlank() && remark != nick)
                                    "备注 · 原名 ${nick.ifBlank { "未命名" }}"
                                else "mid $peerMid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("重新加载") },
                            onClick = {
                                menuOpen = false
                                vm.loadLetters()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.antiRecall) "关闭防撤回" else "开启防撤回") },
                            leadingIcon = {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                vm.applyAntiRecall(!vm.antiRecall)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("查看原始报文") },
                            onClick = {
                                menuOpen = false
                                showRaw = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                HorizontalDivider()
                if (!canSend) {
                    Text(
                        text = "连续签到 $signDays/3 天才能发私信，先去「我的」签到吧",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (vm.uploadingImage || vm.uploadingFile) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (vm.uploadingFile) "文件上传中…" else "图片上传中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box {
                        IconButton(
                            enabled = canSend && !vm.sendingLetter &&
                                !vm.uploadingImage && !vm.uploadingFile,
                            onClick = { attachMenu = true },
                        ) {
                            Icon(
                                Icons.Filled.AddCircleOutline,
                                contentDescription = "发送图片或文件",
                            )
                        }
                        DropdownMenu(
                            expanded = attachMenu,
                            onDismissRequest = { attachMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("发送图片") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Image, contentDescription = null)
                                },
                                onClick = {
                                    attachMenu = false
                                    pickImage.launch("image/*")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("发送文件") },
                                leadingIcon = {
                                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                                },
                                onClick = {
                                    attachMenu = false
                                    pickFile.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("说点什么…") },
                        enabled = canSend && !vm.sendingLetter,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        enabled = canSend && !vm.sendingLetter && input.isNotBlank(),
                        onClick = {
                            val text = input
                            vm.sendLetter(text) { ok -> if (ok) input = "" }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val err = vm.lettersError
            when {
                vm.lettersLoading && vm.letters.isEmpty() -> LoadingBox()
                err != null && vm.letters.isEmpty() ->
                    ErrorBox(err, onRetry = { vm.loadLetters() })
                vm.letters.isEmpty() ->
                    EmptyBox("还没有聊天记录，打个招呼吧")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.letters, key = { it.stableKey }) { l ->
                        LetterBubble(
                            letter = l,
                            mine = l.mine,
                            avatar = partner?.avatar ?: "",
                            onOpenImage = { url ->
                                vm.openViewer(
                                    images = listOf(url),
                                    index = 0,
                                    title = "私信图片 · ${title.ifBlank { vm.letterTargetMid }}",
                                )
                                nav.navigate(Routes.VIEWER)
                            },
                            onOpenFile = { url -> openUrl(context, url) },
                        )
                    }
                }
            }
        }
    }

    if (showRaw) {
        RawPayloadDialog(
            raw = vm.lettersRaw,
            onDismiss = { showRaw = false },
        )
    }
}

/** 原始报文弹窗：私信字段对不上时，把服务端原文复制出来一眼就能看出问题 */
@Composable
private fun RawPayloadDialog(raw: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务端原始返回") },
        text = {
            if (raw.isBlank()) {
                Text("还没有拉到数据", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = raw,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(raw))
                onDismiss()
            }) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun LetterBubble(
    letter: Letter,
    mine: Boolean,
    avatar: String,
    onOpenImage: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val recalled = letter.recalled
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            FkstAvatar(avatar, 34.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .alpha(if (recalled) 0.6f else 1f),
        ) {
            if (recalled) {
                // 防撤回：对方撤回了但本地留着，内容照常显示，只加个标记
                Text(
                    text = if (mine) "你撤回了一条消息 · 本地保留" else "对方撤回了一条消息 · 本地保留",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            if (letter.isFile) {
                FileBubble(letter = letter, mine = mine) { onOpenFile(letter.file) }
            } else if (letter.isImage) {
                ImageBubble(url = letter.image) { onOpenImage(letter.image) }
            } else if (ImageUrls.isRemote(letter.content)) {
                // 文件消息被服务端降级成文本时，内容就是一条链接 —— 单独渲染成附件卡
                LinkBubble(content = letter.content) { onOpenFile(letter.content) }
            } else {
                Card(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = letter.content.ifBlank { "（空消息）" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
            if (letter.createdAt > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeAgo(letter.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (mine) TextAlign.End else TextAlign.Start,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        if (mine) Spacer(Modifier.width(8.dp))
    }
}

/**
 * 图片气泡。
 *
 * 之前只设了 `height(200.dp)`，宽度会跟着图片的固有尺寸走：竖图会被挤成
 * 又细又长的一条，横图又会撑满整行。这里改成「按图片宽高比 + 限制最大宽度」，
 * 没加载出来之前先按 3:4 占位，避免气泡在加载完成的瞬间跳一下。
 */
@Composable
private fun ImageBubble(url: String, onClick: () -> Unit) {
    val painter = rememberAsyncImagePainter(url)
    val size = painter.intrinsicSize
    val rawRatio = if (size.isSpecified && size.height > 0f) size.width / size.height else 0.75f
    // 下限保证竖图不会太高（220/320），上限避免超宽全景图变成一条
    val ratio = rawRatio.coerceIn(220f / 320f, 3f)

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .widthIn(max = 220.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    )
}

/** 附件气泡：文件图标 + 文件名 + 扩展名，点一下用浏览器/系统应用打开 */
@Composable
private fun FileBubble(letter: Letter, mine: Boolean, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = letter.fileName.ifBlank { "附件" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = (if (letter.fileExt.isBlank()) "文件" else letter.fileExt) + " · 点击打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 文件消息被降级成纯文本链接时的展示 */
@Composable
private fun LinkBubble(content: String, onOpen: () -> Unit) {
    val firstLine = content.lineSequence().firstOrNull()?.trim().orEmpty()
    val label = firstLine.removePrefix("[文件]").trim().ifBlank { "附件链接" }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "点击打开链接",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 用系统应用打开一个 http(s) 链接 */
private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "没有能打开这个链接的应用", Toast.LENGTH_SHORT).show()
    }
}

/** 从 content:// 里取原始文件名 */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

private fun nameFor(mime: String): String = when {
    mime.contains("png", true) -> "letter_${System.currentTimeMillis()}.png"
    mime.contains("gif", true) -> "letter_${System.currentTimeMillis()}.gif"
    mime.contains("webp", true) -> "letter_${System.currentTimeMillis()}.webp"
    else -> "letter_${System.currentTimeMillis()}.jpg"
}
