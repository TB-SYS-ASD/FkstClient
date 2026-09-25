package com.tb.fkst.core

import android.content.Context
import coil.Coil
import java.io.File

/**
 * 缓存清理。
 *
 * 只碰「缓存」：Coil 的图片内存/磁盘缓存 + 应用 cacheDir / externalCacheDir 下的临时文件。
 * 不动这些用户数据 —— 壁纸（filesDir）、备注/置顶/投币记录（SharedPreferences）、
 * 防撤回的聊天备份（另有单独的清除入口，见设置页）。
 *
 * 也刻意不碰 `codeCacheDir`：那是 ART 的编译产物，删了不丢数据但会让启动变慢。
 */
object CacheCleaner {

    /** Coil 默认的磁盘缓存目录 */
    fun imageCacheDir(context: Context): File = File(context.cacheDir, "image_cache")

    private fun dirs(context: Context): List<File> =
        listOfNotNull(context.cacheDir, context.externalCacheDir)
            .filter { it.exists() }
            .distinctBy { it.absolutePath }

    /** 当前缓存占用（字节） */
    fun size(context: Context): Long =
        runCatching { dirs(context).sumOf { dirSize(it) } }.getOrDefault(0L)

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return runCatching {
            dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /**
     * 清空缓存，返回清理前的占用（方便提示「已释放 xx MB」）。
     */
    fun clear(context: Context): Long {
        val before = size(context)

        // 1) Coil：先清内存，再清磁盘（顺序无所谓，但先内存能立刻释放）
        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }

        // 2) 缓存目录里的临时文件
        dirs(context).forEach { dir ->
            runCatching {
                dir.listFiles()?.forEach { child ->
                    runCatching { child.deleteRecursively() }
                }
            }
        }

        return before
    }

    /** 人类可读的占用描述 */
    fun readableSize(context: Context): String {
        val bytes = size(context)
        if (bytes <= 0L) return "0 B"
        return ImageUrls.readableSize(bytes)
    }
}
