package com.tb.fkst.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlin.random.Random

/** 服务端返回 res != 0，或网络/解析失败。 */
class FkstApiException(
    val endpoint: String,
    val payload: JSONObject?,
    message: String,
) : Exception(message)

/**
 * HTTP 客户端：参数组装、签名、限频、重试。
 * 与项目内 Python 版 `fkst_sdk/client.py` 行为一致。
 */
class FkstClient(
    private val minIntervalMs: Long = 1200L,
    private val jitterMs: Long = 500L,
    private val timeoutMs: Int = 20_000,
) {

    val dynamicParams: MutableMap<String, String> =
        LinkedHashMap<String, String>().apply { putAll(Constants.DEFAULT_DYNAMIC) }

    /** 当前登录身份；未登录时是 "1" */
    val mid: String get() = dynamicParams["mid"] ?: "1"

    val isGuest: Boolean get() = dynamicParams["unionid"]?.equals("guest", true) != false

    private var lastRequestAt = 0L
    private val throttleLock = Mutex()

    // ------------------------------------------------------------------ 参数

    fun buildParams(
        endpointName: String,
        ep: Endpoint,
        input: Map<String, String>,
    ): LinkedHashMap<String, String> {
        val params = LinkedHashMap<String, String>()
        params.putAll(ep.defaults)

        val missing = ep.required.filter { it !in input.keys && it !in params.keys }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("$endpointName 缺少必需参数: $missing")
        }

        if (!ep.skipDevice) params.putAll(Constants.DEVICE_PARAMS)

        val dyn = LinkedHashMap(dynamicParams)
        ep.excludeDynamic.forEach { dyn.remove(it) }
        params.putAll(dyn)

        if (endpointName != "GET_NOTE") {
            params["call_id"] = System.currentTimeMillis().toString()
        }

        params.putAll(input)
        return params
    }

    // ------------------------------------------------------------------ 请求

    suspend fun request(
        endpointName: String,
        params: Map<String, String> = emptyMap(),
        expectRes: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val ep = Endpoints.MAP[endpointName]
            ?: throw IllegalArgumentException("未定义的端点: $endpointName")

        val built = buildParams(endpointName, ep, params)
        val signed = LinkedHashMap(built)
        signed["api_sig"] = Signer.sign(ep.sign, built)
        val url = ep.baseUrl + ep.path

        var text: String? = null
        var lastError: Throwable? = null
        for (attempt in 0 until 3) {
            throttle()
            try {
                text = send(ep.method, url, signed)
                break
            } catch (t: Throwable) {
                lastError = t
                delay(1500L * (attempt + 1))
            }
        }

        val body = text ?: throw FkstApiException(
            endpointName, null, "网络失败: ${lastError?.message ?: "unknown"}"
        )

        // 文章详情返回的是 HTML，单独包一层
        if (endpointName == "GET_NOTE") {
            return@withContext JSONObject().apply {
                put("res", 0)
                put("html", body)
                put("content", Crypto.extractCipher(body))
            }
        }

        val json = try {
            JSONObject(body)
        } catch (t: Throwable) {
            throw FkstApiException(endpointName, null, "非 JSON 响应: ${body.take(200)}")
        }

        if (expectRes && json.intOr("res", -1) != 0) {
            val msg = json.str("error").ifBlank {
                json.str("remind_hint").ifBlank { json.toString() }
            }
            throw FkstApiException(endpointName, json, "[$endpointName] res=${json.opt("res")} $msg")
        }
        json
    }

    // ------------------------------------------------------------------ 图片上传

    /**
     * 上传一张图片（multipart/form-data）。
     *
     * 实测（2026-09-25）`OSSUploadImage4.php`：
     * - 用**通用签名**，参数要有 `dir_name`（服务端白名单目录），文件字段名固定 `file`
     * - 成功：`{"res":0,"illegal":false,"url":"http://imgcdn.yaerxing.com/upimage/<dir>/…"}`
     * - `illegal=true`：图片内容审核没通过（换一张即可）
     * - `res=2` + 「请开通会员」：该目录是会员功能
     * - `res=2` + 「未创建文件夹:xxx」：目录不在白名单里
     *
     * 这个方法**不抛 res!=0 的异常**，把原始 JSON 交给上层判断
     * （因为失败响应里也可能带可用的 url，而且有 illegal 这种业务态）。
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg",
        dirName: String = Constants.IMAGE_DIR_NOTE,
        endpoint: String = "UPLOAD_IMAGE",
    ): JSONObject = upload(endpoint, bytes, fileName, mimeType, dirName)

    /**
     * 上传一个普通文件（multipart/form-data）。
     *
     * 实测（2026-09-25）`OSSUploadFile2.php`：参数与图片上传完全一致
     * （通用签名 + `dir_name` + 文件字段 `file`），但落到 `upfile/` 目录：
     * - 成功：`{"res":0,"url":"http://imgcdn.yaerxing.com/upfile/<dir>/…","md5":"…"}`
     * - `{"res":1,"error":"不允许的文件类型!"}`：扩展名被服务端黑名单挡掉（zip 不行）
     * - `{"res":1,"error":"非法路径"}`：该目录不接受文件（`stupletter` 只收图）
     *
     * 同样**不抛 res!=0 的异常**，交给上层判断错误文案。
     */
    suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "application/octet-stream",
        dirName: String = Constants.FILE_DIR_NOTE,
    ): JSONObject = upload("UPLOAD_FILE", bytes, fileName, mimeType, dirName)

    /**
     * 上传一段音频（multipart/form-data）。
     *
     * 实测（2026-09-25）`OSSUploadAudio2.php`：
     * - 通用签名 + 文件字段 `file`，**不需要 `dir_name`**
     * - **只收 `.mp3`**：wav / m4a 一律回 `{"res":1,"error":"upload audio failed"}`
     * - 成功：`{"res":0,"url":"http://imgcdn.yaerxing.com/audio/<年月日>/<随机>.mp3"}`
     *
     * 同样**不抛 res!=0 的异常**，交给上层判断错误文案。
     */
    suspend fun uploadAudio(
        bytes: ByteArray,
        fileName: String = "voice.mp3",
        mimeType: String = "audio/mpeg",
    ): JSONObject = upload("UPLOAD_AUDIO", bytes, fileName, mimeType, "")

    private suspend fun upload(
        endpoint: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        dirName: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val ep = Endpoints.MAP[endpoint]
            ?: throw IllegalArgumentException("未定义的端点: $endpoint")

        // 音频上传没有 dir_name 这个概念，留空时不带这个字段
        val extra = if (dirName.isBlank()) emptyMap() else mapOf("dir_name" to dirName)
        val built = buildParams(endpoint, ep, extra)
        val signed = LinkedHashMap(built)
        signed["api_sig"] = Signer.sign(ep.sign, built)

        val boundary = "----FkstBoundary" + java.util.UUID.randomUUID().toString().replace("-", "")
        val body = buildMultipartBody(signed, "file", fileName, mimeType, bytes, boundary)

        throttle()
        val text = sendMultipart(ep.baseUrl + ep.path, body, boundary)

        try {
            JSONObject(text)
        } catch (t: Throwable) {
            throw FkstApiException(endpoint, null, "非 JSON 响应: ${text.take(200)}")
        }
    }

    /** 拼 multipart 请求体：普通字段 + 一个文件字段 */
    private fun buildMultipartBody(
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        boundary: String,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val crlf = "\r\n"
        fields.forEach { (k, v) ->
            out.write(
                ("--$boundary$crlf" +
                    "Content-Disposition: form-data; name=\"$k\"$crlf$crlf" +
                    "$v$crlf").toByteArray(Charsets.UTF_8)
            )
        }
        out.write(
            ("--$boundary$crlf" +
                "Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"$crlf" +
                "Content-Type: $mimeType$crlf$crlf").toByteArray(Charsets.UTF_8)
        )
        out.write(data)
        out.write(crlf.toByteArray(Charsets.UTF_8))
        out.write("--$boundary--$crlf".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun sendMultipart(url: String, body: ByteArray, boundary: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "okhttp/4.9.0")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                ?: throw FkstApiException("upload", null, "HTTP $code")
            val raw = stream.use { it.readBytes() }
            val gzipped = conn.getHeaderField("Content-Encoding")
                ?.contains("gzip", ignoreCase = true) == true
            val bytes = if (gzipped && raw.size >= 2) {
                GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
            } else raw
            if (code !in 200..299) {
                throw FkstApiException("upload", null, "HTTP $code: ${String(bytes, Charsets.UTF_8).take(160)}")
            }
            return String(bytes, Charsets.UTF_8).trim()
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun throttle() {
        throttleLock.withLock {
            val now = System.currentTimeMillis()
            val need = minIntervalMs + Random.nextLong(0, jitterMs + 1)
            val gap = now - lastRequestAt
            if (lastRequestAt != 0L && gap < need) delay(need - gap)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun encode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    private fun send(method: String, url: String, params: Map<String, String>): String {
        val isGet = method.equals("GET", ignoreCase = true)
        val conn = (if (isGet) URL("$url?${encode(params)}") else URL(url))
            .openConnection() as HttpURLConnection

        try {
            conn.requestMethod = if (isGet) "GET" else "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "okhttp/4.9.0")
            conn.setRequestProperty("Accept-Encoding", "gzip")

            if (!isGet) {
                conn.doOutput = true
                conn.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
                )
                val body = encode(params).toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                ?: throw FkstApiException("http", null, "HTTP $code")

            val raw = stream.use { it.readBytes() }
            val gzipped = conn.getHeaderField("Content-Encoding")
                ?.contains("gzip", ignoreCase = true) == true
            val bytes = if (gzipped && raw.size >= 2) {
                GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
            } else raw

            if (code !in 200..299) {
                throw FkstApiException("http", null, "HTTP $code: ${String(bytes, Charsets.UTF_8).take(160)}")
            }
            return String(bytes, Charsets.UTF_8).trim()
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ 会话

    fun updateDynamic(vararg pairs: Pair<String, String?>) {
        pairs.forEach { (k, v) ->
            if (!v.isNullOrBlank()) dynamicParams[k] = v
        }
    }

    fun applyDynamic(map: Map<String, String>) {
        dynamicParams.clear()
        dynamicParams.putAll(Constants.DEFAULT_DYNAMIC)
        map.forEach { (k, v) -> if (v.isNotBlank()) dynamicParams[k] = v }
    }

    fun clearSession() {
        dynamicParams.clear()
        dynamicParams.putAll(Constants.DEFAULT_DYNAMIC)
    }

    suspend fun login(phone: String, password: String): JSONObject {
        val payload = request(
            "LOGIN",
            mapOf(
                "phone_number" to phone,
                "password" to Signer.encryptPassword(password),
                "verify_type" to "1",
            )
        )
        updateDynamic(
            "unionid" to payload.str("unionid"),
            "openid" to payload.str("openid"),
            "mid" to payload.str("mid"),
            "device_token" to payload.str("device_token"),
            // H5 实时答题（paperExercises-v18）签名种子要用 loginToken 原文；
            // tokenSeed 同样要带进 H5 query。login() 漏抓这两个会导致只能看题、不能跳官方 H5 练习。
            "loginToken" to payload.str("loginToken"),
            "tokenSeed" to payload.str("tokenSeed"),
        )
        return payload
    }
}
