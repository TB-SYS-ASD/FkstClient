package com.tb.fkst.ui.screens

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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

    val url = remember(paper) {
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
                        // 旧版本（≤v1.10.0）登录的会话没有 loginToken，升级后不会自动补
                        loggedIn -> "当前会话缺少实时练习凭据（旧版本登录所致），请退出后重新登录"
                        else -> "需要先登录才能进入实时练习"
                    },
                )
            }
            else -> ExerciseWebView(
                url = url,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

private var webRef: WebView? = null

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
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            loading = true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }
                    }
                    webRef = this
                    loadUrl(url)
                }
            },
            update = { wv ->
                // URL 变化（换卷）时重新加载
                if (wv.url != url) wv.loadUrl(url)
            },
        )
        if (loading) {
            LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
        }
    }
}
