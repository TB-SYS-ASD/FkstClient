package com.tb.fkst.core

/**
 * 常量表：域名、密钥、设备指纹、接口定义。
 *
 * 全部来自对官方 APK（com.yaerxing.fkst 2.0.8 / 2.3.3）的逆向分析，
 * 与项目内 Python 版 SDK (`fkst_sdk/`) 保持完全一致。
 */
object Constants {

    const val API_BASE = "https://api.yaerxing.com/"
    const val WEB_BASE = "https://www.yaerxing.com"

    /** 官方 APP 内置的 api_key */
    const val API_KEY = "17bf6ed3b808eb7dcfa5wa0f1f0cf1de"

    /** 签名盐 */
    const val SIGN_SECRET = "9bldwb2d5d02e81h"

    /**
     * 密码盐。原文含希腊字母，这里用转义写法避免源文件编码问题：
     * Γ = U+0393, Κ = U+039A, ζ = U+03B6, Τ = U+03A4
     */
    const val PWD_SALT = "\u0393_\u039A-\u03B6.\u03A4fkst"

    /** 固定设备指纹（官方客户端硬编码，实测仍有效） */
    val DEVICE_PARAMS: Map<String, String> = linkedMapOf(
        "api_key" to API_KEY,
        "appid" to "wx2bd42ba7f4c547f5",
        "app_c" to "171",
        "app_v" to "2.0.2",
        "channel" to "none",
        "platform_id" to "2",
        "device_imei" to "f448c5eaf564af4dc63d5c0587e68290",
        "rom" to "OPPO",
        "model" to "PJJ110",
        "brand" to "OPPO",
        "os_v" to "29",
        "oam" to "0",
        "url_name" to "",
        "device_token" to "",
        "identity" to "171171dguf117cf2.a011f178egua59bd3dest.2194st1h",
        "um_token" to "AjyrWarcqPA-F-J60x70BmVVl8f0BWZzsx2WtcdvgSJm",
    )

    /** 未登录时的游客身份 */
    val DEFAULT_DYNAMIC: Map<String, String> = linkedMapOf(
        "unionid" to "guest",
        "openid" to "guest",
        "mid" to "1",
    )

    /** H5 文章详情专用参数（注意 app_v 这里填的是 app_c） */
    val GET_NOTE_PARAMS: Map<String, String> = linkedMapOf(
        "adolescent_model" to "0",
        "api_key" to API_KEY,
        "app_v" to "171",
        "appid" to "wx2bd42ba7f4c547f5",
        "channel" to "none",
        "font_size" to "2",
        "os_v" to "29",
        "platform_id" to "2",
        "rom" to "OPPO",
        "version" to "2",
    )

    /** 社区分区 */
    data class Category(
        val key: String,
        val label: String,
        val type: String,
        val extra: Map<String, String> = emptyMap(),
    )

    val CATEGORIES: List<Category> = listOf(
        Category("daily", "日常", "10"),
        Category("goods", "好物", "7"),
        Category("paper", "试卷", "12", mapOf("subject_tag" to "0", "grade_tag" to "0")),
        Category("hard", "难题趣题", "6", mapOf("grade_tag" to "0")),
        Category("experience", "学习经验", "2", mapOf("xd_tag" to "0")),
        Category("drawing", "绘画", "9"),
        Category("plog", "学习Plog", "13"),
        Category("handmade", "手工种植", "8"),
        Category("feihualing", "飞花令", "11"),
        Category("essay", "作文随笔", "16"),
        Category("calligraphy", "书法", "14"),
    )

    /** 用户笔记列表的 type（0 = 全部） */
    const val USER_NOTE_ALL = "0"

    // ---------------------------------------------------------------- 图片上传
    //
    // 2026-09-25 实测 OSSUploadImage4.php：
    //   POST multipart，通用签名，字段 dir_name（目录）+ file（文件）
    //   成功 → {"res":0,"illegal":false,"url":"http://imgcdn.yaerxing.com/upimage/<dir>/…"}
    //   illegal=true 表示内容审核没过；dir_name 白名单外的目录回「未创建文件夹」。

    /** 笔记配图目录 */
    const val IMAGE_DIR_NOTE = "stupnote"

    /** 私信图片目录（⚠️ 实测非会员会回「请开通会员」） */
    const val IMAGE_DIR_LETTER = "stupletter"

    /** 头像目录 */
    const val IMAGE_DIR_LOGO = "stuplogo"

    /** 私信图片的回落目录：stupletter 要会员，普通账号退到这里 */
    const val IMAGE_DIR_NOTE_FALLBACK = "stupnote"

    /** 下载图片时保存到相册的子目录名 */
    const val DOWNLOAD_ALBUM = "疯狂刷题"

    // ---------------------------------------------------------------- 文件上传
    //
    // 2026-09-25 实测 OSSUploadFile2.php：
    //   POST multipart，通用签名，字段 dir_name（目录）+ file（文件）
    //   成功 → {"res":0,"url":"http://imgcdn.yaerxing.com/upfile/<dir>/…","md5":"…"}
    //   {"res":1,"error":"不允许的文件类型!"} → 该扩展名被挡（zip 就不行）
    //   {"res":1,"error":"非法路径"} → 该目录不收文件（stupletter 只收图）

    /** 笔记附件目录（唯一实测可传文件的目录） */
    const val FILE_DIR_NOTE = "stupnotefile"

    /** 私信发文件统一用这个目录（stupletter 不接受文件） */
    const val FILE_DIR_LETTER = "stupnotefile"

    /** 单次投币的上限：平台每篇文章最多 2 个币 */
    const val COIN_PER_NOTE_MAX = 2

    // ---------------------------------------------------------------- 私信消息类型
    //
    // SendSTLetterMessage 的 type 参数。文本 1 / 图片 2 是从 2.0.x 的抓包推断的，
    // 文件 3 属于外推值：**这三个都还没被真机验证过**
    // （账号要连续签到满 3 天才允许发私信，09-27 才解锁）。
    // 好消息是发文件做了退化处理：type=3 不被接受时会自动改成「文本消息 + 附件链接」。
    //
    // 另外，从原版 APK 的字符串池里挖到两个私信气泡布局：letter_type_101 / letter_type_102，
    // 不排除服务端实际用的是 101/102。等解锁后跑一次实测就能定，届时只改这里。
    const val LETTER_TYPE_TEXT = "1"
    const val LETTER_TYPE_IMAGE = "2"
    const val LETTER_TYPE_FILE = "3"

    // ---------------------------------------------------------------- 私信刷新
    //
    // 会话页每 10 秒静默拉一次新消息（不弹 loading、不报错，只在数据有变化时才更新界面）。
    const val LETTER_REFRESH_MS = 10_000L

    // ---------------------------------------------------------------- 发布笔记
    //
    // 2026-09-25 实测 UploadNote2（**comment 签名变体**）：
    //   POST urls=<JSON 数组> & title=<标题> [& content=<JSON 包>] [& type=<分区>]
    //   成功 → {"res":0,"id":"5145261"}
    //   少参数 → {"res":1,"remind_hint":"缺少参数 urls"}（注意不是 "xxx field missing"）
    //   发太频 → {"res":2,"remind_hint":"发布频繁，两贴发布间隔至少5分钟"}
    //
    // `title` 之外的字段服务端都有默认值，所以只传 urls + title 也能发出去。
    // `urls` 传图片地址的 JSON 数组，服务端会把第一张当封面存进 `logo` 字段。
    // 删除自己发的笔记：DeleteShuatiNote + nid（通用签名），成功 → {"res":0}
    const val NOTE_PUBLISH_MIN_INTERVAL_SEC = 300

    /** 发布间隔限制（秒），用于前端提前拦一下 */
    const val NOTE_CONTENT_VERSION = 1

    // ---------------------------------------------------------------- 缓存
    //
    // 清理缓存时不会碰这些「用户数据」：备注 / 置顶 / 投币记录 / 壁纸 / 防撤回备份
    // （壁纸和防撤回备份另外给了单独的清除入口）。
    const val AUTHOR_CACHE_LIMIT = 400
}

/**
 * 接口定义。
 * @param path          方法名（拼在 baseUrl 后面）
 * @param method        POST / GET
 * @param sign          default | note | comment
 * @param required      业务必需参数（缺失直接抛错，便于调试）
 * @param defaults      默认值
 * @param skipDevice    是否不携带设备参数
 * @param excludeDynamic 需要剔除的动态参数
 */
data class Endpoint(
    val path: String,
    val method: String = "POST",
    val baseUrl: String = Constants.API_BASE,
    val sign: String = "default",
    val required: List<String> = emptyList(),
    val defaults: Map<String, String> = emptyMap(),
    val skipDevice: Boolean = false,
    val excludeDynamic: List<String> = emptyList(),
)

object Endpoints {

    val MAP: Map<String, Endpoint> = linkedMapOf(
        // ---------------- 账号 ----------------
        "LOGIN" to Endpoint(
            path = "STAccountLogin3",
            required = listOf("phone_number", "password", "verify_type"),
            defaults = mapOf("verify_type" to "1"),   // 1 = 密码登录
        ),
        "GET_ST_PARAMS" to Endpoint(path = "GetSTParams"),
        "GET_MY_DATA" to Endpoint(
            path = "GetSTMyData5",
            required = listOf("all_black_member"),
            defaults = mapOf("all_black_member" to "1"),
        ),
        "GET_USER_DATA" to Endpoint(path = "GetSTUserData", required = listOf("home_id")),
        "GET_LINKMAN_LIST" to Endpoint(
            path = "GetSTLinkmanList",
            required = listOf("updated_at", "page"),
            defaults = mapOf("updated_at" to "0", "page" to "0"),
        ),
        // type=1 互关（私信会话列表用）、2 我关注的、3 粉丝
        // 一页 10 条，返回的 over=true 表示后面没有了 —— 关注/粉丝列表要翻页取全
        "GET_BUDDIES" to Endpoint(
            path = "GetSTBuddies",
            required = listOf("type", "page"),
            defaults = mapOf("type" to "1", "page" to "0"),
        ),
        "GET_BLACK_LIST" to Endpoint(path = "GetSTBlackList"),
        "GET_NOTICES2" to Endpoint(
            path = "GetSTNotices2",
            required = listOf("type", "page"),
            defaults = mapOf("type" to "2", "page" to "0"),
        ),
        // ---------------- 私信（信件） ----------------
        // flag=2 首次打开（附带对方 member 资料），flag=1 翻页取更早消息
        "GET_LETTER_MESSAGE" to Endpoint(
            path = "GetSTLetterMessage",
            required = listOf("my_mid", "other_mid", "letter_id", "flag", "page"),
            defaults = mapOf("letter_id" to "0", "flag" to "2", "page" to "0"),
        ),
        // 发私信：comment 签名变体（与 GetSearchNotes 同款）
        "SEND_LETTER_MESSAGE" to Endpoint(
            path = "SendSTLetterMessage",
            sign = "comment",
            required = listOf("type", "my_mid", "other_mid", "content"),
            defaults = mapOf("type" to "1"),
        ),
        "DEL_LETTER_MESSAGES" to Endpoint(
            path = "DelSTMessages",
            required = listOf("id"),
        ),

        // ---------------- 签到 ----------------
        "ADD_COIN" to Endpoint(
            path = "AddSTCoin",
            required = listOf("coin", "day"),
            defaults = mapOf("coin" to "1", "day" to "1"),
        ),

        // ---------------- 投币（硬币 → 文章） ----------------
        // 2026-09-25 实测：通用签名，参数 nid（文章 id）+ count（投出的币数）。
        // 成功 → {"res":0}；同时账号 coin_count 减 count、该文章的 coins 字段 +count。
        // 缺参时服务端会依次回 "count field missing" / "nid field missing"。
        "COIN_TO_NOTE" to Endpoint(
            path = "STCoin2Note",
            required = listOf("nid", "count"),
            defaults = mapOf("count" to "1"),
        ),

        // ---------------- 社区 ----------------
        "GET_DISCOVER_TAG_NOTES2" to Endpoint(
            path = "GetDiscoverTagNotes2",
            required = listOf("type", "start_time", "page"),
            defaults = mapOf("start_time" to "0", "page" to "0"),
        ),
        "GET_DISCOVER_NOTES3" to Endpoint(
            path = "GetDiscoverNotes3",
            required = listOf("type", "page"),
            defaults = mapOf("page" to "0"),
        ),
        "GET_FOLLOW_USER_NOTES2" to Endpoint(
            path = "GetFollowUserNotes2",
            required = listOf("page"),
            defaults = mapOf("page" to "0"),
        ),
        "GET_USER_NOTES2" to Endpoint(
            path = "GetSTUserNotes2",
            required = listOf("type", "home_id", "page"),
            defaults = mapOf("type" to "0", "page" to "0"),
        ),
        // 注意：搜索用的是 comment 签名变体，返回字段是 matches（不是 notes）
        "SEARCH_NOTES" to Endpoint(
            path = "GetSearchNotes",
            sign = "comment",
            required = listOf("keyword", "page", "ct"),
            defaults = mapOf("page" to "0", "ct" to "20"),
        ),

        // ---------------- H5 文章详情 ----------------
        "GET_NOTE" to Endpoint(
            path = "/shuati/discoverNoteDetail-v5",
            method = "GET",
            baseUrl = Constants.WEB_BASE,
            sign = "note",
            required = listOf("id"),
            skipDevice = true,
            excludeDynamic = listOf("openid"),
            defaults = mapOf(
                "adolescent_model" to "0",
                "font_size" to "2",
                "version" to "2",
            ),
        ),

        // ---------------- 评论 ----------------
        "GET_COMMENT_BY_NID2" to Endpoint(
            path = "GetSTCommentByNid2",
            required = listOf("nid", "order_type", "start_time", "ct", "page"),
            defaults = mapOf("order_type" to "1", "ct" to "10", "page" to "0"),
        ),
        "GET_COMMENT_BY_FID2" to Endpoint(
            path = "GetSTCommentByFid2",
            required = listOf("fid", "object_type", "start_time", "ct", "page"),
            defaults = mapOf("object_type" to "2", "ct" to "20", "page" to "0"),
        ),
        "SET_NOTE_COMMENT1" to Endpoint(
            path = "SetSTNoteComment1",
            sign = "comment",
            required = listOf("content", "fid", "nid"),
            defaults = mapOf("fid" to "0"),
        ),
        "DEL_NOTE_COMMENT" to Endpoint(path = "DelSTNoteComment", required = listOf("id")),
        "SET_NOTE_COMMENT_LIKE" to Endpoint(path = "SetSTNoteCommentLike", required = listOf("id")),
        // 关注 / 取消关注：status=1 关注、status=2 取消关注（实测）
        "FOLLOW_USER" to Endpoint(
            path = "STFollow",
            required = listOf("home_id", "id", "status"),
            defaults = mapOf("status" to "1"),
        ),

        // ---------------- 点赞 ----------------
        "DISCOVER_NOTE_LIKE" to Endpoint(
            path = "DiscoverNoteLike",
            required = listOf("nid", "status"),
            defaults = mapOf("status" to "1"),
        ),
        "GET_LIKE_SHUATI_NOTE" to Endpoint(
            path = "GetLikeShuatiNote",
            required = listOf("page"),
            defaults = mapOf("page" to "0"),
        ),

        // ---------------- 收藏 ----------------
        // 我收藏的笔记列表（返回 notes[]）
        "GET_COLLECTION_NOTES" to Endpoint(
            path = "GetCollectionShuatiNote1",
            required = listOf("page"),
            defaults = mapOf("page" to "0"),
        ),
        // 收藏 / 取消收藏：version + scene + status + object_id + object_type
        // status=1 收藏，status=0 取消；object_type=2 笔记
        "UPDATE_COLLECTION" to Endpoint(
            path = "UpdateSTCollection",
            required = listOf("status", "object_id"),
            defaults = mapOf(
                "version" to "1",
                "scene" to "1",
                "object_type" to "2",
            ),
        ),

        // ---------------- 关注 ----------------
        // 设置关注备注（mark 为备注名，id 为关系 id，来自 GetSTBuddies 的 id 字段）
        "SET_FOLLOW_REMARK" to Endpoint(
            path = "SetSTFollowMark",
            sign = "comment",
            required = listOf("home_id", "id", "mark"),
        ),

        // ---------------- 图片上传（OSS，multipart） ----------------
        // dir_name 是服务端白名单目录；文件字段名固定为 file。
        // 走 multipart，所以不在这里拼 urlencoded body，由 FkstClient.uploadImage 处理。
        "UPLOAD_IMAGE" to Endpoint(
            path = "OSSUploadImage4.php",
            required = listOf("dir_name"),
            defaults = mapOf("dir_name" to Constants.IMAGE_DIR_NOTE),
        ),

        // ---------------- 文件上传（OSS，multipart） ----------------
        // 与图片同款：通用签名 + dir_name + file。
        // 实测可传 pdf / txt；zip 被挡（「不允许的文件类型!」）；
        // stupletter 目录不收文件（「非法路径」），统一用 stupnotefile。
        "UPLOAD_FILE" to Endpoint(
            path = "OSSUploadFile2.php",
            required = listOf("dir_name"),
            defaults = mapOf("dir_name" to Constants.FILE_DIR_NOTE),
        ),

        // ---------------- 发布笔记 ----------------
        // 2026-09-25 实测：**comment 签名变体**（不是通用签名！用通用签名只会回「非法请求2」）。
        // 必需参数只有 title 与 urls，其余（content / type / tagids…）服务端都有默认值。
        //   urls   —— 图片地址的 JSON 数组字符串，第一张会成为封面（logo 字段）
        //   content—— 官方客户端那套 JSON 包（version / text / sw_content …）
        //   type   —— 分区 type，见 Constants.CATEGORIES
        // 成功 → {"res":0,"id":"<新笔记 id>"}
        // 频率限制：两贴间隔至少 5 分钟，否则 res=2。
        "PUBLISH_NOTE" to Endpoint(
            path = "UploadNote2",
            sign = "comment",
            required = listOf("title"),
            defaults = mapOf("type" to "10"),
        ),
        // 删除自己发的笔记：DeleteShuatiNote + nid（通用签名，实测 res=0）
        "DELETE_NOTE" to Endpoint(
            path = "DeleteShuatiNote",
            required = listOf("nid"),
        ),
    )
}
