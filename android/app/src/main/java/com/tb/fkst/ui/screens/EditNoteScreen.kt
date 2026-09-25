package com.tb.fkst.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.tb.fkst.core.Constants
import com.tb.fkst.data.Api
import com.tb.fkst.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑已发笔记的**配图**。
 *
 * 服务端只开放了 `UpdateUploadNoteUrls`（id + urls），所以这里能改的只有图片：
 * 标题和正文改不了（翻遍官方端 dex 也没有对应接口，`UpdateNoteTag` 要管理员权限）。
 *
 * 另一个关键点：`urls` 是**全量覆盖**，不是增量追加。所以界面里显示的就是最终
 * 会存进服务端的那一份 —— 想保留的旧图留着，想删的点掉，保存时整个列表一起提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(vm: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val images = vm.editingImages
    val saving = vm.editingSaving
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (vm.editingImages.size >= Constants.NOTE_IMAGE_MAX) {
            vm.toast = "最多 ${Constants.NOTE_IMAGE_MAX} 张图"
            return@rememberLauncherForActivityResult
        }
        uploading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching Result.failure(IllegalStateException("读不到图片"))
                    val up = Api.uploadImage(
                        vm.client, bytes, "edit_${System.currentTimeMillis()}.jpg", mime,
                        dirName = Constants.IMAGE_DIR_NOTE,
                        allowFallback = false,
                    )
                    when {
                        up.illegal -> Result.failure(IllegalStateException("图片被平台判为违规"))
                        up.url.isBlank() -> Result.failure(
                            IllegalStateException(up.error.ifBlank { "上传失败" })
                        )
                        else -> Result.success(up.url)
                    }
                }.getOrElse { Result.failure(it) }
            }
            uploading = false
            result.onSuccess { vm.editAddImage(it) }
                .onFailure { vm.toast = "上传失败：${it.message ?: "未知原因"}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑配图") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.cancelEditNote()
                        nav.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${images.size} / ${Constants.NOTE_IMAGE_MAX} 张",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            vm.cancelEditNote()
                            nav.popBackStack()
                        },
                        enabled = !saving,
                    ) { Text("取消") }
                    Spacer(Modifier.size(10.dp))
                    Button(
                        onClick = {
                            vm.saveNoteImages { ok ->
                                if (ok) nav.popBackStack()
                            }
                        },
                        enabled = !saving && !uploading,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text("保存")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "只能改配图",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "服务端没开放改标题和正文的接口，所以这里只动图片。" +
                            "保存会把下面这个列表整份覆盖上去，删掉的旧图不会保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(images) { index, url ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { vm.editRemoveImage(index) },
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = "配图 ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // 右上角一个叉，点一下就移除
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }

                if (images.size < Constants.NOTE_IMAGE_MAX) {
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable(enabled = !uploading) { picker.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uploading) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    } else {
                                        SmallFloatingActionButton(
                                            onClick = { picker.launch("image/*") },
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = "添加图片")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
