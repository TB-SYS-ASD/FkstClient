package com.tb.fkst.core

import org.json.JSONObject

/** org.json 的 optString 对 JSON null 会返回 "null"，这里统一兜底。 */
fun JSONObject.str(key: String, def: String = ""): String =
    if (isNull(key)) def else optString(key, def)

fun JSONObject.intOr(key: String, def: Int = 0): Int {
    if (isNull(key)) return def
    return when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: def
        is Boolean -> if (v) 1 else 0
        else -> def
    }
}

fun JSONObject.longOr(key: String, def: Long = 0L): Long {
    if (isNull(key)) return def
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: def
        else -> def
    }
}

fun JSONObject.boolOr(key: String, def: Boolean = false): Boolean {
    if (isNull(key)) return def
    return when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", true)
        else -> def
    }
}
