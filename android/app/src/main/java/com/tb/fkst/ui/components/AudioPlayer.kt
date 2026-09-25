package com.tb.fkst.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 当前正在播放的音频地址。全局只有一个，保证同时只响一条。
 */
private var currentAudioUrl by mutableStateOf<String?>(null)

/**
 * 笔记里的音频播放条。
 *
 * 正文中的 `[音频] <url>` 由 `NoteDetailScreen` 解析出来后交给它渲染。
 * 用系统自带的 [MediaPlayer]，不额外引依赖。
 *
 * 说明：笔记正文并没有音频字段（服务端不提供），这是本客户端自己的扩展，
 * 所以只在我们的客户端里会渲染成播放器，官方端看到的是一行普通文字。
 */
@Composable
fun AudioPlayerBar(
    url: String,
    title: String = "音频",
    modifier: Modifier = Modifier,
) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    // 别人开始播了，先把手里的停掉
    LaunchedEffect(currentAudioUrl) {
        if (currentAudioUrl != url && playing) {
            runCatching { player?.pause() }
            playing = false
        }
    }

    // 播放时轮询进度
    LaunchedEffect(playing, player) {
        while (playing) {
            val p = player
            if (p != null) {
                runCatching {
                    positionMs = p.currentPosition
                    val d = p.duration
                    if (d > 0) durationMs = d
                }
            }
            delay(250)
        }
    }

    DisposableEffect(url) {
        onDispose {
            if (currentAudioUrl == url) currentAudioUrl = null
            runCatching { player?.release() }
            player = null
        }
    }

    fun toggle() {
        if (failed) return
        val p = player
        if (p == null) {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setOnPreparedListener { m ->
                durationMs = runCatching { m.duration }.getOrDefault(0)
                runCatching { m.start() }
                playing = true
                currentAudioUrl = url
            }
            mp.setOnCompletionListener {
                playing = false
                positionMs = 0
                runCatching { it.seekTo(0) }
            }
            mp.setOnErrorListener { _, _, _ ->
                failed = true
                playing = false
                true
            }
            val ok = runCatching {
                mp.setDataSource(url)
                mp.prepareAsync()
            }.isSuccess
            if (!ok) {
                failed = true
                runCatching { mp.release() }
                return
            }
            player = mp
        } else if (playing) {
            runCatching { p.pause() }
            playing = false
        } else {
            runCatching { p.start() }
            playing = true
            currentAudioUrl = url
        }
    }

    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = { toggle() }) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (failed) "音频加载失败" else title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${fmt(positionMs)} / ${fmt(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

private fun fmt(ms: Int): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}
