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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tb.fkst.core.Constants
import com.tb.fkst.data.Api
import com.tb.fkst.data.Comment
import com.tb.fkst.data.NoteDetail
import com.tb.fkst.data.Reply
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.AudioPlayerBar
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LoadingBox
import com.tb.fkst.ui.components.countText
import com.tb.fkst.ui.components.timeAgo
import com.tb.fkst.ui.friendlyError
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 单条评论配图上限 10MB（与服务端一致） */
private const val MAX_COMMENT_IMAGE_BYTES = 10 * 1024 * 1024

/** 评论草稿图：本地预览 uri + 上传后的线上地址 */
private data class DraftImage(
    val uri: Uri,
    val url: String = "",
    val uploading: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(vm: AppViewModel, nav: NavHostController) {
    val note = vm.detailNote
    if (note == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var detail by remember(note.id) { mutableStateOf<NoteDetail?>(null) }
    var loading by remember(note.id) { mutableStateOf(true) }
    var error by remember(note.id) { mutableStateOf<String?>(null) }

    var liked by remember(note.id) { mutableStateOf(note.selfLike) }
    var likeCount by remember(note.id) { mutableStateOf(note.likeCount) }

    var favorited by remember(note.id) { mutableStateOf(vm.collectedIds.contains(note.id)) }
    var favCount by remember(note.id) { mutableStateOf(note.favoriteCount) }
    var favBusy by remember(note.id) { mutableStateOf(false) }

    // 投币：本地记「我投了几枚」，总数用笔记自带的 coins
    val coinMax = vm.coinPerNoteMax
    var coinsGiven by remember(note.id) { mutableStateOf(vm.coinsGiven(note.id)) }
    var coinTotal by remember(note.id) { mutableStateOf(note.coinCount) }

    var comments by remember(note.id) { mutableStateOf<List<Comment>>(emptyList()) }
    var commentsLoading by remember(note.id) { mutableStateOf(true) }
    var commentsMore by remember(note.id) { mutableStateOf(true) }
    var commentPage by remember(note.id) { mutableStateOf(-1) }

    var input by remember(note.id) { mutableStateOf("") }
    var replyTarget by remember(note.id) { mutableStateOf<Comment?>(null) }
    var sending by remember(note.id) { mutableStateOf(false) }

    // 回复展开缓存：commentId -> replies
    val expanded = remember(note.id) { mutableStateMapOf<String, List<Reply>>() }
    val loadingReplies = remember(note.id) { mutableStateListOf<String>() }

    // 评论配图（草稿）：本地预览 uri + 上传后的线上地址
    var draftImage by remember(note.id) { mutableStateOf<DraftImage?>(null) }

    // 选图 → 先传笔记图库，拿到地址再随评论发出去（字段名 content_url，跟官方发作业评论同源）
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        draftImage = DraftImage(uri = uri, uploading = true)
        scope.launch {
            val up = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    if (bytes.size > MAX_COMMENT_IMAGE_BYTES) {
                        vm.toast = "图片超过 10MB，太大了"; return@runCatching null
                    }
                    Api.uploadImage(
                        vm.client, bytes,
                        "comment_${System.currentTimeMillis()}.jpg", mime,
                        dirName = Constants.IMAGE_DIR_NOTE, allowFallback = false,
                    )
                }.getOrNull()
            }
            when {
                up == null -> draftImage = null
                up.illegal -> { vm.toast = "图片被平台判为违规，换一张"; draftImage = null }
                up.url.isBlank() -> {
                    vm.toast = "图片上传失败：${up.error.ifBlank { "未知原因" }}"
                    draftImage = null
                }
                else -> draftImage = DraftImage(uri = uri, url = up.url)
            }
        }
    }

    fun reloadComments() {
        scope.launch {
            commentsLoading = true
            try {
                val p = Api.comments(vm.client, note.id, 0, ct = 10)
                comments = p.items
                commentPage = 0
                commentsMore = p.hasMore
            } catch (t: Throwable) {
                vm.toast = friendlyError(t)
            } finally {
                commentsLoading = false
            }
        }
    }

    LaunchedEffect(note.id) {
        loading = true
        error = null
        detail = null
        commentsLoading = true
        expanded.clear()
        vm.ensureCollected()
        // 「赞过 / 收藏」进来的文章没有作者字段，这里按 home_id 补一份
        vm.ensureAuthor(note)
        try {
            detail = Api.noteDetail(vm.client, note.id)
        } catch (t: Throwable) {
            error = friendlyError(t)
        } finally {
            loading = false
        }
        try {
            val p = Api.comments(vm.client, note.id, 0, ct = 10)
            comments = p.items
            commentPage = 0
            commentsMore = p.hasMore
        } catch (t: Throwable) {
            vm.toast = friendlyError(t)
        } finally {
            commentsLoading = false
        }
    }

    // 收藏状态以后端为准（打开详情时补一次「我的收藏」）
    LaunchedEffect(vm.collectedIds) {
        favorited = vm.collectedIds.contains(note.id)
    }

    fun sendComment() {
        val text = input.trim()
        val img = draftImage?.url
        if ((text.isEmpty() && img.isNullOrBlank()) || sending) return
        sending = true
        val parent = replyTarget?.id ?: "0"
        vm.postComment(note.id, text, parent, img ?: "") { ok ->
            sending = false
            if (ok) {
                input = ""
                replyTarget = null
                draftImage = null
                vm.toast = "已发送"
                reloadComments()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note.title.ifBlank { "文章" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 自己的笔记才给「编辑配图 / 删除」，别人的不给
                    if (vm.isMyNote(note)) {
                        var menuOpen by remember { mutableStateOf(false) }
                        var confirmDelete by remember { mutableStateOf(false) }

                        if (confirmDelete) {
                            AlertDialog(
                                onDismissRequest = { confirmDelete = false },
                                title = { Text("删除这条笔记？") },
                                text = { Text("删掉就找不回来了，配图和评论一起没。") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        confirmDelete = false
                                        vm.deleteMyNote(note.id) { ok ->
                                            if (ok) nav.popBackStack()
                                        }
                                    }) { Text("删除") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                                },
                            )
                        }

                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑配图") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Image, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    vm.beginEditNote(note)
                                    nav.navigate(com.tb.fkst.ui.Routes.EDIT_NOTE)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除笔记") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                    IconButton(onClick = {
                        val target = !liked
                        vm.likeNote(note.id, target) { ok ->
                            if (ok) {
                                liked = target
                                likeCount = (likeCount + if (target) 1 else -1).coerceAtLeast(0)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "点赞",
                            tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    replyTarget?.let { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AssistChip(
                                onClick = { replyTarget = null },
                                label = { Text("回复 @${target.nickName.ifBlank { "该用户" }}") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }

                    // 评论配图预览（上传中显示转圈，可一键移除）
                    draftImage?.let { d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                AsyncImage(
                                    model = d.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (d.uploading) {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { draftImage = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "移除图片",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (d.uploading) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "图片上传中…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // 发图按钮：选好图后先传笔记图库，再随评论发出
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !sending && draftImage == null,
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "发图片",
                                tint = if (draftImage == null) {
                                    LocalContentColor.current
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(if (replyTarget != null) "回复…" else "说点什么…")
                            },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                        )
                        IconButton(
                            onClick = { sendComment() },
                            enabled = (input.isNotBlank() || draftImage?.url?.isNotBlank() == true)
                                && !sending && draftImage?.uploading != true,
                        ) {
                            if (sending) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // 图片（点开进入全屏查看器，可缩放 + 保存原图）
            if (note.images.isNotEmpty()) {
                val originals = note.originalImages
                item {
                    AsyncImage(
                        model = note.images.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable {
                                vm.openViewer(originals, 0, note.title.ifBlank { "图片" })
                                nav.navigate(Routes.VIEWER)
                            },
                    )
                }
                if (note.images.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(note.images.size - 1) { i ->
                                AsyncImage(
                                    model = note.images[i + 1],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable {
                                            vm.openViewer(
                                                originals,
                                                i + 1,
                                                note.title.ifBlank { "图片" },
                                            )
                                            nav.navigate(Routes.VIEWER)
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // 标题 + 作者
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = note.title.ifBlank { "（无标题）" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val authorName = vm.authorNameOf(note)
                    if (authorName.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                vm.userTarget = note.homeId
                                nav.navigate(Routes.USER)
                            },
                        ) {
                            FkstAvatar(vm.authorAvatarOf(note), 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = authorName,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = timeAgo(note.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 正文
            item {
                when {
                    loading -> LoadingBox()
                    error != null -> ErrorBox(error!!, onRetry = {
                        scope.launch {
                            loading = true
                            try {
                                detail = Api.noteDetail(vm.client, note.id)
                                error = null
                            } catch (t: Throwable) {
                                error = friendlyError(t)
                            } finally {
                                loading = false
                            }
                        }
                    })

                    else -> {
                        val body = detail?.plain?.takeIf { it.isNotBlank() }
                            ?: note.plainText
                        if (body.isNotBlank()) {
                            // 正文里的 `[音频] <url>` 是本客户端的扩展（服务端没有音频字段），
                            // 拆出来单独渲染成播放器，其余文本按原样展示。
                            val lines = body.lines()
                            val audios = lines.mapNotNull {
                                Constants.AUDIO_LINE_RE.find(it.trim())?.groupValues?.get(1)
                            }
                            val textOnly = lines
                                .filter { Constants.AUDIO_LINE_RE.find(it.trim()) == null }
                                .joinToString("\n")
                                .trim()

                            if (textOnly.isNotBlank()) {
                                Text(
                                    text = textOnly,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            audios.forEachIndexed { i, u ->
                                if (i == 0 && textOnly.isNotBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                } else if (i > 0) {
                                    Spacer(Modifier.height(8.dp))
                                }
                                AudioPlayerBar(
                                    url = u,
                                    title = if (audios.size > 1) "音频 ${i + 1}" else "音频",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 文末作者卡片：明确标出这篇文章是谁写的，点一下进 TA 的主页
            item {
                val homeId = note.homeId
                val name = vm.authorNameOf(note)
                val icon = vm.authorAvatarOf(note)
                if (homeId.isNotBlank() || name.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Card(
                        onClick = {
                            if (homeId.isNotBlank()) {
                                vm.userTarget = homeId
                                nav.navigate(Routes.USER)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FkstAvatar(icon, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "作者",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = name.ifBlank {
                                        if (homeId.isNotBlank()) "用户 $homeId" else "未知作者"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (homeId.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "mid $homeId · 点击进入主页",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 点赞
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = {
                        val target = !liked
                        vm.likeNote(note.id, target) { ok ->
                            if (ok) {
                                liked = target
                                likeCount = (likeCount + if (target) 1 else -1).coerceAtLeast(0)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (liked) "已赞 ${countText(likeCount)}" else "点赞 ${countText(likeCount)}")
                    }
                    Spacer(Modifier.width(16.dp))
                    FilledTonalButton(
                        onClick = {
                            if (favBusy) return@FilledTonalButton
                            val target = !favorited
                            favBusy = true
                            vm.toggleCollection(note.id, target) { ok ->
                                favBusy = false
                                if (ok) {
                                    favorited = target
                                    favCount = (favCount + if (target) 1 else -1).coerceAtLeast(0)
                                    vm.toast = if (target) "已收藏" else "已取消收藏"
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (favorited) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (favorited) "已收藏 ${countText(favCount)}" else "收藏 ${countText(favCount)}")
                    }
                }

                // 投币：每篇文章最多 2 枚，投满后按钮置灰
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val left = (coinMax - coinsGiven).coerceAtLeast(0)
                    val busy = vm.tippingNote == note.id
                    FilledTonalButton(
                        enabled = left > 0 && !busy,
                        onClick = {
                            vm.tipNote(note.id, 1) { ok, n ->
                                if (ok) {
                                    coinsGiven += n
                                    coinTotal += n
                                }
                            }
                        },
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Paid,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (coinsGiven >= coinMax) "已投满 $coinsGiven/$coinMax"
                            else "投币 $coinsGiven/$coinMax"
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (coinsGiven >= coinMax) {
                            "本篇已收到 $coinTotal 枚硬币"
                        } else {
                            "本篇已收到 $coinTotal 枚 · 还能投 $left 枚"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Text(
                    text = "评论 ${if (comments.isNotEmpty()) comments.size.toString() else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }

            // 评论
            if (commentsLoading && comments.isEmpty()) {
                item { LoadingBox() }
            }
            if (!commentsLoading && comments.isEmpty()) {
                item {
                    Text(
                        text = "还没有评论，来抢沙发",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
            }

            items(comments.size) { i ->
                val c = comments[i]
                CommentRow(
                    comment = c,
                    extraReplies = expanded[c.id].orEmpty(),
                    loadingReplies = loadingReplies.contains(c.id),
                    onReply = { replyTarget = c },
                    onLike = {
                        scope.launch {
                            try {
                                Api.likeComment(vm.client, c.id)
                                vm.toast = "已点赞该评论"
                            } catch (t: Throwable) {
                                vm.toast = friendlyError(t)
                            }
                        }
                    },
                    onOpenUser = { homeId ->
                        vm.userTarget = homeId
                        nav.navigate(Routes.USER)
                    },
                    onImageClick = { url ->
                        vm.openViewer(listOf(url), 0, "评论图片")
                        nav.navigate(Routes.VIEWER)
                    },
                    onExpand = {
                        loadingReplies.add(c.id)
                        scope.launch {
                            try {
                                expanded[c.id] = Api.replies(vm.client, c.id)
                            } catch (t: Throwable) {
                                vm.toast = friendlyError(t)
                            } finally {
                                loadingReplies.remove(c.id)
                            }
                        }
                    },
                )
            }

            if (commentsMore && comments.isNotEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    if (commentsLoading) return@launch
                                    commentsLoading = true
                                    try {
                                        val p = Api.comments(
                                            vm.client, note.id, commentPage + 1, ct = 10
                                        )
                                        comments = comments + p.items
                                        commentPage += 1
                                        commentsMore = p.hasMore
                                    } catch (t: Throwable) {
                                        vm.toast = friendlyError(t)
                                    } finally {
                                        commentsLoading = false
                                    }
                                }
                            },
                            enabled = !commentsLoading,
                        ) {
                            Text(if (commentsLoading) "加载中…" else "加载更多评论")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    extraReplies: List<Reply>,
    loadingReplies: Boolean,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onOpenUser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onExpand: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            FkstAvatar(comment.avatar, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.nickName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenUser(comment.homeId) },
                    )
                    if (comment.province.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = comment.province,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                if (comment.display.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = comment.display,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // 评论配图（content_url）：点开进全屏查看器
                if (comment.contentUrl.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    AsyncImage(
                        model = comment.contentUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onImageClick(comment.contentUrl) },
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeAgo(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onReply() },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "赞 ${comment.zanCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.clickable { onLike() },
                    )
                }

                // 楼中楼
                val allReplies = comment.replies + extraReplies
                if (allReplies.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CardishBox {
                        allReplies.forEach { r ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = r.nickName.ifBlank { "匿名" } + "：",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = buildString {
                                        if (r.relayContent.isNotBlank()) {
                                            append("回复 ").append(r.relayContent).append("：")
                                        }
                                        append(r.display)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (r.contentUrl.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                AsyncImage(
                                    model = r.contentUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .clickable { onImageClick(r.contentUrl) },
                                )
                            }
                        }
                    }
                }

                if (comment.replyCount > allReplies.size) {
                    val remain = comment.replyCount - allReplies.size
                    TextButton(
                        onClick = onExpand,
                        enabled = !loadingReplies,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (loadingReplies) "加载中…" else "展开 $remain 条回复",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardishBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) { Column { content() } }
}
