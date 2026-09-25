package com.tb.fkst.core

import java.security.MessageDigest

/**
 * 签名算法（三种变体），与官方 APP 完全一致。
 *
 * 通用：  MD5( 字典序拼接 KV + SALT + MD5(tpl)[5:21] ).upper()
 *   其中 tpl = "f0{call_id 后四位}com.yaerxing.fkst{app_c}F.K*$t"
 *
 * 文章详情：把 call_id 换成 timestamp，app_c 换成 app_v
 * 发评论：  只签 api_key + call_id + openid 三个字段
 */
object Signer {

    private const val TEXT_TPL = "f0%scom.yaerxing.fkst%sF.K*\$t"
    private const val NOTE_TPL = "f0%s%sF.K*\$t"

    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    private fun tail4(value: String?): String {
        val v = value ?: return ""
        return if (v.length <= 4) v else v.substring(v.length - 4)
    }

    private fun digestOf(tpl: String): String = md5(tpl).substring(5, 21)

    /** 通用签名 */
    fun signParams(params: Map<String, String>, appC: String = "171"): String {
        val body = StringBuilder()
        params.entries
            .filter { it.key != "api_sig" }
            .sortedBy { it.key }
            .forEach { body.append(it.key).append(it.value) }
        body.append(Constants.SIGN_SECRET)
        body.append(digestOf(TEXT_TPL.format(tail4(params["call_id"]), appC)))
        return md5(body.toString()).uppercase()
    }

    /** H5 文章详情专用：用 timestamp 后四位 */
    fun signNote(params: Map<String, String>): String {
        val body = StringBuilder()
        params.entries
            .filter { it.key != "api_sig" }
            .sortedBy { it.key }
            .forEach { body.append(it.key).append(it.value) }
        body.append(Constants.SIGN_SECRET)
        val appV = Constants.GET_NOTE_PARAMS["app_v"] ?: "171"
        body.append(digestOf(NOTE_TPL.format(tail4(params["timestamp"]), appV)))
        return md5(body.toString()).uppercase()
    }

    /** 发评论专用 */
    fun signComment(params: Map<String, String>): String {
        val callId = params["call_id"] ?: error("评论签名缺少参数: call_id")
        val apiKey = params["api_key"] ?: error("评论签名缺少参数: api_key")
        val openid = params["openid"] ?: error("评论签名缺少参数: openid")
        val appC = params["app_c"] ?: "171"
        val body = StringBuilder()
            .append("api_key").append(apiKey)
            .append("call_id").append(callId)
            .append("openid").append(openid)
            .append(Constants.SIGN_SECRET)
            .append(digestOf(TEXT_TPL.format(tail4(callId), appC)))
        return md5(body.toString()).uppercase()
    }

    fun sign(kind: String, params: Map<String, String>): String = when (kind) {
        "note" -> signNote(params)
        "comment" -> signComment(params)
        else -> signParams(params)
    }

    /** 登录密码 = MD5(明文 + 盐) */
    fun encryptPassword(password: String, salt: String = Constants.PWD_SALT): String =
        md5(password + salt)
}
