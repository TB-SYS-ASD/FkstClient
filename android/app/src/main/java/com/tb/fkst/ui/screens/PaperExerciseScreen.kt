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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.tb.fkst.core.H5Sign
import com.tb.fkst.ui.AppViewModel
import com.tb.fkst.ui.components.EmptyBox

/**
 * H5 实时答题：把官方 paperExercises-v18 页面（带签名的会话参数）丢进 WebView。
 *
 * ⚠️ 官方页面只对**部分**试卷提供完整题面。实测（2026-09-26）：「答案暂缺」那类卷子
 * 服务端会返回「降级版」页面 —— 只有题目 ID、不带 shuati-fun 渲染引擎，题干要宿主
 * 通过 `getCacheData()` 注入（那张仁爱版 Unit8 卷 54KB，正常卷 32 万+ 字节）。
 * 这种卷进 H5 必然是空壳，所以加载完成后探一次；没题面就自动切到原生刷题。
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

    // H5 页面探测结果：官方没给题面 → 走原生刷题
    var h5NoContent by remember(paper?.id) { mutableStateOf(false) }
    // 用户手动切回官方 H5
    var forceH5 by remember(paper?.id) { mutableStateOf(false) }
    val detail = vm.paperDetail
    val quizMode = h5NoContent && !forceH5 && detail != null

    // 要走原生刷题时，确保题目数据在手（和「试卷详情」同一份接口）
    LaunchedEffect(paper?.id, h5NoContent) {
        if (paper != null && h5NoContent && detail == null &&
            !vm.paperDetailLoading && vm.paperDetailError == null
        ) {
            vm.openPaper(paper)
        }
    }

    Scaffold(
        topBar = {
            if (!quizMode) {
                TopAppBar(
                    title = { Text(paper?.title?.ifBlank { "实时练习" } ?: "实时练习") },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (url != null && forceH5) {
                            TextButton(onClick = { forceH5 = false }) { Text("原生") }
                        } else if (h5NoContent && detail != null) {
                            TextButton(onClick = { forceH5 = true }) { Text("官方 H5") }
                        }
                        IconButton(onClick = { webRef?.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            quizMode -> PaperQuizScreen(
                detail = detail!!,
                onBack = { nav.popBackStack() },
                onSwitchToH5 = { forceH5 = true },
                banner = "官方 H5 这卷没给题面（服务端只返回题目 ID，题干要原生注入）——已切到原生刷题，可正常作答、对答案。",
                modifier = Modifier.fillMaxSize(),
            )

            url == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val loggedIn = vm.client.mid.isNotBlank() ||
                    vm.client.dynamicParams["unionid"]?.isNotBlank() == true
                EmptyBox(
                    when {
                        paper == null -> "请先从试卷详情进入"
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

            // 探测说没题面，但题目数据还没拉回来
            h5NoContent && !forceH5 -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (vm.paperDetailError != null) {
                    EmptyBox(vm.paperDetailError ?: "题目加载失败")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { forceH5 = true }) { Text("还是看官方页面") }
                } else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "官方 H5 没给题面，正在准备原生刷题…",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            else -> ExerciseWebView(
                url = url,
                onNoContent = { h5NoContent = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

private var webRef: WebView? = null

private const val H5_HOME = "https://www.yaerxing.com/"

/**
 * 探测页面到底有没有题面（不能只看有没有题号：Vue 的 v-show 会把空的 .q-type 也留在 DOM 里）。
 * 判据：官方渲染引擎 ShuatiMathod 在不在 + 正文有效字符数。降级页 = 无引擎且正文极短。
 */
private val PROBE_JS = """
(function(){
  try{
    var b = document.body;
    var t = b ? (b.innerText || '') : '';
    var compact = t.replace(/\s+/g, '');
    var eng = (typeof window.ShuatiMathod !== 'undefined') ? 1 : 0;
    return JSON.stringify({len: compact.length, eng: eng});
  }catch(e){ return JSON.stringify({len: -1, eng: 0, err: String(e)}); }
})()
""".trimIndent()

private fun isDegraded(raw: String?): Boolean {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return false
    // evaluateJavascript 回的是 JSON 字符串字面量（带引号转义）
    val unquoted = s.removeSurrounding("\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
    val len = Regex("\"len\"\\s*:\\s*(-?\\d+)").find(unquoted)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    val eng = Regex("\"eng\"\\s*:\\s*(\\d+)").find(unquoted)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    if (eng == 1) return false
    return len in 0..400
}

/**
 * 两段式 cookie bootstrap（桌面端 window.py 2026-08-11 实测同款坑，勿直载）：
 * paperExercises 页面内 ajax 拉题干需要 yex_session cookie——直载答题页只种
 * XSRF-TOKEN，题干全空（只剩题号骨架）。先静默访问 H5 首页种会话 cookie，
 * 首页加载完成后再载目标答题页。
 */
private class BootstrapClient(
    private val onLoadingStart: () -> Unit,
    private val onLoadingDone: (WebView?) -> Unit,
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
        onLoadingDone(view)
    }
}

@Composable
private fun ExerciseWebView(
    url: String,
    onNoContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var probed by remember(url) { mutableStateOf(false) }

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
                        onLoadingDone = { view ->
                            loading = false
                            if (view != null && !probed) {
                                probed = true
                                view.evaluateJavascript(PROBE_JS) { raw ->
                                    if (isDegraded(raw)) onNoContent()
                                }
                            }
                        },
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
                    probed = false
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
