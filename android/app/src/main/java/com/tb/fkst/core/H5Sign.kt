package com.tb.fkst.core

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * H5 实时答题页（paperExercises-v18）的 URL 构造 + HMAC-SHA256 签名。
 *
 * 来源：`fkst-desktop-main/fkst_client/letter.py::build_paper_exercise_url`，
 * 由 CEO 破解 + 实测验证（HTTP 200、返回真实试卷页）。
 *
 * 为什么要用这个：官方 H5 在线答题/交卷对第三方 native 接口一律回「非法访问」，
 * 但官方 H5 页面本身接受带签名的会话参数，所以用这个 URL 丢进 WebView，
 * 就能在第三方客户端里真正答题、交卷。这是「试卷库」之外终于补上的真·刷题入口。
 *
 * 签名：seed = build_h5_seed(loginToken)，对全部参数（按 key 字典序、
 * 排除 api_sig）k+v 拼接后做 HMAC-SHA256。seed 公式：
 *   prefix + _b_segment("36.02%") + native + seedTail + loginToken 原文
 * 其中 _b_segment 用到 XOR(15405) 反转 + SHA256[:16]（security/e.java b()）。
 */
object H5Sign {

    private const val H5_API_KEY = "d4fa9e597efc51853950c3244d86857e"
    private const val H5_BASE = "https://www.yaerxing.com/shuati"
    private const val PREFIX = "a90810a656ffc82f"        // fc/e.java f27038j
    private const val NATIVE = "c109702588c70cd2"        // NativeHelper privateString（两站共享）
    private const val SEED_TAIL = "2ec77668983f2d66"     // R.string.seed 资源段（两站共享）
    private const val XOR_KEY = 15405
    private const val OBF_A = "+c8z"
    private const val OBF_B = "*j.#"
    private const val OBF_FIXED = "ffe5688f23c50f16"

    /** security/e.java b("36.02%")：36.02% 段，固定值（CEO 反向核实过） */
    private val SEG36: String = bSegment("36.02%")

    /** XOR(15405) 错位反转：b(a) 公式里 F(s) 的实现 */
    private fun fObf(s: String): String =
        s.map { (it.code xor XOR_KEY).toChar() }.reversed().joinToString("")

    /** security/e.java b(seed)：SHA256(OBF_A + F(OBF_FIXED) + OBF_B + F(seed))[:16] */
    private fun bSegment(seed: String): String {
        val body = OBF_A + fObf(OBF_FIXED) + OBF_B + fObf(seed)
        return sha256Hex(body).take(16)
    }

    /** gd/t.java j()：H5 签名密钥 = prefix + 36.02%段 + native + seed + loginToken 原文 */
    private fun buildSeed(loginToken: String): String =
        PREFIX + SEG36 + NATIVE + SEED_TAIL + loginToken

    /**
     * 构造 paperExercises-v18 URL。缺 loginToken / pid / type 返回 null（调用方提示登录）。
     * 签名 key 用 loginToken 原文；pid/type 来自试卷，mid/tokenSeed/unionid 来自会话。
     */
    fun buildPaperExerciseUrl(
        loginToken: String,
        mid: String,
        tokenSeed: String,
        unionid: String,
        pid: String,
        type: String,
    ): String? {
        if (loginToken.isBlank() || pid.isBlank() || type.isBlank()) return null

        val params = LinkedHashMap<String, String>().apply {
            put("adolescent_model", "0")
            put("api_key", H5_API_KEY)
            put("app_v", "188")
            put("appid", "wx2bd42ba7f4c547f5")
            put("font_size", "2")
            put("mid", mid)
            put("os_v", "29")
            put("platform_id", "2")
            put("rom", "OPPO")
            put("sign_algorithm", "hamc-sha256")
            put("timestamp", System.currentTimeMillis().toString())
            put("token_seed", tokenSeed)
            put("unionid", unionid)
            // biz 5 键
            put("pid", pid)
            put("enter", "0")
            put("type", type)
            put("aid", "1")
            put("secret_key", (100_000 + Random.nextInt(0, 100_001)).toString())
        }

        val seed = buildSeed(loginToken)
        params["api_sig"] = hmacSha256(seed, concatParams(params))
        return "$H5_BASE/paperExercises-v18?" + urlEncode(params)
    }

    /** H5 签名拼接：排除 api_sig，全部参数（含空值）按 key 字典序 k+v */
    private fun concatParams(params: Map<String, String>): String =
        params.entries
            .filter { it.key != "api_sig" }
            .sortedBy { it.key }
            .joinToString("") { "${it.key}${it.value}" }

    private fun urlEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" +
                java.net.URLEncoder.encode(v, "UTF-8")
        }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
