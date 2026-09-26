package com.tb.fkst.ui.screens

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.tb.fkst.core.H5Sign
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.EmptyBox

/**
 * H5 实时答题：把官方 paperExercises-v18 页面（带签名的会话参数）丢进 WebView。
 *
 * 这是绕过「第三方 native 交卷被挡」的唯一正路 —— 官方 H5 接受我们的合法会话，
 * 真能答题、交卷。会话身份全在带 HMAC 签名的 query 参数里，不需要同步 cookie。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperExerciseScreen(vm: AppViewModel, nav: NavHostController) {
    val paper = vm.currentPaper
    // 登录态变化时重建 URL（登录回来后 loginToken 可能已补上）
    val loginTokenState = remember { mutableStateOf(vm.client.dynamicParams["loginToken"] ?: "") }
    val url = remember(paper, loginTokenState.value) {
        paper?.let {
            H5Sign.buildPaperExerciseUrl(
                loginToken = vm.client.dynamicParams["loginToken"] ?: "",
                mid = vm.client.mid,
                tokenSeed = vm.client.dynamicParams["tokenSeed"] ?: "",
                unionid = vm.client.dynamicParams["unionid"] ?: "",
                pid = it.id,
                type = it.type,
            )
        }
    }
    // 每次进入前台/重组时刷新一次凭据状态，登录回来能自动重试
    androidx.compose.runtime.LaunchedEffect(Unit) {
        loginTokenState.value = vm.client.dynamicParams["loginToken"] ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(paper?.title?.ifBlank { "实时练习" } ?: "实时练习") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webRef?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when {
            url == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val loggedIn = vm.client.mid.isNotBlank() ||
                    vm.client.dynamicParams["unionid"]?.isNotBlank() == true
                EmptyBox(
                    when {
                        paper == null -> "请先从试卷详情进入"
                        // 旧版本（≤v1.10.0）或 v1.11.1 之前登录的会话没有 loginToken
                        loggedIn -> "当前会话缺少实时练习凭据，请退出后重新登录"
                        else -> "需要先登录才能进入实时练习"
                    },
                )
                val yexError = vm.client.dynamicParams["yexError"]
                if (loggedIn && !yexError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "上次登录凭据获取失败：$yexError",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                if (paper != null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.navigate(com.tb.fkst.ui.Routes.LOGIN) }) {
                        Text(if (loggedIn) "去重新登录" else "去登录")
                    }
                }
            }
            else -> ExerciseWebView(
                url = url,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

private var webRef: WebView? = null

private const val H5_HOME = "https://www.yaerxing.com/"

/**
 * 两段式 cookie bootstrap（桌面端 window.py 2026-08-11 实测同款坑，勿直载）：
 * paperExercises 页面内 ajax 拉题干需要 yex_session cookie——直载答题页只种
 * XSRF-TOKEN，题干全空（只剩题号骨架）。先静默访问 H5 首页种会话 cookie，
 * 首页加载完成后再载目标答题页。
 */
private class BootstrapClient(
    private val onLoadingStart: () -> Unit,
    private val onLoadingDone: () -> Unit,
) : WebViewClient() {
    var pendingTarget: String? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onLoadingStart()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        val target = pendingTarget
        if (view != null && target != null) {
            pendingTarget = null
            view.loadUrl(target)
            return // 目标页加载中，保持 loading
        }
        onLoadingDone()
    }
}

@Composable
private fun ExerciseWebView(url: String, modifier: Modifier = Modifier) {
    var loading by remember { mutableStateOf(true) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0 Mobile wv"
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    val client = BootstrapClient(
                        onLoadingStart = { loading = true },
                        onLoadingDone = { loading = false },
                    )
                    webViewClient = client
                    webRef = this
                    tag = url
                    if (url.contains("paperExercises")) {
                        client.pendingTarget = url
                        loadUrl(H5_HOME)
                    } else {
                        loadUrl(url)
                    }
                }
            },
            update = { wv ->
                // URL 变化（换卷）时重新走 bootstrap；bootstrap 首页阶段不干预
                if (wv.tag != url) {
                    wv.tag = url
                    val client = wv.webViewClient as? BootstrapClient
                    if (url.contains("paperExercises")) {
                        client?.pendingTarget = url
                        wv.loadUrl(H5_HOME)
                    } else {
                        client?.pendingTarget = null
                        wv.loadUrl(url)
                    }
                }
            },
        )
        if (loading) {
            LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
        }
    }
}
