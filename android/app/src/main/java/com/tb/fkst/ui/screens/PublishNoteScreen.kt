package com.tb.fkst.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tb.fkst.core.Constants
import com.tb.fkst.core.ImageUrls
import com.tb.fkst.data.PendingAudio
import com.tb.fkst.data.PendingImage
import com.tb.fkst.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单张配图上限 10MB（与服务端一致） */
private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

/** 最多九张，跟官方客户端一致 */
private const val MAX_IMAGES = 9

/**
 * 发布笔记。
 *
 * 接口是 `UploadNote2`（comment 签名变体）：先逐张把图传到 `stupnote`，
 * 再把 `urls` + `title` + `content` + `type` 一并发出去。
 *
 * 实测的坑：
 * - 服务端限制「两贴发布间隔至少 5 分钟」，发太频会回 res=2
 * - 标题不能为空；正文与分区是可选的
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishNoteScreen(vm: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    // 预览图：uri + 已经读好的字节
    data class Shot(val uri: Uri, val image: PendingImage)

    val shots = remember { mutableStateListOf<Shot>() }

    // 音频：uri + 已经读好的字节（服务端只认 mp3）
    data class Clip(val uri: Uri, val audio: PendingAudio)

    val clips = remember { mutableStateListOf<Clip>() }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (clips.size >= Constants.AUDIO_MAX_COUNT) {
                vm.toast = "最多只能放 ${Constants.AUDIO_MAX_COUNT} 段音频"
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                    val name = displayName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    Triple(name, bytes, mime)
                }.getOrNull()
            }
            if (loaded == null) {
                vm.toast = "读取音频失败"
                return@launch
            }
            val (name, bytes, mime) = loaded
            when {
                !name.lowercase().endsWith(".mp3") ->
                    vm.toast = "服务端只接受 mp3，请先转成 mp3 再选"
                bytes.size > Constants.AUDIO_MAX_BYTES ->
                    vm.toast = "音频超过 ${Constants.AUDIO_MAX_BYTES / 1024 / 1024}MB，太大了"
                else -> clips.add(Clip(uri, PendingAudio(bytes, name, mime)))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val room = MAX_IMAGES - shots.size
            if (room <= 0) {
                vm.toast = "最多只能放 $MAX_IMAGES 张图"
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                uris.take(room).mapNotNull { uri ->
                    runCatching {
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@runCatching null
                        Triple(uri, bytes, mime)
                    }.getOrNull()
                }
            }
            var tooBig = 0
            loaded.forEach { (uri, bytes, mime) ->
                if (bytes.size > MAX_IMAGE_BYTES) {
                    tooBig++
                } else {
                    shots.add(Shot(uri, PendingImage(bytes, nameFor(mime), mime)))
                }
            }
            when {
                tooBig > 0 -> vm.toast = "有 $tooBig 张图超过 10MB，已跳过"
                uris.size > room -> vm.toast = "最多 $MAX_IMAGES 张，多余的没收"
            }
        }
    }

    fun submit() {
        if (vm.publishing) return
        if (title.isBlank()) {
            vm.toast = "标题不能为空"
            return
        }
        vm.publishNote(title, text, shots.map { it.image }, clips.map { it.audio }) { ok ->
            if (ok) nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发布笔记") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { submit() }, enabled = !vm.publishing) {
                        if (vm.publishing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发布")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 12.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        placeholder = { Text("给这篇起个名字（必填）") },
                        singleLine = true,
                        enabled = !vm.publishing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("正文") },
                        placeholder = { Text("写点什么…（可选）") },
                        minLines = 5,
                        maxLines = 14,
                        enabled = !vm.publishing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 配图 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "配图 ${shots.size}/$MAX_IMAGES",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { picker.launch("image/*") },
                            enabled = !vm.publishing && shots.size < MAX_IMAGES,
                        ) {
                            Icon(
                                Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("选图片")
                        }
                    }

                    if (shots.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "第一张会作为封面。可以一次选多张。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(12.dp))
                        // 三列缩略图，右上角一个「×」删掉
                        shots.chunked(3).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEachIndexed { _, shot ->
                                    Box(Modifier.weight(1f)) {
                                        AsyncImage(
                                            model = shot.uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(92.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                ),
                                        )
                                        IconButton(
                                            onClick = { shots.remove(shot) },
                                            enabled = !vm.publishing,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(26.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "移除",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                // 补齐空位，保证每行三格宽度一致
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Text(
                            text = "已选 ${shots.size} 张，合计 ${
                                ImageUrls.readableSize(shots.sumOf { it.image.size.toLong() })
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 音频 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "音频 ${clips.size}/${Constants.AUDIO_MAX_COUNT}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { audioPicker.launch("audio/mpeg") },
                            enabled = !vm.publishing && clips.size < Constants.AUDIO_MAX_COUNT,
                        ) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("选音频")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (clips.isEmpty()) {
                            "服务端只接受 mp3。音频会以「${Constants.AUDIO_MARK} 地址」的形式附在正文末尾，" +
                                "在本客户端里渲染成播放器，官方客户端会显示成一行文字。"
                        } else {
                            "共 ${ImageUrls.readableSize(clips.sumOf { it.audio.size.toLong() })}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    clips.forEach { clip ->
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = clip.audio.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { clips.remove(clip) },
                                enabled = !vm.publishing,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 分区 ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "分区",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Constants.CATEGORIES.chunked(3).forEach { row ->
                        Row(
                            Modifier.padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { cat ->
                                FilterChip(
                                    selected = vm.publishCategory.key == cat.key,
                                    onClick = {
                                        vm.selectPublishCategory(Constants.CATEGORIES.indexOf(cat))
                                    },
                                    enabled = !vm.publishing,
                                    label = { Text(cat.label) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "平台限制两篇之间至少间隔 5 分钟；配图会先后传到平台的笔记图库，"
                            + "第一张作为封面。发布成功后可以在「我的主页 → 发布」里看到。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { submit() },
                enabled = !vm.publishing && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (vm.publishing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("发布中…")
                } else {
                    Text("发布", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun nameFor(mime: String): String = when {
    mime.contains("png", true) -> "note_${System.currentTimeMillis()}.png"
    mime.contains("gif", true) -> "note_${System.currentTimeMillis()}.gif"
    mime.contains("webp", true) -> "note_${System.currentTimeMillis()}.webp"
    mime.contains("heic", true) -> "note_${System.currentTimeMillis()}.heic"
    else -> "note_${System.currentTimeMillis()}.jpg"
}

/**
 * 取 content:// 的真实文件名。
 *
 * 音频上传**服务端只看扩展名**（实测：内容是 wav、文件名写成 .mp3 照样收），
 * 所以这里必须拿到原始文件名才能判断用户选的到底是不是 mp3。
 */
private fun displayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                val name = c.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "voice.mp3"
}
