package com.tb.fkst.core

import android.util.Base64
import java.net.URLDecoder

/**
 * H5 文章正文解密：base64 → XOR → base64 → urldecode
 *
 * key = MD5(secret_key + api_key)[8:16]，小写
 */
object Crypto {

    fun makeKey(secretKey: Any): String =
        Signer.md5(secretKey.toString() + Constants.API_KEY).substring(8, 16).lowercase()

    /**
     * 等价于 Python 的 urllib.parse.unquote：
     * 只解 %xx，不把 '+' 当空格（Java 的 URLDecoder 会，所以先把 '+' 转义掉）。
     */
    private fun unquote(s: String): String =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    fun decryptContent(cipher: String?, secretKey: Any): String {
        if (cipher.isNullOrEmpty()) return ""
        return try {
            val key = makeKey(secretKey).toByteArray(Charsets.UTF_8)
            val step1 = Base64.decode(cipher, Base64.DEFAULT)
            val xor = ByteArray(step1.size) { i ->
                (step1[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            val step3 = Base64.decode(xor, Base64.DEFAULT)
            unquote(String(step3, Charsets.UTF_8))
        } catch (t: Throwable) {
            ""
        }
    }

    /** 从 H5 页面里抠出密文正文 */
    private val CIPHER_RE = Regex(
        "<div[^>]*class=\"note-content[^>]*>([^<]+)</div>",
        RegexOption.IGNORE_CASE
    )

    fun extractCipher(html: String): String =
        CIPHER_RE.find(html)?.groupValues?.get(1) ?: ""

    /** 粗略去标签，用于列表预览 */
    fun stripTags(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        var t = Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .replace(html, "")
        t = Regex("<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
            .replace(t, " [图片] ")
        t = Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE).replace(t, "\n")
        t = Regex("<[^>]+>").replace(t, "")
        t = unquote(t)
        return Regex("\n{3,}").replace(t, "\n\n").trim()
    }

    /** 把 @提及 的 HTML 包装去掉，便于展示 */
    fun plainContent(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var t = Regex("<span[^>]*class='fkst-at'[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
            .replace(text, "$1")
        t = Regex("<[^>]+>").replace(t, "")
        return t.trim()
    }
}
