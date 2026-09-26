package com.tb.fkst.core

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * YEX 账号加密登录（移植自 fkst-desktop-main/fkst_client/client.py）。
 *
 * 为什么需要它：ST 侧的 `STAccountLogin3` 明文响应里只有 unionid/openid/mid/device_token，
 * **没有 loginToken / tokenSeed**（2026-09-26 抓包证实），而官方 H5 实时练习
 * （paperExercises-v18）的 URL 签名必须要 loginToken 原文。
 *
 * 这条通道是官方 APP 真正的登录流程（GuideLoginActivity）：
 *   1. 业务 JSON（手机号+密码）用「验证公钥」做 RSA-PKCS1v15 包裹的 AES-256-GCM 信封加密；
 *   2. 同时带上一对**客户端现生成**的设备 RSA 公钥（public_key 参数）；
 *   3. 服务端把账号数据（含 loginToken/tokenSeed）用这把公钥加密成 encryptedAccount 回来；
 *   4. 客户端用自己手里的私钥解密拿到明文账号。
 *
 * 签名与 ST 三件套完全不同：seed 是 64 位 hex 常量，HMAC-SHA256，登录时全量签名。
 */
object YexLogin {

    // ---- 常量（与 desktop client.py 完全一致）----
    private const val API_KEY = "d5f020b1acab8d83cba6953ad01633ba"
    private const val APP_V = "2.2.5"
    private const val APP_C = "188"
    private const val APPID = "wx2bd42ba7f4c547f5"
    private const val PLATFORM_ID = "2"
    private const val BASE_URL = "https://api.yaerxing.com"
    private const val LOGIN_PATH = "YEXAccountLoginByPhoneNumber"

    private const val SEED =
        "ffe5688f23c50f1621bec974d5f997eac109702588c70cd22ec77668983f2d66"

    // 抓包示例设备（OPPO PJJ110 / Android 10）
    private const val DEVICE_IMEI = "2e87169336ceebac4fb541ba82dda677"
    private const val DEVICE_ROM = "OPPO"
    private const val DEVICE_BRAND = "OPPO"
    private const val DEVICE_MODEL = "PJJ110"
    private const val DEVICE_OS_V = "29"

    /** 服务端验证公钥（APK assets 里的 verification_public_key.pem，原样内置）。 */
    private const val VERIFICATION_PUB_PEM =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA05pCoeSHZ1m+2dklWxh9\n" +
            "UbuDfzusCf23oa3HzX8DxgckOZBBEKvxxlWK8Jn2tEijhma7AjP20AM79nEIVmyk\n" +
            "s+CXiqHL9Ct8Tvh4OpsvFu3v+U4zQ1Q34UWxHHI6JC5dOv+B6PMqaFdWAtr6bMSR\n" +
            "u69lFbMCYucth/scV6YDTxc06gSknufnaSoC1v/7knuKg7XL8gjAqMQsttGmnCYn\n" +
            "3biE3ZwJIPygJB3i7mjves7nIksTgeU7EdX54Cja9AmREcU3aWnbWCs7w9LBaqWr\n" +
            "WAfRryXYCOuWO3eT4WJje9BN+7zNXeYxSLBJf+sPB+w1M/wd/BYyF1P0kvwlP/NY\n" +
            "swIDAQAB\n" +
            "-----END PUBLIC KEY-----"

    class YexLoginException(message: String, val result: JSONObject? = null) :
        Exception(message)

    /**
     * 手机号+密码登录，返回解密后的账号 JSON。
     * 包含 mid / openid / unionid / tokenSeed / deviceToken / loginToken 等字段。
     *
     * [session] 用于注入当前会话的公共参数（mid/unionid/...），登录前大多是 guest 也无妨。
     */
    fun passwordLogin(
        phone: String,
        password: String,
        session: FkstClient,
    ): JSONObject {
        // ---- 1. 设备 RSA 密钥对（每次登录现生成，私钥只用于本次解密）----
        val kg = KeyPairGenerator.getInstance("RSA")
        kg.initialize(2048)
        val devKey = kg.generateKeyPair()
        val devPubB64 = Base64.encodeToString(devKey.public.encoded, Base64.NO_WRAP)

        // ---- 2. 业务 JSON 信封加密 ----
        val business = JSONObject().apply {
            put("phone_number", phone)
            put("password", password)
        }.toString()

        val verPub = pemPublicKey(VERIFICATION_PUB_PEM)
        val aesKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val encKey = Base64.encodeToString(
            rsaEncrypt(verPub, aesKey), Base64.NO_WRAP
        )
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val encData = Base64.encodeToString(
            aesGcmEncrypt(aesKey, iv, business.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )

        // ---- 3. 组参数 + 全量签名 ----
        val params = linkedMapOf<String, String>(
            "api_key" to API_KEY,
            "call_id" to System.currentTimeMillis().toString(),
            "platform_id" to PLATFORM_ID,
            "app_v" to APP_V,
            "app_c" to APP_C,
            "appid" to APPID,
            "mid" to session.mid,
            "unionid" to (session.dynamicParams["unionid"] ?: "guest"),
            "openid" to (session.dynamicParams["openid"] ?: "guest"),
            "token_seed" to (session.dynamicParams["tokenSeed"] ?: ""),
            "device_token" to (session.dynamicParams["device_token"] ?: ""),
            "url_name" to "",
            "oam" to "0",
            "sign_algorithm" to "hamc-sha256",
            "device_imei" to DEVICE_IMEI,
            "rom" to DEVICE_ROM,
            "brand" to DEVICE_BRAND,
            "model" to DEVICE_MODEL,
            "os_v" to DEVICE_OS_V,
            "public_key" to devPubB64,
            "encrypt_aes_key" to encKey,
            "encrypt_data" to encData,
        )
        // 登录时无 loginToken，seed 不附加；全量签名（按 key 排序拼 KV）
        val concat = params.entries
            .filter { it.key != "api_sig" }
            .sortedBy { it.key }
            .joinToString("") { it.key + it.value }
        params["api_sig"] = hmacSha256Hex(SEED, concat)

        // ---- 4. 发请求 ----
        val text = postForm("$BASE_URL/$LOGIN_PATH", params)
        val result = JSONObject(text)
        val res = result.optInt("res", -1)
        if (res != 0) {
            val msg = result.optString("msg")
                .ifBlank { result.optString("remind_hint") }
                .ifBlank { "res=$res" }
            throw YexLoginException(msg, result)
        }
        val encrypted = result.optJSONObject("encryptedAccount")
            ?: throw YexLoginException("响应缺少 encryptedAccount", result)

        // ---- 5. 解密账号 ----
        val accountKey = rsaDecrypt(devKey.private, b64(encrypted.getString("key")))
        val accIv = b64(encrypted.getString("iv"))
        val accCt = b64(encrypted.getString("ciphertext"))
        val accTag = b64(encrypted.optString("tag"))
        val plain = aesGcmDecrypt(accountKey, accIv, accCt + accTag)
        return JSONObject(String(plain, Charsets.UTF_8))
    }

    // ------------------------------------------------------------ 工具

    private fun b64(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

    private fun pemPublicKey(pem: String): PublicKey {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = X509EncodedKeySpec(b64(body))
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    private fun rsaEncrypt(pub: PublicKey, data: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.ENCRYPT_MODE, pub)
            doFinal(data)
        }

    private fun rsaDecrypt(priv: PrivateKey, data: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.DECRYPT_MODE, priv)
            doFinal(data)
        }

    /** AES-256-GCM，输出 IV 前置 + 密文 + 128 位 tag（与 cryptography.AESGCM 一致）。 */
    private fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain) // Android 已带 tag 后缀
        return iv + ct
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ctWithTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ctWithTag)
    }

    fun hmacSha256Hex(key: String, msg: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** 表单 POST（gzip 响应处理与 FkstClient.send 一致）。 */
    private fun postForm(url: String, params: Map<String, String>): String {
        val body = params.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" +
                java.net.URLEncoder.encode(v, "UTF-8")
        }.toByteArray(Charsets.UTF_8)

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "android")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                ?: throw YexLoginException("HTTP $code")
            val raw = stream.use { it.readBytes() }
            val gzipped = conn.getHeaderField("Content-Encoding")
                ?.contains("gzip", ignoreCase = true) == true
            val bytes = if (gzipped && raw.size >= 2) {
                GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
            } else raw
            val text = String(bytes, Charsets.UTF_8).trim()
            if (code !in 200..299) throw YexLoginException("HTTP $code: ${text.take(160)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
