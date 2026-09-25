package com.tb.fkst.data

import com.tb.fkst.core.Constants
import com.tb.fkst.core.Crypto
import com.tb.fkst.core.FkstClient
import com.tb.fkst.core.boolOr
import com.tb.fkst.core.intOr
import com.tb.fkst.core.str
import org.json.JSONObject
import kotlin.random.Random

/**
 * 业务封装：把接口调用整理成好用的挂起函数。
 * 与项目内 Python 版 `fkst_sdk/services.py` 一一对应。
 */
object Api {

    // ------------------------------------------------------------ 账号

    suspend fun myStats(client: FkstClient): MyStats =
        MyStats.from(client.request("GET_MY_DATA", mapOf("all_black_member" to "1")))

    suspend fun profile(client: FkstClient, homeId: String): UserProfile =
        UserProfile.from(homeId, client.request("GET_USER_DATA", mapOf("home_id" to homeId)))

    suspend fun notices(client: FkstClient, type: String = "2", page: Int = 0): Paged<Notice> {
        val r = client.request("GET_NOTICES2", mapOf("type" to type, "page" to page.toString()))
        return Paged(Notice.list(r), !r.boolOr("over"))
    }

    // ------------------------------------------------------------ 内容

    suspend fun discover(
        client: FkstClient,
        type: String,
        page: Int,
        startTime: Long = 0L,
        extra: Map<String, String> = emptyMap(),
    ): Paged<Note> {
        val p = LinkedHashMap<String, String>()
        p["type"] = type
        p["page"] = page.toString()
        p["start_time"] = startTime.toString()
        p.putAll(extra)
        val r = client.request("GET_DISCOVER_TAG_NOTES2", p)
        return Paged(Note.list(r), !r.boolOr("over"))
    }

    suspend fun follow(client: FkstClient, page: Int): Paged<Note> {
        val r = client.request("GET_FOLLOW_USER_NOTES2", mapOf("page" to page.toString()))
        return Paged(Note.list(r), !r.boolOr("over"))
    }

    suspend fun userNotes(
        client: FkstClient,
        homeId: String,
        type: String = Constants.USER_NOTE_ALL,
        page: Int = 0,
    ): Paged<Note> {
        val r = client.request(
            "GET_USER_NOTES2",
            mapOf("home_id" to homeId, "type" to type, "page" to page.toString())
        )
        return Paged(Note.list(r), !r.boolOr("over"))
    }

    /**
     * 「我赞过的」。
     *
     * ⚠️ 这个接口只返回 `id / title / type / thumb / tagids / home_id / link_id` ——
     * **没有 `nick_name` / `logo`**，所以列表里作者要靠 home_id 另外补（见 AppViewModel.resolveAuthors）。
     */
    suspend fun likedNotes(client: FkstClient, page: Int = 0): Paged<Note> {
        val r = client.request("GET_LIKE_SHUATI_NOTE", mapOf("page" to page.toString()))
        return Paged(Note.list(r), !r.boolOr("over"))
    }

    suspend fun search(client: FkstClient, keyword: String, page: Int = 0): Paged<SearchItem> {
        val r = client.request(
            "SEARCH_NOTES",
            mapOf("keyword" to keyword, "page" to page.toString(), "ct" to "20")
        )
        return Paged(SearchItem.list(r), !r.boolOr("over"))
    }

    /** 文章正文（H5，密文）→ 解密成明文 */
    suspend fun noteDetail(client: FkstClient, noteId: String): NoteDetail {
        val secretKey = Random.nextInt(100_000, 2_000_000)
        val p = LinkedHashMap<String, String>()
        p.putAll(Constants.GET_NOTE_PARAMS)
        p["secret_key"] = secretKey.toString()
        p["timestamp"] = System.currentTimeMillis().toString()
        p["id"] = noteId

        val r = client.request("GET_NOTE", p)
        val cipher = r.str("content")
        val content = Crypto.decryptContent(cipher, secretKey)
        return NoteDetail(noteId = noteId, content = content, plain = Crypto.stripTags(content))
    }

    // ------------------------------------------------------------ 评论

    suspend fun comments(
        client: FkstClient,
        noteId: String,
        page: Int = 0,
        orderType: String = "1",
        ct: Int = 10,
    ): Paged<Comment> {
        val r = client.request(
            "GET_COMMENT_BY_NID2",
            mapOf(
                "nid" to noteId,
                "page" to page.toString(),
                "order_type" to orderType,
                "ct" to ct.toString(),
                "start_time" to (System.currentTimeMillis() / 1000).toString(),
            )
        )
        return Paged(Comment.list(r), !r.boolOr("over"))
    }

    suspend fun replies(client: FkstClient, commentId: String, page: Int = 0): List<Reply> {
        val r = client.request(
            "GET_COMMENT_BY_FID2",
            mapOf(
                "fid" to commentId,
                "page" to page.toString(),
                "object_type" to "2",
                "ct" to "20",
                "start_time" to (System.currentTimeMillis() / 1000).toString(),
            )
        )
        return Comment.list(r).map { c ->
            Reply(
                id = c.id, content = c.content, relayContent = "",
                createdAt = c.createdAt, fid = commentId,
                nickName = c.nickName, avatar = c.avatar, homeId = c.homeId,
            )
        }
    }

    /** 发评论；parentCommentId = "0" 表示顶层评论 */
    suspend fun postComment(
        client: FkstClient,
        content: String,
        noteId: String,
        parentCommentId: String = "0",
    ): JSONObject {
        val text = content.trim()
        require(text.isNotEmpty()) { "评论内容不能为空" }
        return client.request(
            "SET_NOTE_COMMENT1",
            mapOf("content" to text, "nid" to noteId, "fid" to parentCommentId)
        )
    }

    suspend fun deleteComment(client: FkstClient, commentId: String): JSONObject =
        client.request("DEL_NOTE_COMMENT", mapOf("id" to commentId))

    // ------------------------------------------------------------ 点赞 / 关注

    suspend fun likeNote(client: FkstClient, noteId: String, like: Boolean = true): JSONObject =
        client.request(
            "DISCOVER_NOTE_LIKE",
            mapOf("nid" to noteId, "status" to if (like) "1" else "0")
        )

    suspend fun likeComment(client: FkstClient, commentId: String): JSONObject =
        client.request("SET_NOTE_COMMENT_LIKE", mapOf("id" to commentId))

    /** 关注 / 取消关注（STFollow：status=1 关注、status=2 取消关注） */
    suspend fun followUser(client: FkstClient, homeId: String, follow: Boolean = true): JSONObject =
        client.request(
            "FOLLOW_USER",
            mapOf(
                "home_id" to homeId,
                "id" to homeId,
                "status" to if (follow) "1" else "2",
            )
        )

    /** 设置关注备注（需要 GetSTBuddies 返回的关系 id） */
    suspend fun followRemark(client: FkstClient, homeId: String, relationId: String, mark: String): JSONObject =
        client.request(
            "SET_FOLLOW_REMARK",
            mapOf("home_id" to homeId, "id" to relationId, "mark" to mark)
        )

    // ------------------------------------------------------------ 收藏

    /** 我收藏的笔记 */
    suspend fun collectionNotes(client: FkstClient, page: Int = 0): Paged<Note> {
        val r = client.request("GET_COLLECTION_NOTES", mapOf("page" to page.toString()))
        return Paged(Note.list(r), !r.boolOr("over"))
    }

    /** 收藏 / 取消收藏一条笔记 */
    suspend fun setCollection(client: FkstClient, noteId: String, on: Boolean): JSONObject =
        client.request(
            "UPDATE_COLLECTION",
            mapOf(
                "status" to if (on) "1" else "0",
                "object_id" to noteId,
            )
        )

    // ------------------------------------------------------------ 关注 / 粉丝

    /**
     * 关注 / 粉丝列表。
     *
     * type=1 互关、2 我关注的、3 粉丝。一页 10 条，`over=true` 表示取完了 ——
     * 早先只拉 page=0，所以关注了 40 个人也只看得到 10 个（「显示不全」就是这个原因）。
     */
    suspend fun buddies(client: FkstClient, type: String = "1", page: Int = 0): Paged<Buddy> {
        val r = client.request(
            "GET_BUDDIES",
            mapOf("type" to type, "page" to page.toString())
        )
        return Paged(Buddy.list(r), !r.boolOr("over"))
    }

    /** 一次取全（用于私信会话列表这种要拿全部互关好友的场景） */
    suspend fun allBuddies(client: FkstClient, type: String = "1", maxPages: Int = 20): List<Buddy> {
        val out = mutableListOf<Buddy>()
        var page = 0
        while (page < maxPages) {
            val p = buddies(client, type, page)
            out += p.items
            if (!p.hasMore || p.items.isEmpty()) break
            page++
        }
        return out
    }

    /**
     * 拉取与某个用户的私信记录。
     * flag=2 首次打开（附带对方资料），flag=1 翻页取更早的消息。
     */
    suspend fun letters(
        client: FkstClient,
        otherMid: String,
        letterId: String = "0",
        flag: String = "2",
    ): LetterThread {
        val r = client.request(
            "GET_LETTER_MESSAGE",
            mapOf(
                "my_mid" to client.mid,
                "other_mid" to otherMid,
                "letter_id" to letterId,
                "flag" to flag,
                "page" to "0",
            )
        )
        val member = r.optJSONObject("member")?.let { runCatching { Buddy.fromMember(it) }.getOrNull() }
        return LetterThread(
            member = member,
            letters = Letter.list(r, selfMid = client.mid, peerMid = otherMid),
            raw = r.toString(),
        )
    }

    /** 发送私信。签名用 comment 变体（与发评论同款）。 */
    suspend fun sendLetter(
        client: FkstClient,
        otherMid: String,
        content: String,
    ): JSONObject {
        val text = content.trim()
        require(text.isNotEmpty()) { "私信内容不能为空" }
        return client.request(
            "SEND_LETTER_MESSAGE",
            mapOf(
                "type" to Constants.LETTER_TYPE_TEXT,
                "my_mid" to client.mid,
                "other_mid" to otherMid,
                "content" to text,
            )
        )
    }

    suspend fun deleteLetter(client: FkstClient, letterId: String): JSONObject =
        client.request("DEL_LETTER_MESSAGES", mapOf("id" to letterId))

    /**
     * 上传图片，返回可用的图片地址（已转成原图形态）。
     *
     * 私信图片目录 `stupletter` 是**会员功能**（非会员回「请开通会员」），
     * 所以这里做一层回落：先试 `stupletter`，被拒就退到 `stupnote`，
     * 保证普通账号也能把图片发出去。
     */
    suspend fun uploadImage(
        client: FkstClient,
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg",
        dirName: String = Constants.IMAGE_DIR_LETTER,
        allowFallback: Boolean = true,
    ): UploadedImage {
        val first = UploadedImage.from(client.uploadImage(bytes, fileName, mimeType, dirName))
        if (first.ok || !allowFallback) return first
        if (first.needVip) {
            val fallback = UploadedImage.from(
                client.uploadImage(bytes, fileName, mimeType, Constants.IMAGE_DIR_NOTE_FALLBACK)
            )
            // 回落成功时保留原错误，方便上层提示「私信图目录需要会员」
            return if (fallback.ok) fallback.copy(error = first.error) else fallback
        }
        return first
    }

    /** 发送图片私信（type=2，content 放图片地址） */
    suspend fun sendLetterImage(
        client: FkstClient,
        otherMid: String,
        imageUrl: String,
    ): JSONObject {
        require(imageUrl.isNotBlank()) { "图片地址为空" }
        return client.request(
            "SEND_LETTER_MESSAGE",
            mapOf(
                "type" to Constants.LETTER_TYPE_IMAGE,
                "my_mid" to client.mid,
                "other_mid" to otherMid,
                "content" to imageUrl,
            ),
            expectRes = false,
        )
    }

    /**
     * 上传一个普通文件（PDF / Word / txt …）。
     *
     * 实测（2026-09-25）走 `OSSUploadFile2.php`：
     * - 只有 `stupnotefile` 目录收文件（`stupletter` 回「非法路径」）
     * - 服务端按扩展名做黑名单，zip 会被拒（「不允许的文件类型!」）
     * - 成功 → `{"res":0,"url":"http://imgcdn.yaerxing.com/upfile/…"}`
     */
    suspend fun uploadFile(
        client: FkstClient,
        bytes: ByteArray,
        fileName: String,
        mimeType: String = "application/octet-stream",
        dirName: String = Constants.FILE_DIR_NOTE,
    ): UploadedFile =
        UploadedFile.from(client.uploadFile(bytes, fileName, mimeType, dirName))

    /**
     * 发送文件私信。
     *
     * 因为平台的「文件消息」类型没能在真机上验证过（私信要连续签到满 3 天），
     * 这里做两条腿走路：
     *   1. 先按 `type=3` + `content=文件地址` 发；
     *   2. 服务端不认（res != 0）就退化成**文本消息 + 链接**，保证对方一定收得到。
     * 返回 (是否成功, 是不是退化成了链接, 服务端原始响应)
     */
    suspend fun sendLetterFile(
        client: FkstClient,
        otherMid: String,
        fileUrl: String,
        fileName: String = "",
    ): Triple<Boolean, Boolean, JSONObject> {
        require(fileUrl.isNotBlank()) { "文件地址为空" }

        val typed = client.request(
            "SEND_LETTER_MESSAGE",
            mapOf(
                "type" to Constants.LETTER_TYPE_FILE,
                "my_mid" to client.mid,
                "other_mid" to otherMid,
                "content" to fileUrl,
                "file_name" to fileName,
            ),
            expectRes = false,
        )
        if (typed.intOr("res", -1) == 0) return Triple(true, false, typed)

        // 退化：文本消息里带上文件链接
        val link = buildString {
            append("[文件] ")
            if (fileName.isNotBlank()) append(fileName).append('\n')
            append(fileUrl)
        }
        val fell = client.request(
            "SEND_LETTER_MESSAGE",
            mapOf(
                "type" to Constants.LETTER_TYPE_TEXT,
                "my_mid" to client.mid,
                "other_mid" to otherMid,
                "content" to link,
            ),
            expectRes = false,
        )
        return Triple(fell.intOr("res", -1) == 0, true, if (fell.intOr("res", -1) == 0) fell else typed)
    }

    // ------------------------------------------------------------ 投币

    /**
     * 给一篇文章投币（硬币 → 文章）。
     *
     * 实测（2026-09-25）：`STCoin2Note` + `nid` + `count`，通用签名。
     * 成功回 `{"res":0}`，账号 `coin_count` 减少 `count`，文章的 `coins` 字段增加 `count`。
     */
    suspend fun coinToNote(client: FkstClient, noteId: String, count: Int = 1): JSONObject {
        val n = count.coerceAtLeast(1)
        return client.request(
            "COIN_TO_NOTE",
            mapOf("nid" to noteId, "count" to n.toString()),
            expectRes = false,
        )
    }

    // ------------------------------------------------------------ 发布笔记

    /**
     * 发布一篇社区笔记。
     *
     * 实测（2026-09-25）走 `UploadNote2`，注意用的是 **comment 签名变体** ——
     * 换通用签名只会回「非法请求2」。必需参数只有 `title` 和 `urls`：
     *
     * - `urls`    ：已经上传好的图片地址，JSON 数组字符串；第一张会成为封面（`logo`）
     * - `title`   ：标题
     * - `content` ：官方那套包体 JSON（见 [buildNoteContent]）
     * - `type`    ：分区 type（见 [Constants.CATEGORIES]）
     *
     * 成功 → `{"res":0,"id":"<新笔记 id>"}`。
     * 频率限制：两贴间隔至少 5 分钟，否则 `res=2`（提示文案里会说明）。
     */
    suspend fun publishNote(
        client: FkstClient,
        title: String,
        text: String,
        imageUrls: List<String> = emptyList(),
        type: String = Constants.CATEGORIES.first().type,
    ): JSONObject {
        val t = title.trim()
        require(t.isNotEmpty()) { "标题不能为空" }
        return client.request(
            "PUBLISH_NOTE",
            mapOf(
                "title" to t,
                "content" to buildNoteContent(text),
                "urls" to org.json.JSONArray(imageUrls.filter { it.isNotBlank() }).toString(),
                "type" to type,
            ),
            expectRes = false,
        )
    }

    /** 删除自己发的笔记（`DeleteShuatiNote` + `nid`，通用签名） */
    suspend fun deleteNote(client: FkstClient, noteId: String): JSONObject {
        require(noteId.isNotBlank()) { "笔记 id 为空" }
        return client.request("DELETE_NOTE", mapOf("nid" to noteId), expectRes = false)
    }

    /**
     * 拼官方客户端的正文包体。
     *
     * 服务端 `content` 字段存的其实是这么一坨 JSON：
     * ```json
     * {"version":1,"text":"正文","update_count":0,"up_count":0,
     *  "sw_title":[],"sw_content":[],"sw":[[],[]],"si_urls":[],"si_label":[]}
     * ```
     * 列表页读的时候我们也是从 `text` / `sw_content` 里把纯文本抽出来的
     * （见 `Note.decodeContent`），所以这里按同一个结构拼回去就行。
     */
    fun buildNoteContent(text: String): String = org.json.JSONObject().apply {
        put("version", Constants.NOTE_CONTENT_VERSION)
        put("text", text)
        put("update_count", 0)
        put("up_count", 0)
        put("sw_title", org.json.JSONArray())
        put("sw_content", org.json.JSONArray())
        put("sw", org.json.JSONArray())
        put("si_urls", org.json.JSONArray())
        put("si_label", org.json.JSONArray())
    }.toString()

    // ------------------------------------------------------------ 签到

    /** 金币 / 连续签到状态 */
    suspend fun coinState(client: FkstClient): CoinState =
        CoinState.from(client.request("GET_MY_DATA", mapOf("all_black_member" to "1")))

    /**
     * 每日签到。day 传空则自动取「连续签到天数 + 1」。
     * 重复签到服务端返回 res=2，这里不当作错误抛出。
     */
    suspend fun checkIn(client: FkstClient): JSONObject {
        val state = coinState(client)
        if (state.signedToday) {
            return JSONObject().apply {
                put("res", 2); put("already", true)
                put("days", state.signDays); put("coins", state.coinCount)
            }
        }
        val r = client.request(
            "ADD_COIN",
            mapOf("coin" to "1", "day" to (state.signDays + 1).toString()),
            expectRes = false,
        )
        return r
    }
}
