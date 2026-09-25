package com.tb.fkst.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.tb.fkst.core.CacheCleaner
import com.tb.fkst.core.Constants
import com.tb.fkst.core.FkstClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 卡片样式：默认实心卡片 / 玻璃拟态（透明卡片 + 壁纸） */
enum class ThemeStyle { DEFAULT, GLASS }

/**
 * 全局仓库：持有 FkstClient，负责会话与偏好的持久化。
 * 用 SharedPreferences 存，不写明文密码（只存手机号方便下次填）。
 */
class Repository(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("fkst_prefs", Context.MODE_PRIVATE)

    val client = FkstClient()

    // -------------------------------------------------------- 会话

    fun hasSession(): Boolean =
        prefs.getString("mid", null)?.isNotBlank() == true &&
            prefs.getString("mid", "") != "1"

    fun restoreSession(): Boolean {
        val mid = prefs.getString("mid", "") ?: ""
        if (mid.isBlank() || mid == "1") return false
        client.applyDynamic(
            mapOf(
                "mid" to mid,
                "unionid" to (prefs.getString("unionid", "") ?: ""),
                "openid" to (prefs.getString("openid", "") ?: ""),
                "device_token" to (prefs.getString("device_token", "") ?: ""),
            )
        )
        return true
    }

    fun saveSession() {
        prefs.edit()
            .putString("mid", client.dynamicParams["mid"])
            .putString("unionid", client.dynamicParams["unionid"])
            .putString("openid", client.dynamicParams["openid"])
            .putString("device_token", client.dynamicParams["device_token"])
            .apply()
    }

    fun clearSession() {
        client.clearSession()
        prefs.edit()
            .remove("mid").remove("unionid").remove("openid").remove("device_token")
            .apply()
    }

    // -------------------------------------------------------- 账号（记住手机号）

    var savedPhone: String
        get() = prefs.getString("phone", "") ?: ""
        set(v) = prefs.edit().putString("phone", v).apply()

    var rememberMe: Boolean
        get() = prefs.getBoolean("remember_me", true)
        set(v) = prefs.edit().putBoolean("remember_me", v).apply()

    // -------------------------------------------------------- 外观

    /** Monet 动态取色（Android 12+） */
    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(v) = prefs.edit().putBoolean("dynamic_color", v).apply()

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = prefs.edit().putString("theme_mode", v.name).apply()

    /** 打开 App 时自动签到（连续签到 3 天才能解锁私信） */
    var autoCheckIn: Boolean
        get() = prefs.getBoolean("auto_check_in", true)
        set(v) = prefs.edit().putBoolean("auto_check_in", v).apply()

    // -------------------------------------------------------- 私信防撤回
    //
    // 平台没有撤回接口，能观察到的「撤回」就是消息从 GetSTLetterMessage 的返回里消失。
    // 打开这个开关后，每段会话都会在本地留一份归档，消失的消息会以「已撤回」留在列表里。

    /** 私信防撤回：默认开（这是本客户端相对官方客户端的一个增益功能） */
    var antiRecall: Boolean
        get() = prefs.getBoolean("letter_anti_recall", true)
        set(v) = prefs.edit().putBoolean("letter_anti_recall", v).apply()

    /** 会话归档：other_mid -> Letter.encodeArchive(...) 的 JSON */
    var letterArchive: Map<String, String>
        get() = runCatching {
            val raw = prefs.getString("letter_archive", "") ?: ""
            if (raw.isBlank()) emptyMap() else {
                val o = JSONObject(raw)
                o.keys().asSequence()
                    .associateWith { o.optString(it) }
                    .filterValues { it.isNotBlank() }
            }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit()
            .putString(
                "letter_archive",
                JSONObject().apply { v.forEach { (k, s) -> if (s.isNotBlank()) put(k, s) } }.toString(),
            )
            .apply()

    /** 清空防撤回留下的本地聊天备份 */
    fun clearLetterArchive() {
        prefs.edit().remove("letter_archive").apply()
    }

    // -------------------------------------------------------- 作者资料缓存
    //
    // 「赞过 / 收藏」两个接口只给 home_id，不给昵称头像，所以补拉之后缓存一份，
    // 下次进列表就不用再逐个人去问服务端了。

    /** home_id -> AuthorBrief.encode() */
    var authorCache: Map<String, String>
        get() = runCatching {
            val raw = prefs.getString("author_cache", "") ?: ""
            if (raw.isBlank()) emptyMap() else {
                val o = JSONObject(raw)
                o.keys().asSequence()
                    .associateWith { o.optString(it) }
                    .filterValues { it.isNotBlank() }
            }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit()
            .putString(
                "author_cache",
                JSONObject().apply { v.forEach { (k, s) -> if (s.isNotBlank()) put(k, s) } }.toString(),
            )
            .apply()

    fun clearAuthorCache() {
        prefs.edit().remove("author_cache").apply()
    }

    /** 卡片样式 */
    var themeStyle: ThemeStyle
        get() = runCatching {
            ThemeStyle.valueOf(prefs.getString("theme_style", ThemeStyle.DEFAULT.name)!!)
        }.getOrDefault(ThemeStyle.DEFAULT)
        set(v) = prefs.edit().putString("theme_style", v.name).apply()

    // -------------------------------------------------------- 排版

    /**
     * 文章列表的排版：1 = 单列大卡，2 = 双列（一排两个）。
     * 三个 tab（发现 / 搜索 / 我的主页）共用同一个偏好。
     */
    var listColumns: Int
        get() = prefs.getInt("list_columns", 1).coerceIn(1, 2)
        set(v) = prefs.edit().putInt("list_columns", v.coerceIn(1, 2)).apply()

    // -------------------------------------------------------- 投币记录
    //
    // 平台没有「这篇文章我投过几币」的查询接口（笔记对象只有总数 `coins`），
    // 所以按文章 id 本地记一份，用于卡住「每篇最多 2 个币」的上限。

    /** note_id -> 我已经投出去的币数 */
    var noteCoins: Map<String, Int>
        get() = runCatching {
            val raw = prefs.getString("note_coins", "") ?: ""
            if (raw.isBlank()) emptyMap() else {
                val o = JSONObject(raw)
                o.keys().asSequence()
                    .associateWith { o.optInt(it, 0) }
                    .filterValues { it > 0 }
            }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit()
            .putString(
                "note_coins",
                JSONObject().apply { v.forEach { (k, n) -> if (n > 0) put(k, n) } }.toString(),
            )
            .apply()

    // -------------------------------------------------------- 壁纸

    /** 自定义壁纸在本地的绝对路径；空 = 没设置 */
    var wallpaperPath: String
        get() = prefs.getString("wallpaper_path", "") ?: ""
        set(v) = prefs.edit().putString("wallpaper_path", v).apply()

    /** 把相册里的图复制到应用私有目录，返回新路径 */
    fun saveWallpaper(uri: Uri): String? = runCatching {
        val file = File(appContext.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        removeOldWallpapers(keep = file.name)
        wallpaperPath = file.absolutePath
        file.absolutePath
    }.getOrNull()

    fun clearWallpaper() {
        removeOldWallpapers(keep = null)
        wallpaperPath = ""
    }

    private fun removeOldWallpapers(keep: String?) {
        runCatching {
            appContext.filesDir.listFiles()
                ?.filter { it.name.startsWith("wallpaper_") && it.name != keep }
                ?.forEach { it.delete() }
        }
    }

    // -------------------------------------------------------- 私信：置顶 / 备注
    //
    // 平台没有置顶接口，所以置顶完全走本地；
    // 备注优先写服务端（SetSTFollowMark），同时本地也存一份做兜底
    // ——服务端备注只对「已存在关注关系」的人生效。

    /** 置顶的会话（home_id 集合） */
    var pinnedConversations: Set<String>
        get() = prefs.getStringSet("pinned_letters", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("pinned_letters", v).apply()

    /** 本地备注：home_id -> 备注名 */
    var remarks: Map<String, String>
        get() = runCatching {
            val raw = prefs.getString("letter_remarks", "") ?: ""
            if (raw.isBlank()) emptyMap() else {
                val o = JSONObject(raw)
                o.keys().asSequence().associateWith { o.optString(it) }
            }
        }.getOrDefault(emptyMap())
        set(v) = prefs.edit()
            .putString(
                "letter_remarks",
                JSONObject().apply { v.forEach { (k, name) -> put(k, name) } }.toString(),
            )
            .apply()

    // -------------------------------------------------------- 登录

    suspend fun login(phone: String, password: String, remember: Boolean = true) =
        withContext(Dispatchers.IO) {
            val payload = client.login(phone, password)
            saveSession()
            if (remember) savedPhone = phone
            payload
        }

    fun logout() = clearSession()

    /** 校验持久化的会话是否还有效 */
    suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        if (!restoreSession()) return@withContext false
        try {
            Api.myStats(client)
            true
        } catch (t: Throwable) {
            clearSession()
            false
        }
    }

    fun currentMid(): String = client.dynamicParams["mid"] ?: ""

    // -------------------------------------------------------- 缓存（转发给 CacheCleaner）

    fun cacheSizeBytes(): Long = CacheCleaner.size(appContext)

    fun clearCacheBytes(): Long = CacheCleaner.clear(appContext)
}
