package com.tb.fkst.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一次 GitHub Releases 检查的结果。
 *
 * @param hasUpdate   服务端版本号比本地新
 * @param latestTag   最新 Release 的 tag（如 "v1.7.0"）
 * @param latestName  Release 标题（如 "FkstClient V1.7.0"）
 * @param notes       发行说明正文（Markdown 原文，界面里当纯文本展示）
 * @param pageUrl     浏览器打开的下载页
 * @param apkUrl      附带的 .apk 资产下载直链；可能为空（该 Release 没传 apk 资产）
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestTag: String,
    val latestName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
)

/**
 * 从 GitHub Releases 检查更新。
 *
 * - 版本号解析只认 `v?x.y.z`，别的格式（比如 "V1.7.0-rc1"）一律当作「无法比较」，
 *   结果是不会提示 —— 宁可错过，不能误报。
 * - 网络失败/超时返回 null，调用方静默忽略即可，绝不能拿更新失败打扰用户。
 */
object UpdateChecker {

    /** 当前客户端版本（build.gradle 里 versionName，程序启动时塞进来） */
    @Volatile
    var currentVersion: String = "0.0.0"

    /** 把 "v1.7.0" / "1.7.0" 解析成 (1,7,0)；解析不了返回 null */
    fun parseVersion(tag: String): Triple<Int, Int, Int>? {
        val m = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)$""").find(tag.trim()) ?: return null
        val (a, b, c) = m.destructured
        return runCatching { Triple(a.toInt(), b.toInt(), c.toInt()) }.getOrNull()
    }
    /** latest > current ？双方都得能解析，否则一律 false（宁可错过，不能误报） */
    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val l = parseVersion(latestTag) ?: return false
        val c = parseVersion(currentVersion) ?: return false
        return l.first != c.first && l.first > c.first ||
            l.first == c.first && l.second != c.second && l.second > c.second ||
            l.first == c.first && l.second == c.second && l.third > c.third
    }

    /**
     * 拉 GitHub `releases/latest`。
     *
     * `releases/latest` 天然排除草稿和预发布，所以只有打正式 Release 才会触发提示。
     * 超时 [Constants.UPDATE_TIMEOUT_MS]；任何异常都吞掉返回 null。
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(Constants.GITHUB_LATEST_API).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = Constants.UPDATE_TIMEOUT_MS
                conn.readTimeout = Constants.UPDATE_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "FkstClient-UpdateCheck")

                val code = conn.responseCode
                if (code != 200) return@runCatching null
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val o = JSONObject(body)

                val tag = o.str("tag_name")
                if (tag.isBlank()) return@runCatching null

                // 找 .apk 资产（没有也不影响，页面里总有下载入口）
                var apk = ""
                val assets = o.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.str("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apk = a.str("browser_download_url")
                            if (apk.isNotBlank()) break
                        }
                    }
                }
                UpdateInfo(
                    hasUpdate = isNewer(tag, currentVersion),
                    latestTag = tag,
                    latestName = o.str("name").ifBlank { tag },
                    notes = o.str("body"),
                    pageUrl = o.str("html_url").ifBlank { Constants.GITHUB_RELEASES_PAGE },
                    apkUrl = apk,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
