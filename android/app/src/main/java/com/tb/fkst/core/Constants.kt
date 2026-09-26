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

    // ---------------------------------------------------------------- 试卷库
    //
    // 2026-09-26 实测（v1.9.0 新增的「试卷」板块，取代做不了的官方 H5 刷题）：
    //
    //   GetShuatiPaper5           试卷列表。**只有 f_gradeid（年级）和 version_id
    //                             （教材版本，须与 f_gradeid 一起传）真正生效**；
    //                             subject / xd / papertype 单独传会被服务端直接忽略。
    //                             ⚠️ 响应里**没有 res 字段**，调用时要 expectRes=false。
    //   GetZJPaperById5           试卷详情：pid + type + aid + paperid，返回
    //                             paper.questionlist[]，每组 {qtype, question[]}，
    //                             题目带 question_text / answer_text / explanation_text。
    //                             ⚠️ 只对 **type=1（同步卷）** 有效，其它 type 回 res=1；
    //                             不存在的 id 回 questionlist=null —— 真有校验。
    //                             （名称里的 ZJ 不是「组卷」的拼音缩写误猜，官方端
    //                              SimplePaperFragment 之外的详情页就是走它。）
    //   CollectShuatiPaper        收藏试卷：status + type + pid。
    //                             ⚠️ 它**恒回 res=0** —— 对不存在的 pid 也是 0，
    //                             所以无法从返回值判断成败，只能乐观更新本地收藏状态。
    //                             首次实测可逆（收藏 → 收藏列表出现；取消 → 消失），
    //                             但当天晚些时候复测出现过「返回 0、列表里查不到」的情况
    //                             （同一时段笔记收藏列表、赞过列表也一起 res=1，
    //                             怀疑是服务端侧抖动 / 高频调用被限），所以别把收藏当可靠存储。
    //   GetCollectionShuatiPaper5 我收藏的试卷：type 有白名单（0/1/2/12 有效，22 回 res=1）。
    //   GetSTFilterData           教材版本列表：filter=1 + subject + f_gradeid。
    //   GetSearchPapers7          搜试卷：**comment 签名变体**，返回 matches[]（pid/type/logo）。
    //                             搜出来的卷子 type 多是 22/14/1111…，这些**看不了题**。
    //
    // 学段 xd：1 小学 / 2 初中 / 3 高中。年级映射是**实测校准**的：
    // 拿每个 f_gradeid 拉一页，按返回的卷子标题反推（例如 f_gradeid=7 全是「四年级」）。
    // subject 是这批卷子自带的学科码（3 数学 / 4 英语 / 7 化学），拉版本列表时要带上。
    // 表里刻意只留校准过的项 —— 像 f_gradeid=17/25 这种返回的卷子年级对不上的，没放进来。

    /** 试卷库筛选用的年级项 */
    data class PaperGrade(
        val id: String,
        val label: String,
        val xd: String,
        val subject: String,
    )

    val PAPER_GRADES: List<PaperGrade> = listOf(
        PaperGrade("", "最新", "", "4"),
        PaperGrade("1", "一年级", "1", "4"),
        PaperGrade("3", "二年级", "1", "4"),
        PaperGrade("5", "三年级", "1", "4"),
        PaperGrade("7", "四年级", "1", "4"),
        PaperGrade("9", "五年级", "1", "4"),
        PaperGrade("11", "六年级", "1", "4"),
        PaperGrade("13", "七年级·数学", "2", "3"),
        PaperGrade("15", "八年级·数学", "2", "3"),
        PaperGrade("19", "八年级·英语", "2", "4"),
        PaperGrade("21", "九年级·化学", "2", "7"),
        PaperGrade("23", "高中·数学", "3", "3"),
    )

    /** 收藏列表的 type 白名单：同步卷 1，其余是其它模块的卷子 */
    const val PAPER_COLLECT_TYPE = "1"

    // ---------------------------------------------------------------- 图片上传
    //
    // 2026-09-25 实测 OSSUploadImage4.php：
    //   POST multipart，通用签名，字段 dir_name（目录）+ file（文件）
    //   成功 → {"res":0,"illegal":false,"url":"http://imgcdn.yaerxing.com/upimage/<dir>/…"}
    //   illegal=true 表示内容审核没过；dir_name 白名单外的目录回「未创建文件夹」。

    /** 笔记配图目录 */
    const val IMAGE_DIR_NOTE = "stupnote"

    /** 一条笔记最多几张配图（发布 / 编辑共用同一个上限） */
    const val NOTE_IMAGE_MAX = 9

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

    // ---------------------------------------------------------------- 音频上传
    //
    // 2026-09-25 实测 OSSUploadAudio2.php：
    //   POST multipart，通用签名，文件字段名固定为 file，**不需要 dir_name**（传了会被忽略）
    //   **只收 .mp3**：传 wav / m4a / 改了扩展名的 wav 一律回 {"res":1,"error":"upload audio failed"}
    //   成功 → {"res":0,"url":"http://imgcdn.yaerxing.com/audio/2026/09/25/<随机>.mp3"}
    //
    // 笔记正文本身没有音频字段（扫过 363 篇社区笔记，零音频痕迹），
    // 所以音频是**本客户端自己的扩展**：把地址以 `[音频] <url>` 的形式写进正文，
    // 由 NoteDetailScreen 渲染成播放器；官方客户端只会显示成一行普通文字。

    /** 正文里引用音频的标记前缀 */
    const val AUDIO_MARK = "[音频]"

    /** 单段音频上限 */
    const val AUDIO_MAX_BYTES = 20 * 1024 * 1024

    /** 一篇笔记最多挂几段音频 */
    const val AUDIO_MAX_COUNT = 3

    /** 从正文里抠出音频地址：`[音频] http://…mp3` */
    val AUDIO_LINE_RE = Regex(
        """^\[音频]\s*(\S+?\.mp3)(\?\S*)?$""",
        RegexOption.IGNORE_CASE,
    )

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

    // ---------------------------------------------------------------- 缓存
    //
    // 清理缓存时不会碰这些「用户数据」：备注 / 置顶 / 投币记录 / 壁纸 / 防撤回备份
    // （壁纸和防撤回备份另外给了单独的清除入口）。
    const val AUTHOR_CACHE_LIMIT = 400

    // ---------------------------------------------------------------- 更新检查
    //
    // 版本号从 GitHub Releases 读：走 `/releases`（列表，按发布时间倒序），
    // **不用** `/releases/latest` —— 后者会排除预发布，测试版就查不到了。
    // 换仓库只改下面 owner / repo 两行，URL 都是拼出来的。
    //
    // ⚠️ 已知坑：早期 Release 的 tag 叫 `qwq` / `awa`，解析不出 v?x.y.z 时
    // 客户端会**静默跳过**，不会误报。发版把 tag 起成 `v1.9.0` 这种规范名，
    // 弹窗和「跳过此版本」才有的比。

    /** GitHub 账号 */
    const val GITHUB_OWNER = "TB-SYS-ASD"

    /** 仓库名 */
    const val GITHUB_REPO = "FkstClient"

    /**
     * releases 列表接口（JSON，按发布时间倒序）。
     *
     * ⚠️ 不用 `releases/latest`：那个接口会**排除预发布**，而 v1.9.0 是标了
     * 「测试版（pre-release）」发的，用 latest 会直接查不到，更新检查等于失效。
     * 列表接口草稿和预发布都返回，客户端自己挑第一个版本号能解析的。
     */
    const val GITHUB_RELEASES_API =
        "https://api.github.com/repos/TB-SYS-ASD/FkstClient/releases?per_page=10"

    /** 浏览器打开的发布页 */
    const val GITHUB_RELEASES_PAGE =
        "https://github.com/TB-SYS-ASD/FkstClient/releases"

    /** 检查更新的超时（GitHub 在部分网络下会抽风，别等太久） */
    const val UPDATE_TIMEOUT_MS = 12_000
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
        //
        // ⚠️ 2026-09-26 实测：`GetSTNotices2` 对这个账号**恒返回空数组**，
        // 而老的 `GetSTNotices`（不带 2）能正常返回通知（type=1 有 6 条）。
        // 所以这里走 v1。字段：id / content / type / sub_type / object_id /
        // is_read / created_at / logo（评论、点赞这类还会有 nick_name、home_id）。
        //
        // type 语义（官方端 tab 是「评论 / 点赞 / 全部 / 系统消息」，
        // 跟 GetSTMyData5 的 messages[{type:1..4}] 对得上）：
        //   0 = 全部（实测传 0 返回所有类型）  1 = 系统消息（已验证：全是系统文案）
        //   2 = 评论    3 = 点赞    4 = 投币/其它（这两个是推断值，见 Notice.kind 的注释）
        "GET_NOTICES" to Endpoint(
            path = "GetSTNotices",
            required = listOf("type", "page"),
            defaults = mapOf("type" to "0", "page" to "0"),
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
        // 评论点赞：实测少了 status 会报 "status field missing"，必须有
        "SET_NOTE_COMMENT_LIKE" to Endpoint(
            path = "SetSTNoteCommentLike",
            required = listOf("id", "status"),
            defaults = mapOf("status" to "1"),
        ),
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
        // 收藏 / 取消收藏：**nid + status**（status=1 收藏，status=0 取消）
        //
        // 2026-09-25 实测：这条是从官方端 dex 里挖出来的真接口。
        // 之前用的 UpdateSTCollection 是个坑 —— 它同样返回 {"res":0}，
        // 但收藏完再去查 GetCollectionShuatiNote1，**列表里根本没有这条**，
        // 也就是「假成功、不落库」。CollectShuatiNote 才是真正生效的那个：
        //   收藏 nid=5146280 → 收藏列表出现该 id；取消 → 从列表消失（可逆验证过）
        "SET_NOTE_COLLECTION" to Endpoint(
            path = "CollectShuatiNote",
            required = listOf("status", "nid"),
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

        // ---------------- 音频上传（OSS，multipart） ----------------
        // 通用签名 + 文件字段 file，**不需要 dir_name**。
        // 只认 .mp3，其它格式一律 "upload audio failed"。
        // 成功 → {"res":0,"url":"http://imgcdn.yaerxing.com/audio/2026/09/25/<随机>.mp3"}
        "UPLOAD_AUDIO" to Endpoint(path = "OSSUploadAudio2.php"),

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

        // ---------------- 编辑已发笔记的配图 ----------------
        // 2026-09-25 从官方端 dex 挖出来并实测：id + urls（图片地址 JSON 数组，同发布格式）
        //
        // 可信度判据：拿**不存在的 id** 去调，服务端回 {"res":2} —— 说明它真的去
        // 查笔记了，不是无脑成功。作为对比：
        //   UpdateUploadNoteStatus（id+score+status）对不存在的 id 也回 res=0，
        //   这种「怎么都成功」的接口跟之前踩过的 UpdateSTCollection 一个德性，
        //   score/status 的取值语义也没有任何线索，所以**没有做进客户端**。
        //
        // 反过来，标题和正文**改不了**：翻遍 dex 只有 UpdateUploadNoteUrls /
        // UpdateUploadNoteStatus / UpdateNoteTag 三个，UpdateNoteTag 实测回
        // 「非本区管理员」（要管理员权限），没有改标题正文的接口。
        "UPDATE_NOTE_URLS" to Endpoint(
            path = "UpdateUploadNoteUrls",
            required = listOf("id", "urls"),
        ),

        // ---------------- 试卷库 ----------------
        // 细节见 Constants 里「试卷库」那段注释。共用的坑先写在最前面：
        //   **GetShuatiPaper5 / GetZJPaperById5 / GetSearchPapers7 的响应里没有 res 字段**，
        //   调用时必须 expectRes=false，否则会被当成失败抛异常。
        //
        // 列表：page 分页，f_gradeid + version_id 筛选（version_id 要搭配 f_gradeid）。
        "GET_PAPERS" to Endpoint(
            path = "GetShuatiPaper5",
            defaults = mapOf("page" to "0"),
        ),
        // 详情：pid / paperid 都填试卷 id，type 填试卷自带的 type（只有 1 有题目），aid 固定 0
        "GET_PAPER_DETAIL" to Endpoint(
            path = "GetZJPaperById5",
            required = listOf("pid", "type"),
            defaults = mapOf("aid" to "0"),
        ),
        // 教材版本列表：filter=1 固定，subject + f_gradeid 决定返回哪一套
        "GET_PAPER_VERSIONS" to Endpoint(
            path = "GetSTFilterData",
            required = listOf("subject", "f_gradeid"),
            defaults = mapOf("filter" to "1"),
        ),
        // 收藏 / 取消收藏试卷：status=1 收藏、0 取消，type 用试卷的 type
        "COLLECT_PAPER" to Endpoint(
            path = "CollectShuatiPaper",
            required = listOf("status", "type", "pid"),
        ),
        // 我收藏的试卷（type 白名单：0 / 1 / 2 / 12）
        "GET_PAPER_COLLECTIONS" to Endpoint(
            path = "GetCollectionShuatiPaper5",
            defaults = mapOf("type" to Constants.PAPER_COLLECT_TYPE, "page" to "0"),
        ),
        // 搜试卷：comment 签名变体，必需 keyword + page + ct
        "SEARCH_PAPERS" to Endpoint(
            path = "GetSearchPapers7",
            sign = "comment",
            required = listOf("keyword", "page"),
            defaults = mapOf("page" to "0", "ct" to "20"),
        ),

        // ---------------- 答题记录 ----------------
        // GetRecordShuatiQuestion1：返回 questions[]（题干 + 选项），force 签名。
        // 实测 res=0；over/isend 恒 False（记录可能 200+ 条），翻页靠短页判定（<10 条停）。
        "GET_RECORD_SHUATI_QUESTION" to Endpoint(
            path = "GetRecordShuatiQuestion1",
            required = listOf("page"),
            defaults = mapOf("page" to "0"),
        ),
    )
}
