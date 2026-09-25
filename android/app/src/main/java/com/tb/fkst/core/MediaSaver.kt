package com.tb.fkst.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 把网络图片存到系统相册。
 *
 * 传进来的一定要是 [ImageUrls.origin] 处理过的原图地址
 * ——接口返回的 `resize_*` 缩略图既小，也不是平台保存的原始文件。
 */
object MediaSaver {

    /** 下载并保存；成功返回「相册/子目录/文件名」这样的可读位置 */
    suspend fun downloadToGallery(context: Context, url: String): String =
        withContext(Dispatchers.IO) {
            require(url.isNotBlank()) { "图片地址为空" }
            val bytes = fetch(url)
            val name = uniqueName(ImageUrls.fileNameOf(url))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bytes, name)
            } else {
                saveToPublicDir(context, bytes, name)
            }
        }

    private fun fetch(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 40_000
            conn.instanceFollowRedirects = true
            // CDN 对裸请求比较敏感，带上跟 App 一致的 UA 与来源页
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            conn.setRequestProperty("Referer", Constants.WEB_BASE + "/")
            conn.setRequestProperty("Accept", "image/*,*/*;q=0.8")
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** Android 10+：走 MediaStore，不需要任何权限 */
    private fun saveViaMediaStore(context: Context, bytes: ByteArray, name: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeOf(name))
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + Constants.DOWNLOAD_ALBUM,
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法写入相册")

        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入相册")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "${Environment.DIRECTORY_PICTURES}/${Constants.DOWNLOAD_ALBUM}/$name"
    }

    /** Android 9 及以下：写到公共 Pictures 目录再通知相册刷新（需要存储权限） */
    private fun saveToPublicDir(context: Context, bytes: ByteArray, name: String): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Constants.DOWNLOAD_ALBUM,
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeOf(name)),
            null,
        )
        return file.absolutePath
    }

    private fun uniqueName(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ".jpg"
        return "${base}_${System.currentTimeMillis() % 100000}$ext"
    }

    private fun mimeOf(name: String): String = when {
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".bmp", true) -> "image/bmp"
        else -> "image/jpeg"
    }
}
