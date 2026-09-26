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

    /**
     * 消息通知（GetSTNotices）。
     *
     * `type`：0 全部 / 1 系统 / 2 评论 / 3 点赞 / 4 投币（1 已实测，2~4 为推断，见 NoticeKind）。
     *
     * ⚠️ 别再用 `GetSTNotices2`：实测恒返回空数组，客户端之前就是被它坑成「永远没消息」。
     */
    suspend fun notices(client: FkstClient, type: String = "0", page: Int = 0): Paged<Notice> {
        val r = client.request("GET_NOTICES", mapOf("type" to type, "page" to page.toString()))
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
        // 自己发的笔记，服务端存的 `content` 就是 buildNoteContent() 那坨 JSON；
        // 官方笔记则是带模板的 HTML。统一走 Note.readable()，先解包体再剥标签。
        return NoteDetail(
            noteId = noteId,
            content = content,
            plain = Note.readable(content.ifBlank { cipher }),
        )
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
                contentUrl = c.contentUrl,
            )
        }
    }

    /**
     * 发评论；parentCommentId = "0" 表示顶层评论。
     *
     * @param imageUrl 评论配图，走 `content_url`（跟官方端发作业评论同一个字段名）。
     *                 传空串表示纯文字。
     *
     * ⚠️ 这条**没能端到端验证**：测试账号发评论恒回 `res=1`（疑似被限制发言），
     * 参数名是从官方 dex 里 `WorkCommentFragment` 提交 `content_url` 挖出来的，
     * 而笔记评论的返回条目里确实有 `content_url` 这一列。
     */
    suspend fun postComment(
        client: FkstClient,
        content: String,
        noteId: String,
        parentCommentId: String = "0",
        imageUrl: String = "",
    ): JSONObject {
        val text = content.trim()
        require(text.isNotEmpty() || imageUrl.isNotBlank()) { "说点什么，或者配张图" }
        val params = mutableMapOf(
            "content" to text,
            "nid" to noteId,
            "fid" to parentCommentId,
        )
        if (imageUrl.isNotBlank()) params["content_url"] = imageUrl.trim()
        return client.request("SET_NOTE_COMMENT1", params)
    }

    suspend fun deleteComment(client: FkstClient, commentId: String): JSONObject =
        client.request("DEL_NOTE_COMMENT", mapOf("id" to commentId))

    // ------------------------------------------------------------ 点赞 / 关注

    suspend fun likeNote(client: FkstClient, noteId: String, like: Boolean = true): JSONObject =
        client.request(
            "DISCOVER_NOTE_LIKE",
            mapOf("nid" to noteId, "status" to if (like) "1" else "0")
        )

    /** 赞评论。实测少传 status 服务端会报 "status field missing"，所以这里固定带 1 */
    suspend fun likeComment(client: FkstClient, commentId: String): JSONObject =
        client.request("SET_NOTE_COMMENT_LIKE", mapOf("id" to commentId, "status" to "1"))

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

    /**
     * 收藏 / 取消收藏一条笔记。
     *
     * 走 CollectShuatiNote（nid + status），**不是** UpdateSTCollection ——
     * 后者返回 res=0 但收藏列表里查不到，是个假成功的接口，详见 Constants 里的注释。
     */
    suspend fun setCollection(client: FkstClient, noteId: String, on: Boolean): JSONObject =
        client.request(
            "SET_NOTE_COLLECTION",
            mapOf(
                "status" to if (on) "1" else "0",
                "nid" to noteId,
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
        audioUrls: List<String> = emptyList(),
        type: String = Constants.CATEGORIES.first().type,
    ): JSONObject {
        val t = title.trim()
        require(t.isNotEmpty()) { "标题不能为空" }
        return client.request(
            "PUBLISH_NOTE",
            mapOf(
                "title" to t,
                "content" to buildNoteContent(text, audioUrls),
                "urls" to org.json.JSONArray(imageUrls.filter { it.isNotBlank() }).toString(),
                "type" to type,
            ),
            expectRes = false,
        )
    }

    /**
     * 上传一段音频，成功返回可访问的 url；失败抛异常并带上服务端文案。
     *
     * 接口是 `OSSUploadAudio2.php`，**只认 `.mp3`**。
     */
    suspend fun uploadAudio(client: FkstClient, bytes: ByteArray, fileName: String): String {
        val r = client.uploadAudio(bytes, fileName)
        val url = r.str("url")
        if (r.optInt("res", -1) != 0 || url.isBlank()) {
            throw IllegalStateException(
                r.str("error").ifBlank { "音频上传失败（服务端只接受 mp3）" }
            )
        }
        return url
    }

    /** 删除自己发的笔记（`DeleteShuatiNote` + `nid`，通用签名） */
    suspend fun deleteNote(client: FkstClient, noteId: String): JSONObject {
        require(noteId.isNotBlank()) { "笔记 id 为空" }
        return client.request("DELETE_NOTE", mapOf("nid" to noteId), expectRes = false)
    }

    /**
     * 改已发笔记的配图（`UpdateUploadNoteUrls`，id + urls）。
     *
     * `urls` 是**全量覆盖**：传什么就是什么，没传进去的旧图会被摘掉，
     * 所以调用方要把保留下来的旧图一起拼进来。
     *
     * 服务端对不存在的 id 会回 `res=2`，是真有校验的（不像 UpdateSTCollection 那种
     * 无脑 res=0），所以调用方可以直接拿 res 判断成败。
     *
     * 注意：只能改图，**标题和正文改不了** —— 服务端没有开放这两个接口。
     */
    suspend fun updateNoteImages(client: FkstClient, noteId: String, imageUrls: List<String>): JSONObject {
        require(noteId.isNotBlank()) { "笔记 id 为空" }
        return client.request(
            "UPDATE_NOTE_URLS",
            mapOf(
                "id" to noteId,
                "urls" to org.json.JSONArray(imageUrls.filter { it.isNotBlank() }).toString(),
            ),
        )
    }

    /**
     * 拼发布笔记的 `content` 字段 —— **正文原样提交，不要包成 JSON**。
     *
     * 2026-09-25 复测修正了 14.3 节的旧结论：服务端把这个字段当**正文文本**存，
     * 不做任何协议解析。官方笔记在列表接口里返回的 `content` 就是纯文本
     * （如 `'好久不见'`），`GetNote` 解密出来是 `<p>好久不见</p>`。
     *
     * 之前按 `{"version":1,"text":…}` 提交，结果整串 JSON 被当成正文存了下来，
     * 列表和详情页都会原样显示这坨 JSON。
     * 老数据仍由 `Note.decodeContent` 兼容解析，不会显示成 JSON。
     *
     * 音频没有对应字段，改用正文里的一行标记 `[音频] <url>` 表达，
     * 追加在正文末尾；渲染见 `NoteDetailScreen`。
     */
    fun buildNoteContent(text: String, audioUrls: List<String> = emptyList()): String {
        val body = text.trim()
        val marks = audioUrls.filter { it.isNotBlank() }
            .joinToString("\n") { "${Constants.AUDIO_MARK} $it" }
        return when {
            marks.isEmpty() -> body
            body.isEmpty() -> marks
            else -> "$body\n$marks"
        }
    }

    // ------------------------------------------------------------ 试卷库

    /**
     * 试卷列表（GetShuatiPaper5）。
     *
     * 实测（2026-09-26）：只有 `f_gradeid`（年级）和 `version_id`（教材版本）会生效，
     * 而且 `version_id` 必须跟 `f_gradeid` 一起传 —— 单独传会返回 0 条。
     * `subject` / `xd` / `papertype` 传了也是被忽略。
     *
     * ⚠️ 响应里**没有 `res` 字段**（只有 `papers[]` / `answers[]`），
     * 所以这里必须 `expectRes = false`，否则会被当成 res=-1 抛异常。
     *
     * 「还有没有下一页」靠 `over` 字段，跟社区那几个列表接口一个套路。
     */
    suspend fun papers(
        client: FkstClient,
        page: Int = 0,
        fGradeId: String = "",
        versionId: String = "",
    ): Paged<Paper> {
        val p = LinkedHashMap<String, String>()
        p["page"] = page.toString()
        if (fGradeId.isNotBlank()) p["f_gradeid"] = fGradeId
        if (versionId.isNotBlank()) p["version_id"] = versionId
        val r = client.request("GET_PAPERS", p, expectRes = false)
        return Paged(Paper.list(r), !r.boolOr("over"))
    }

    /**
     * 试卷详情（GetZJPaperById5）：pid + type + aid + paperid。
     *
     * `pid` 和 `paperid` 都填试卷 id，`type` 填试卷自带的 type，`aid` 固定 0。
     * **只有 type=1（同步卷）拿得到题目**，其它 type 服务端回 res=1，
     * 上层会退化成「只看元信息」。响应同样没有 res 字段。
     */
    suspend fun paperDetail(client: FkstClient, paper: Paper): PaperDetail? {
        val r = client.request(
            "GET_PAPER_DETAIL",
            mapOf(
                "pid" to paper.id,
                "paperid" to paper.id,
                "type" to paper.type,
                "aid" to "0",
            ),
            expectRes = false,
        )
        return PaperDetail.from(r)
    }

    /** 教材版本列表（GetSTFilterData：filter=1 + subject + f_gradeid） */
    suspend fun paperVersions(
        client: FkstClient,
        subject: String,
        fGradeId: String,
    ): List<PaperVersion> {
        if (fGradeId.isBlank() || subject.isBlank()) return emptyList()
        val r = client.request(
            "GET_PAPER_VERSIONS",
            mapOf("subject" to subject, "f_gradeid" to fGradeId),
        )
        return PaperVersion.list(r)
    }

    /**
     * 收藏 / 取消收藏试卷（CollectShuatiPaper：status + type + pid）。
     *
     * 跟笔记收藏那条 CollectShuatiNote 同款，也是从官方端 dex 里挖出来的。
     * 首次实测可逆：status=1 收藏 pid=80446 → 收藏列表出现；status=0 → 消失。
     *
     * ⚠️ 两个坑：
     * 1. 它**恒回 res=0**，连不存在的 pid 也是 0，所以没法用返回值判断成败；
     * 2. 复测时遇到过「返回 0 但收藏列表里查不到」（同一时段笔记收藏列表、
     *    赞过列表也一起 res=1，像是服务端侧抖动）。
     *
     * 所以上层只能**乐观更新**本地的已收藏集合，不要拿它当可靠的云端存储。
     */
    suspend fun collectPaper(client: FkstClient, paper: Paper, on: Boolean): JSONObject =
        client.request(
            "COLLECT_PAPER",
            mapOf(
                "status" to if (on) "1" else "0",
                "type" to paper.type,
                "pid" to paper.id,
            ),
            expectRes = false,
        )

    /**
     * 我收藏的试卷（GetCollectionShuatiPaper5）。
     *
     * `type` 有白名单：0 / 1 / 2 / 12 有效，其它值（比如考研卷的 22）回 res=1，
     * 所以这里固定查同步卷 [Constants.PAPER_COLLECT_TYPE]。
     */
    suspend fun paperCollections(client: FkstClient, page: Int = 0): Paged<Paper> {
        val r = client.request(
            "GET_PAPER_COLLECTIONS",
            mapOf("type" to Constants.PAPER_COLLECT_TYPE, "page" to page.toString()),
        )
        return Paged(Paper.list(r), !r.boolOr("over"))
    }

    /**
     * 搜试卷（GetSearchPapers7，**comment 签名变体**）。
     *
     * 返回 `matches[]`，字段是 `pid` / `topic_title` / `logo` / `type`，
     * 跟列表接口的 `papers[]` 不一样，所以这里先转成 [Paper] 再交给 UI。
     * 搜出来的卷子 type 大多是 22（考研）/ 14（专升本）这类，**看不了题目**，
     * 列表里照样展示，点进去会提示不支持在线查看。
     */
    suspend fun searchPapers(client: FkstClient, keyword: String, page: Int = 0): Paged<Paper> {
        val r = client.request(
            "SEARCH_PAPERS",
            mapOf("keyword" to keyword, "page" to page.toString(), "ct" to "20"),
            expectRes = false,
        )
        val arr = r.optJSONArray("matches") ?: return Paged(emptyList(), false)
        val items = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                Paper(
                    id = o.str("pid"),
                    title = o.str("topic_title"),
                    subject = "",
                    xd = "",
                    fGradeId = "",
                    versionId = "",
                    logo = o.str("logo"),
                    littleTitle = o.str("tag1"),
                    questionNum = 0,
                    lookNum = 0,
                    useNum = 0,
                    isAnswer = false,
                    type = o.str("type"),
                    createdAt = 0L,
                )
            }
        }.filter { it.id.isNotBlank() }
        return Paged(items, !r.boolOr("over"))
    }

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
