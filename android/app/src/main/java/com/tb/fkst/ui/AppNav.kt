package com.tb.fkst.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tb.fkst.core.Constants
import com.tb.fkst.ui.screens.DiscoverScreen
import com.tb.fkst.ui.screens.ImageViewerScreen
import com.tb.fkst.ui.screens.LetterChatScreen
import com.tb.fkst.ui.screens.LetterListScreen
import com.tb.fkst.ui.screens.LoginScreen
import com.tb.fkst.ui.screens.MeScreen
import com.tb.fkst.ui.screens.MyHomeScreen
import com.tb.fkst.ui.screens.NoteDetailScreen
import com.tb.fkst.ui.screens.NoteListScreen
import com.tb.fkst.ui.screens.NoticesScreen
import com.tb.fkst.ui.screens.OnboardingScreen
import com.tb.fkst.ui.screens.PublishNoteScreen
import com.tb.fkst.ui.screens.SearchScreen
import com.tb.fkst.ui.screens.SettingsScreen
import com.tb.fkst.ui.screens.SocialScreen
import com.tb.fkst.ui.screens.UserScreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val ONBOARDING = "onboarding"
    const val NOTE = "note"
    const val MY_NOTES = "my_notes"
    const val MY_HOME = "my_home"
    const val LIKED = "liked"
    const val COLLECTION = "collection"
    const val SOCIAL = "social"
    const val NOTICES = "notices"
    const val USER = "user"
    const val SETTINGS = "settings"
    const val LETTERS = "letters"
    const val LETTER_CHAT = "letter_chat"
    const val VIEWER = "viewer"
    const val PUBLISH = "publish"
}

@Composable
fun AppNavHost(vm: AppViewModel) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.boot() }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }

    // 有新版本且没被跳过 → 弹更新提示（启动静默检查 / 设置页手动检查共用这一个）
    UpdateDialog(vm) { url ->
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                )
            )
        }
    }

    var startDest by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm.booted) {
        if (vm.booted && startDest == null) {
            startDest = when {
                // 第一次打开：政策没同意过 → 引导（政策 + 刷题说明，只出现这一次）
                !vm.policyAgreed -> Routes.ONBOARDING
                vm.loggedIn -> Routes.MAIN
                else -> Routes.LOGIN
            }
        }
    }

    val start = startDest
    if (start == null) {
        SplashScreen()
        return
    }

    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.LOGIN) {
            LaunchedEffect(vm.loggedIn) {
                if (vm.loggedIn) {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            }
            LoginScreen(vm)
        }

        composable(Routes.MAIN) { MainScreen(vm, nav) }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(vm) {
                nav.navigate(if (vm.loggedIn) Routes.MAIN else Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            }
        }

        composable(Routes.NOTE) { NoteDetailScreen(vm, nav) }

        composable(Routes.MY_NOTES) {
            NoteListScreen(
                title = "我的笔记",
                state = vm.myNotesFeed,
                vm = vm,
                nav = nav,
                onLoad = { reset -> vm.loadMyNotes(reset) },
            )
        }

        composable(Routes.LIKED) {
            NoteListScreen(
                title = "我赞过的",
                state = vm.likedFeed,
                vm = vm,
                nav = nav,
                onLoad = { reset -> vm.loadLiked(reset) },
            )
        }

        composable(Routes.COLLECTION) {
            NoteListScreen(
                title = "我的收藏",
                state = vm.collectionFeed,
                vm = vm,
                nav = nav,
                onLoad = { reset -> vm.loadCollection(reset) },
            )
        }

        composable(Routes.SOCIAL) { SocialScreen(vm, nav) }

        composable(Routes.NOTICES) { NoticesScreen(vm, nav) }

        composable(Routes.USER) { UserScreen(vm, nav) }

        composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }

        composable(Routes.LETTERS) { LetterListScreen(vm, nav) }

        composable(Routes.LETTER_CHAT) { LetterChatScreen(vm, nav) }

        composable(Routes.MY_HOME) { MyHomeScreen(vm, nav) }

        composable(Routes.VIEWER) { ImageViewerScreen(vm, nav) }

        composable(Routes.PUBLISH) { PublishNoteScreen(vm, nav) }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "疯狂刷题",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

/**
 * 底部导航四项：发现 / 搜索 / 私信 / 我的。
 * 「关注」流已经并进发现页的二级切换里，不再单独占一个 tab。
 */
private val TABS = listOf(
    Tab("发现", Icons.Filled.Explore),
    Tab("搜索", Icons.Filled.Search),
    Tab("私信", Icons.Filled.Forum),
    Tab("我的", Icons.Filled.Person),
)

@Composable
fun MainScreen(vm: AppViewModel, nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                0 -> DiscoverScreen(vm, nav)
                1 -> SearchScreen(vm, nav)
                2 -> LetterListScreen(vm, nav, embedded = true)
                else -> MeScreen(vm, nav)
            }
        }
    }
}

// ------------------------------------------------------------------ 更新弹窗

/**
 * 发现新版本时的提示弹窗。
 *
 * - 「去下载」：优先用 Release 附带的 .apk 资产直链，没有就打开发布页
 * - 「跳过此版本」：记住 tag，这个版本不再提示（下个版本照常提示）
 * - 「稍后」：只关掉这一次的弹窗
 */
@Composable
fun UpdateDialog(vm: AppViewModel, openUrl: (String) -> Unit) {
    val info = vm.updateInfo ?: return

    AlertDialog(
        onDismissRequest = { vm.dismissUpdate() },
        title = { Text("发现新版本 ${info.latestTag}") },
        text = {
            Column {
                Text(
                    text = info.latestName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = info.notes.take(600),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.dismissUpdate()
                openUrl(info.apkUrl.ifBlank { info.pageUrl })
            }) { Text("去下载") }
        },
        dismissButton = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.skipUpdate() }) { Text("跳过此版本") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.dismissUpdate() }) { Text("稍后") }
            }
        },
    )
}
