package com.tb.fkst.core

/**
 * 图片地址工具。
 *
 * 官方 CDN（imgcdn.yaerxing.com）的规则是：
 * ```
 * http://imgcdn.yaerxing.com/resize_540x540/upimage/stupnote/2026/09/25/xxx.jpg   ← 列表用的缩略/裁剪图
 * http://imgcdn.yaerxing.com/upimage/stupnote/2026/09/25/xxx.jpg                  ← 上传时的原图
 * ```
 * 接口里 `urls` / `thumb` 返回的都是带 `resize_*` 前缀的版本，
 * 把这一段去掉就是服务器上保存的原图（未压缩、无平台叠加水印）。
 * 头像字段 `logo` 本身就是原图形态，可以印证这个规则。
 */
object ImageUrls {

    /** 匹配 `/resize_540x540/`、`/resize_1080x1080/` 这类 CDN 缩放段 */
    private val RESIZE_SEGMENT = Regex("/resize_\\d+x\\d+/")

    /** 去掉 CDN 缩放段，拿原图（下载/全屏查看用这个） */
    fun origin(url: String): String {
        if (url.isBlank()) return url
        return url.replace(RESIZE_SEGMENT, "/")
    }

    /** 是否是 http(s) 图片地址 */
    fun isRemote(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true)

    /** 猜测扩展名，用于下载时命名 */
    fun extensionOf(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "")
        return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) ".$ext" else ".jpg"
    }

    /** 建议的保存文件名 */
    fun fileNameOf(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val base = clean.substringAfterLast('/')
        return if (base.isNotBlank() && base.contains('.')) base else "fkst_${System.currentTimeMillis()}.jpg"
    }

    // ---------------------------------------------------------------- 文件

    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
    )

    /** 已知的「非图片」附件扩展名（用来把私信里的文件消息和图片消息分开） */
    private val FILE_EXT = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv",
        "zip", "rar", "7z", "apk", "mp3", "mp4", "m4a", "wav", "aac", "amr", "epub",
    )

    /** URL 路径里的扩展名（小写，不含点） */
    fun rawExtensionOf(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "")
        return ext.lowercase().takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: ""
    }

    fun isImageUrl(url: String): Boolean = rawExtensionOf(url) in IMAGE_EXT

    fun isFileUrl(url: String): Boolean = rawExtensionOf(url) in FILE_EXT

    /** 文件名（用于展示） */
    fun baseNameOf(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        return clean.substringAfterLast('/').ifBlank { url }
    }

    /** 人类可读的体积 */
    fun readableSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
