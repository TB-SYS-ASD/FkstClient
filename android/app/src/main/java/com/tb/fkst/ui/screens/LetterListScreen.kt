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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tb.fkst.data.Buddy
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.Routes
import com.tb.fkst.ui.components.EmptyBox
import com.tb.fkst.ui.components.ErrorBox
import com.tb.fkst.ui.components.FkstAvatar
import com.tb.fkst.ui.components.LoadingBox

/**
 * 私信会话列表。
 *
 * 平台没有"会话列表"接口，会话对象就是互关好友（GetSTBuddies type=1），
 * 点进去用 GetSTLetterMessage 拉与 TA 的消息。
 *
 * 置顶是纯本地功能（平台没有对应接口）；备注优先写服务端 SetSTFollowMark，
 * 同时本地留一份，换设备/未关注的情况下也能显示。
 *
 * @param embedded 作为底部导航的 tab 使用时置 true（不显示返回箭头）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterListScreen(
    vm: AppViewModel,
    nav: NavHostController,
    embedded: Boolean = false,
) {
    LaunchedEffect(Unit) {
        vm.loadBuddies()
        vm.refreshCoin()
    }

    var remarkTarget by remember { mutableStateOf<Buddy?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("私信") },
                navigationIcon = {
                    if (!embedded) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadBuddies() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            vm.coinState?.let {
                SignBanner(
                    days = it.signDays,
                    canSend = it.canSendLetter,
                    onCheckIn = { vm.doCheckIn() },
                    checkingIn = vm.checkingIn,
                    signedToday = it.signedToday,
                )
            }

            val err = vm.buddiesError
            val list = vm.sortedBuddies
            when {
                vm.buddiesLoading && vm.buddies.isEmpty() -> LoadingBox()
                err != null && vm.buddies.isEmpty() ->
                    ErrorBox(err, onRetry = { vm.loadBuddies() })
                list.isEmpty() ->
                    EmptyBox("还没有互相关注的好友，互关后才能私信")
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.homeId }) { b ->
                        BuddyRow(
                            buddy = b,
                            name = vm.nameOf(b),
                            pinned = vm.isPinned(b.homeId),
                            remark = vm.remarkOf(b.homeId),
                            onOpen = {
                                vm.openLetter(b.homeId, b)
                                nav.navigate(Routes.LETTER_CHAT)
                            },
                            onTogglePin = { vm.togglePin(b.homeId) },
                            onEditRemark = { remarkTarget = b },
                        )
                    }
                }
            }
        }
    }

    remarkTarget?.let { target ->
        RemarkDialog(
            title = target.nickName.ifBlank { target.homeId },
            initial = vm.remarkOf(target.homeId),
            onDismiss = { remarkTarget = null },
            onConfirm = { text ->
                vm.setRemark(target.homeId, text, target.relationId)
                remarkTarget = null
            },
        )
    }
}

@Composable
private fun BuddyRow(
    buddy: Buddy,
    name: String,
    pinned: Boolean,
    remark: String,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onEditRemark: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pinned) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FkstAvatar(buddy.avatar, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "已置顶",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (remark.isNotBlank()) append("备注：").append(remark).append(" · ")
                        append("粉丝 ").append(buddy.fans)
                        if (buddy.followed) append(" · 互关")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "取消置顶" else "置顶会话") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                        onClick = {
                            menu = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (remark.isBlank()) "设置备注" else "修改备注") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            menu = false
                            onEditRemark()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("发私信") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                        onClick = {
                            menu = false
                            onOpen()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemarkDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注") },
        text = {
            Column {
                Text(
                    text = "给「$title」起个你记得住的名字，留空就是清除备注。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("备注名") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 顶部签到状态条：提示距离解锁私信还差几天，未解锁时给一个签到按钮 */
@Composable
fun SignBanner(
    days: Int,
    canSend: Boolean,
    onCheckIn: (() -> Unit)? = null,
    checkingIn: Boolean = false,
    signedToday: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canSend) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.MarkChatUnread,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (canSend) "私信已解锁" else "私信还没解锁",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (canSend) "可以正常收发私信了"
                    else "连续签到 $days/3 天即可解锁，每日签到一次",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!canSend && onCheckIn != null) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCheckIn, enabled = !checkingIn && !signedToday) {
                    Text(if (signedToday) "已签到" else "签到")
                }
            }
        }
    }
}
