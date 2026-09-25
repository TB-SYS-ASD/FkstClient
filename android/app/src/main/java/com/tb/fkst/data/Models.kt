package com.tb.fkst.data

import com.tb.fkst.core.Crypto
import com.tb.fkst.core.ImageUrls
import com.tb.fkst.core.boolOr
import com.tb.fkst.core.intOr
import com.tb.fkst.core.longOr
import com.tb.fkst.core.str
import org.json.JSONArray
import org.json.JSONObject

/** 分页结果 */
data class Paged<T>(val items: List<T>, val hasMore: Boolean)

private fun parseImages(raw: String?): List<String> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    } catch (t: Throwable) {
        emptyList()
    }
}

/** 社区笔记 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val images: List<String>,
    val thumb: String,
    val authorName: String,
    val authorAvatar: String,
    val homeId: String,
    val likeCount: Int,
    val selfLike: Boolean,
    val favoriteCount: Int,
    val createdAt: Long,
    val type: String,
    /** 这篇文章收到的硬币数（服务端 `coins` 字段） */
    val coinCount: Int = 0,
) {
    /** 列表用封面 */
    val cover: String get() = images.firstOrNull() ?: thumb

    /**
     * 原图列表（去掉 CDN 的 resize 段）。
     * 全屏查看与下载都用这个，见 [ImageUrls]。
     */
    val originalImages: List<String>
        get() {
            val list = images.map { ImageUrls.origin(it) }
            if (list.isNotEmpty()) return list
            return if (thumb.isBlank()) emptyList() else listOf(ImageUrls.origin(thumb))
        }

    /**
     * 可读正文。
     *
     * `content` 在服务端存储时会被包成 `{"version":1,"text":…,"si_urls":…}` 这种结构，
     * `GetNote` 读回来则是 `<p>正文</p>`；而 1.5.0 之前提交的正文本身就是 JSON，
     * 于是读回来成了双层嵌套。统一交给 [readable] 归一成纯文本，
     * 避免把 JSON 或 HTML 标签显示给用户。
     */
    val plainText: String get() = readable(content)

    companion object {

        /**
         * 正文归一化：先解包体 JSON，再剥 HTML 标签。
         *
         * 之所以要**反复解几层**，是因为服务端自己也会包一层：
         * 提交上去的正文会被服务端存成
         * `{"version":1,"text":"<提交的正文>",…,"sw":[[],[]]}`，
         * `GetNote` 则把它还原成 `<p><提交的正文></p>`。
         *
         * 而 1.5.0 之前的版本提交的正文**本身就是一坨 JSON**，
         * 于是服务端把它当成「正文文本」包了起来，读回来就成了双层嵌套：
         * `<p>{"version":1,"text":"qwqwqw",…}</p>` —— 这正是详情页显示出
         * 原始 JSON 的原因。多解几层即可同时兼容新老数据。
         */
        fun readable(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            var cur: String = raw
            var depth = 0
            while (depth < 3) {
                val next = decodeContent(cur)
                if (next == cur) break
                cur = next
                depth++
            }
            return Crypto.stripTags(cur)
        }

        /**
         * 把 content 的包体 JSON 解成可读文本；不是 JSON 包就原样返回。
         *
         * 不要求整串都是 JSON —— 从第一个 `{` 到最后一个 `}` 截出来试解析，
         * 这样 JSON 被 HTML 模板包住时也能抽出来。
         */
        fun decodeContent(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return raw
            val start = t.indexOf('{')
            val end = t.lastIndexOf('}')
            if (start < 0 || end <= start) return raw
            return try {
                val o = JSONObject(t.substring(start, end + 1))
                val sb = StringBuilder()
                val text = o.optString("text")
                if (text.isNotBlank()) sb.append(text)
                when (val sc = o.opt("sw_content")) {
                    is JSONArray -> for (i in 0 until sc.length()) {
                        when (val v = sc.opt(i)) {
                            is String -> if (v.isNotBlank()) {
                                if (sb.isNotEmpty()) sb.append('\n')
                                sb.append(v)
                            }
                            is JSONArray -> for (j in 0 until v.length()) {
                                val s = v.optString(j)
                                if (s.isNotBlank()) {
                                    if (sb.isNotEmpty()) sb.append('\n')
                                    sb.append(s)
                                }
                            }
                        }
                    }
                }
                sb.toString().trim().ifBlank { "" }
            } catch (e: Throwable) {
                raw
            }
        }

        fun from(o: JSONObject) = Note(
            id = o.str("id"),
            title = o.str("title"),
            content = o.str("content"),
            images = parseImages(o.optString("urls")),
            thumb = o.str("thumb"),
            authorName = o.str("nick_name"),
            authorAvatar = o.str("logo"),
            homeId = o.str("home_id"),
            likeCount = o.intOr("like_count"),
            selfLike = o.boolOr("self_like"),
            favoriteCount = o.str("favorite_ct").toIntOrNull() ?: 0,
            createdAt = o.longOr("created_at"),
            type = o.str("type"),
            coinCount = o.str("coins").toIntOrNull() ?: 0,
        )

        fun list(o: JSONObject, key: String = "notes"): List<Note> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { from(it) }.getOrNull() }
            }
        }
    }
}

/** 楼中楼的一条回复 */
data class Reply(
    val id: String,
    val content: String,
    val relayContent: String,
    val createdAt: Long,
    val fid: String,
    val nickName: String,
    val avatar: String,
    val homeId: String,
) {
    val display: String get() = com.tb.fkst.core.Crypto.plainContent(content)

    companion object {
        fun from(o: JSONObject) = Reply(
            id = o.str("id"),
            content = o.str("content"),
            relayContent = o.str("relay_content"),
            createdAt = o.longOr("created_at"),
            fid = o.str("fid"),
            nickName = o.str("nick_name"),
            avatar = o.str("logo"),
            homeId = o.str("home_id"),
        )
    }
}

/** 顶层评论 */
data class Comment(
    val id: String,
    val content: String,
    val createdAt: Long,
    val nickName: String,
    val avatar: String,
    val homeId: String,
    val province: String,
    val zanCount: Int,
    val replyCount: Int,
    val isTop: Boolean,
    val likeStatus: Int,
    val isSelf: Boolean,
    val replies: List<Reply>,
) {
    val display: String get() = com.tb.fkst.core.Crypto.plainContent(content)

    companion object {
        fun from(o: JSONObject): Comment {
            val reps = o.optJSONArray("replies")
            val list = if (reps == null) emptyList() else (0 until reps.length())
                .mapNotNull { i -> reps.optJSONObject(i)?.let { runCatching { Reply.from(it) }.getOrNull() } }
            return Comment(
                id = o.str("id"),
                content = o.str("content"),
                createdAt = o.longOr("created_at"),
                nickName = o.str("nick_name"),
                avatar = o.str("logo"),
                homeId = o.str("home_id"),
                province = o.str("user_province"),
                zanCount = o.str("zan_ct").toIntOrNull() ?: 0,
                replyCount = o.str("comment_ct").toIntOrNull() ?: 0,
                isTop = o.boolOr("is_top"),
                likeStatus = o.intOr("like_status"),
                isSelf = o.boolOr("is_self"),
                replies = list,
            )
        }

        fun list(o: JSONObject): List<Comment> {
            val arr = o.optJSONArray("comments") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { from(it) }.getOrNull() }
            }
        }
    }
}

/** 用户资料（GetSTUserData） */
data class UserProfile(
    val homeId: String,
    val nickName: String,
    val avatar: String,
    val province: String,
    val sex: String,
    val followCount: Int,
    val fansCount: Int,
    val releaseCount: Int,
    val answerCount: Int,
    val isFollow: Boolean,
) {
    companion object {
        fun from(homeId: String, o: JSONObject): UserProfile {
            val info = o.optJSONObject("info") ?: JSONObject()
            return UserProfile(
                homeId = homeId,
                nickName = info.str("nick_name"),
                avatar = info.str("logo"),
                province = info.str("province"),
                sex = info.str("sex"),
                followCount = o.str("follow_count").toIntOrNull() ?: 0,
                fansCount = o.str("fans_count").toIntOrNull() ?: 0,
                releaseCount = o.intOr("release_count"),
                answerCount = o.str("answer_question_count").toIntOrNull() ?: 0,
                isFollow = o.boolOr("is_follow"),
            )
        }
    }
}

/** 我的统计（GetSTMyData5） */
data class MyStats(
    val followCount: Int,
    val fansCount: Int,
    val coinCount: Int,
    val unread: Int,
    val awardCount: Int,
) {
    companion object {
        fun from(o: JSONObject): MyStats {
            var unread = 0
            o.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    unread += m.str("count").toIntOrNull() ?: 0
                }
            }
            return MyStats(
                followCount = o.str("follow_count").toIntOrNull() ?: 0,
                fansCount = o.str("fans_count").toIntOrNull() ?: 0,
                coinCount = o.str("coin_count").toIntOrNull() ?: 0,
                unread = unread,
                awardCount = o.str("award_count").toIntOrNull() ?: 0,
            )
        }
    }
}

/** 消息通知 */
data class Notice(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val nickName: String,
    val avatar: String,
) {
    companion object {
        fun from(o: JSONObject) = Notice(
            id = o.str("id"),
            title = o.str("title"),
            content = o.str("content"),
            createdAt = o.longOr("created_at"),
            nickName = o.str("nick_name"),
            avatar = o.str("logo"),
        )

        fun list(o: JSONObject): List<Notice> {
            val arr = o.optJSONArray("notices") ?: o.optJSONArray("messages") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { from(it) }.getOrNull() }
            }
        }
    }
}

/**
 * 搜索结果条目（GetSearchNotes 返回的 matches[]）。
 * 注意字段和社区笔记不同：只有 nid / title / thumb，点进去走 H5 详情。
 */
data class SearchItem(
    val nid: String,
    val title: String,
    val thumb: String,
    val type: Int,
    val createdAt: Long,
) {
    /** 搜索结果只有 nid/title/thumb，转成 Note 方便复用详情页 */
    fun toNote(): Note = Note(
        id = nid,
        title = title,
        content = "",
        images = if (thumb.isNotBlank()) listOf(thumb) else emptyList(),
        thumb = thumb,
        authorName = "",
        authorAvatar = "",
        homeId = "",
        likeCount = 0,
        selfLike = false,
        favoriteCount = 0,
        createdAt = createdAt,
        type = type.toString(),
        coinCount = 0,
    )

    companion object {
        fun list(o: JSONObject): List<SearchItem> {
            val arr = o.optJSONArray("matches") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val it = arr.optJSONObject(i) ?: return@mapNotNull null
                runCatching {
                    SearchItem(
                        nid = if (it.isNull("nid")) "" else (it.opt("nid")?.toString() ?: ""),
                        title = it.str("title"),
                        thumb = it.str("thumb"),
                        type = it.intOr("type"),
                        createdAt = it.longOr("created_at"),
                    )
                }.getOrNull()
            }
        }
    }
}

/** 文章正文（解密后） */
data class NoteDetail(
    val noteId: String,
    val content: String,
    val plain: String,
)

/**
 * 作者简介（只有 mid / 昵称 / 头像）。
 *
 * 「赞过」（GetLikeShuatiNote）与「收藏」（GetCollectionShuatiNote1）这两个接口
 * 返回的笔记**只有 `home_id`，没有 `nick_name` / `logo`**，所以列表里作者一直显示「匿名」。
 * 解决办法是拿 `home_id` 去 GetSTUserData 单独补一份资料，并本地缓存下来。
 */
data class AuthorBrief(
    val homeId: String,
    val name: String,
    val avatar: String,
) {
    /** 本地缓存的存储形态：昵称 \u0001 头像 */
    fun encode(): String = name + "\u0001" + avatar

    companion object {
        fun decode(homeId: String, raw: String): AuthorBrief? {
            if (raw.isBlank()) return null
            val parts = raw.split('\u0001')
            val name = parts.getOrNull(0).orEmpty()
            val avatar = parts.getOrNull(1).orEmpty()
            if (name.isBlank() && avatar.isBlank()) return null
            return AuthorBrief(homeId, name, avatar)
        }
    }
}

/** 登录成功后的身份 */
data class Session(
    val mid: String,
    val unionid: String,
    val openid: String,
    val deviceToken: String,
)

// ==================================================================== 私信

/**
 * 好友 / 会话对象（GetSTBuddies）。
 * type=1 互关、2 我关注的、3 粉丝。
 */
data class Buddy(
    val homeId: String,
    val nickName: String,
    val avatar: String,
    val fans: Int,
    val isMutual: Boolean,
    /** 备注名，互关时可用作会话标题 */
    val mark: String = "",
    /** 服务端关系 id（SetSTFollowMark 设置备注时要用） */
    val relationId: String = "",
    /** 0 未关注 / 3 已关注（互关） */
    val followStatus: Int = 0,
) {
    val displayName: String get() = mark.ifBlank { nickName }
    val followed: Boolean get() = followStatus == 3

    /** 展示名：本地备注 > 服务端备注（mark）> 昵称 > mid */
    fun nameWith(localRemark: String?): String =
        localRemark?.takeIf { it.isNotBlank() }
            ?: displayName.ifBlank { homeId }

    companion object {
        fun from(o: JSONObject) = Buddy(
            homeId = if (o.isNull("home_id")) "" else (o.opt("home_id")?.toString() ?: ""),
            nickName = o.str("nick_name"),
            avatar = o.str("logo"),
            fans = o.str("fans").toIntOrNull() ?: 0,
            isMutual = o.optBoolean("is_mutual", false),
            mark = o.str("mark"),
            relationId = if (o.isNull("id")) "" else (o.opt("id")?.toString() ?: ""),
            followStatus = o.optInt("follow_status", if (o.optBoolean("is_mutual", false)) 3 else 0),
        )

        /**
         * GetSTLetterMessage 返回的 `member` 对象。
         *
         * 它只有 `id` / `nick_name` / `logo` 三个字段，**没有 home_id**，
         * 但这里的 `id` 就是 home_id（实测 `{"id":"10000002",...}`）。
         * 好友列表（GetSTBuddies）里的 `id` 反而是「关系 id」，所以两个入口要用两个解析器，
         * 混用会让 homeId 变成关系 id，备注、置顶、标题全对不上。
         */
        fun fromMember(o: JSONObject): Buddy = Buddy(
            homeId = o.str("home_id").ifBlank { o.str("id") },
            nickName = o.str("nick_name"),
            avatar = o.str("logo"),
            fans = o.str("fans").toIntOrNull() ?: 0,
            isMutual = o.optBoolean("is_mutual", false),
            mark = o.str("mark"),
            relationId = "",
            followStatus = o.optInt("follow_status", 0),
        )

        fun list(o: JSONObject): List<Buddy> {
            val arr = o.optJSONArray("buddies") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { from(it) }.getOrNull() }
            }
        }
    }
}

/**
 * 单条私信（GetSTLetterMessage 返回的 letters[]）。
 *
 * 服务端字段名在不同版本里有出入，这里对每个字段都做了别名兜底，
 * 只要能取到 id / content / 发送时间就能正常渲染。
 *
 * **方向（谁发的）单独说明**：服务端不返回 `from_mid`，它用的是 `is_self`；
 * 所以左右气泡不能拿 `from_mid == myMid` 去算（那样会全部算成「别人发的」，
 * 自己发出去的消息全挤在左边）。判定逻辑集中在本文件的 [resolveDirection]。
 */
data class Letter(
    val id: String,
    val content: String,
    val fromMid: String,
    val toMid: String,
    val createdAt: Long,
    val isRead: Boolean,
    /** 服务端的消息类型（1 文本 / 2 图片 / 3 文件，实测值待签到解锁后复核） */
    val type: String = "",
    /** 图片消息的图片地址（已转成原图）；非图片消息为空 */
    val image: String = "",
    /** 文件消息的附件地址；非文件消息为空 */
    val file: String = "",
    /** 文件消息的原始文件名 */
    val fileName: String = "",
    /** 是不是我自己发的 —— 由 [resolveDirection] 结合整段会话算出来，UI 直接用 */
    val mine: Boolean = false,
    /** 服务端 `is_self` 的原值，没这个字段时是 null（调试用） */
    val selfSent: Boolean? = null,
    /** 服务端 `my_mid` 原值（兜底判方向用） */
    val myMid: String = "",
    /** 服务端 `other_mid` 原值（兜底判方向用） */
    val otherMid: String = "",
    /**
     * 「已撤回」标记。
     *
     * 平台没有撤回接口，客户端能观察到的「撤回」= 这条消息从
     * GetSTLetterMessage 的返回里消失了（对方用官方客户端撤回 / 删除）。
     * 开了「私信防撤回」之后，我们会把消息本地归档，下次刷新时发现某条不见了
     * 就把它以 [recalled] = true 留在列表里，UI 灰显并标注「已撤回」。
     */
    val recalled: Boolean = false,
    val raw: String = "",
) {
    /** 图片消息 */
    val isImage: Boolean get() = image.isNotBlank()

    /** 文件消息 */
    val isFile: Boolean get() = file.isNotBlank()

    /** 按扩展名猜出来的体积/类型提示用的后缀 */
    val fileExt: String get() = ImageUrls.rawExtensionOf(file).uppercase()

    /** LazyColumn 的稳定 key：服务端可能只给会话 id（lid），单用它会让同类消息撞 key */
    val stableKey: String get() = "$id|$createdAt|${content.take(24)}"

    companion object {
        /** 依次尝试多个候选字段名，取第一个非空值 */
        private fun pick(o: JSONObject, vararg keys: String): String {
            for (k in keys) {
                if (!o.has(k) || o.isNull(k)) continue
                val v = o.opt(k)?.toString() ?: continue
                if (v.isNotBlank() && v != "null") return v
            }
            return ""
        }

        /** 三态布尔：只有能明确读懂时才返回 true/false，否则 null */
        private fun pickBool(o: JSONObject, vararg keys: String): Boolean? {
            val v = pick(o, *keys).lowercase()
            return when (v) {
                "1", "true", "yes", "y", "self", "me", "是" -> true
                "0", "false", "no", "n", "other", "ta", "否" -> false
                else -> null
            }
        }

        fun from(o: JSONObject): Letter {
            val created = pick(o, "created_at", "create_time", "ctime", "time", "timestamp")
            val body = pick(o, "content", "letter_content", "text", "message")
            val urlOnly = pick(o, "url", "image", "img", "pic", "image_url", "letter_url")
            val type = pick(o, "type", "letter_type", "msg_type", "message_type")
            val explicitFile = pick(o, "file", "file_url", "letter_file", "attachment", "attachment_url")
            val explicitName = pick(o, "file_name", "filename", "name")

            // 服务端的媒体消息有三种落法：type + url 字段 / content 直接放地址 / 单独的 file 字段。
            // 这里统一按「扩展名」判断它到底是图还是文件，避免把 PDF 当成图片渲染。
            val candidate = when {
                ImageUrls.isRemote(explicitFile) -> explicitFile
                ImageUrls.isRemote(urlOnly) -> urlOnly
                ImageUrls.isRemote(body) -> body
                else -> ""
            }

            val isFile = candidate.isNotBlank() &&
                (explicitFile == candidate || ImageUrls.isFileUrl(candidate))
            val image = when {
                candidate.isBlank() -> ""
                isFile -> ""
                ImageUrls.isImageUrl(candidate) || explicitFile != candidate -> ImageUrls.origin(candidate)
                else -> ""
            }

            return Letter(
                id = pick(o, "id", "letter_id", "lid"),
                content = body,
                // 只有明确的「发送者」字段才算 from；home_id 放最后，避免把会话 id 当成方向
                fromMid = pick(o, "from_mid", "from_home_id", "send_mid", "sender_mid", "send_home_id", "home_id", "mid"),
                toMid = pick(o, "to_mid", "to_home_id", "receive_mid", "other_mid"),
                createdAt = created.toLongOrNull() ?: 0L,
                isRead = pick(o, "is_read", "read").let { it == "1" || it == "true" },
                type = type,
                image = image,
                file = if (isFile) candidate else "",
                fileName = explicitName.ifBlank {
                    if (isFile) ImageUrls.baseNameOf(candidate) else ""
                },
                selfSent = pickBool(o, "is_self", "self", "is_me", "is_mine", "is_own", "mine"),
                myMid = pick(o, "my_mid"),
                otherMid = pick(o, "other_mid"),
                raw = o.toString(),
            )
        }

        fun list(o: JSONObject, selfMid: String = "", peerMid: String = ""): List<Letter> {
            val arr = o.optJSONArray("letters")
                ?: o.optJSONArray("messages")
                ?: o.optJSONArray("list")
                ?: return emptyList()
            val items = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { from(it) }.getOrNull() }
            }
            return ordered(resolveDirection(items, selfMid, peerMid))
        }

        /**
         * 时间升序：老的在上、新的在下（会话页新消息贴着输入框）。
         *
         * 服务端返回的顺序不保证，全都没有时间戳时只能保持原顺序 ——
         * 这时候也「不为难」它，总比乱排好。
         */
        fun ordered(items: List<Letter>): List<Letter> {
            if (items.size < 2) return items
            if (items.none { it.createdAt > 0 }) return items
            return items.withIndex()
                .sortedWith(compareBy({ it.value.createdAt }, { it.index }))
                .map { it.value }
        }

        /** 单条会话本地归档的上限，防止 prefs 被聊天记录撑爆 */
        const val ARCHIVE_LIMIT = 300

        /**
         * 把一段会话压成可持久化的 JSON（防撤回用）。
         *
         * 存的是**服务端原始报文 + 已经算好的方向**，这样下次拉不到这条消息时
         * 还能原样渲染出来（包括是图片还是文件、靠左还是靠右）。
         */
        fun encodeArchive(items: List<Letter>): String {
            val arr = JSONArray()
            items.takeLast(ARCHIVE_LIMIT).forEach { l ->
                if (l.raw.isBlank()) return@forEach
                val rec = JSONObject()
                rec.put("mine", l.mine)
                rec.put("recalled", l.recalled)
                val body = runCatching { JSONObject(l.raw) }.getOrNull() ?: return@forEach
                rec.put("d", body)
                arr.put(rec)
            }
            return arr.toString()
        }

        fun decodeArchive(json: String): List<Letter> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val rec = arr.optJSONObject(i) ?: return@mapNotNull null
                    val body = rec.optJSONObject("d") ?: return@mapNotNull null
                    runCatching {
                        from(body).copy(
                            mine = rec.optBoolean("mine", false),
                            recalled = rec.optBoolean("recalled", false),
                        )
                    }.getOrNull()
                }
            }.getOrDefault(emptyList())
        }

        /**
         * 把「归档里已经不见了」的消息找出来，标记成已撤回。
         *
         * 判定方式：同一段会话里，上一次拉到过、这一次没了的消息 —— 就是对方撤回了。
         *
         * **只认「本次返回区间之内」的消息**：会话接口是按页拉最新 N 条的，
         * 归档里更早的那些只是没被这一页覆盖到，并不是被撤回。所以拿本次返回里
         * 最老的一条时间戳当水位线，比它更老的归档消息一律不算撤回。
         * 没有可用时间戳时（水位线为 0）宁可不判，也不要冤枉人。
         *
         * @return (合并后的完整列表, **这一次新发现**的撤回消息)
         */
        fun mergeRecalled(
            fresh: List<Letter>,
            archived: List<Letter>,
        ): Pair<List<Letter>, List<Letter>> {
            if (archived.isEmpty() || fresh.isEmpty()) return fresh to emptyList()

            val cutoff = fresh.map { it.createdAt }.filter { it > 0 }.minOrNull()
                ?: return fresh to emptyList()

            val aliveKeys = fresh.map { it.stableKey }.toHashSet()
            val aliveIds = fresh.map { it.id }.filter { it.isNotBlank() }.toHashSet()
            val missing = archived.filter { a ->
                a.createdAt >= cutoff &&
                    (a.id.isBlank() || a.id !in aliveIds) &&
                    a.stableKey !in aliveKeys
            }
            if (missing.isEmpty()) return fresh to emptyList()

            // 之前就已经标过「已撤回」的继续留着（所以不能直接从归档里删掉），
            // 只有这次新变的才算「新发现」——否则每 10 秒轮询一次就会一直弹提示。
            val total = ordered(fresh + missing.map { it.copy(recalled = true) })
            val newly = missing.filter { !it.recalled }
            return total to newly
        }

        /**
         * 判断每条消息是不是「我发的」，决定气泡靠左还是靠右。
         * 平台在这件事上很不统一，所以按可靠性从高到低依次尝试：
         * 1. `is_self` —— 服务端真正的方向字段（字符串池里能挖到，与 `is_read` 相邻）；
         * 2. `my_mid` / `other_mid` 同时等于「自己 / 对方」—— 说明这两个字段是「发送者 / 接收者」；
         * 3. `to_mid` 这类老写法：只有它在整段会话里出现过两种取值才敢用
         *    （否则说明它其实是固定的「对方 mid」，拿它判方向会把所有消息都算成自己发的）；
         * 4. 全都认不出来时按「别人发的」渲染，宁可靠左也不要乱认。
         */
        private fun resolveDirection(
            items: List<Letter>,
            selfMid: String,
            peerMid: String,
        ): List<Letter> {
            if (items.isEmpty()) return items

            if (items.any { it.selfSent != null }) {
                return items.map { it.copy(mine = it.selfSent == true) }
            }

            if (selfMid.isNotBlank()) {
                if (peerMid.isNotBlank() &&
                    items.all { it.myMid.isNotBlank() && it.otherMid.isNotBlank() }
                ) {
                    return items.map {
                        it.copy(mine = it.myMid == selfMid && it.otherMid == peerMid)
                    }
                }

                if (peerMid.isNotBlank() &&
                    items.map { it.toMid }.filter { it.isNotBlank() }.distinct().size >= 2
                ) {
                    return items.map { it.copy(mine = it.toMid == peerMid) }
                }

                if (items.any { it.fromMid.isNotBlank() && it.fromMid != it.toMid }) {
                    return items.map { it.copy(mine = it.fromMid == selfMid) }
                }
            }

            return items
        }
    }
}

/** 一次私信拉取的结果：会话对象资料 + 消息列表 */
data class LetterThread(
    val member: Buddy?,
    val letters: List<Letter>,
    /** 服务端原始返回，出问题时用于排查（私信页菜单里可以看） */
    val raw: String = "",
)

/** 图片上传结果（OSSUploadImage4.php） */
data class UploadedImage(
    val url: String,
    /** 内容审核没通过，需要换图 */
    val illegal: Boolean,
    /** 该目录需要会员 */
    val needVip: Boolean,
    val res: Int,
    val error: String,
) {
    val ok: Boolean get() = res == 0 && url.isNotBlank() && !illegal

    companion object {
        fun from(o: JSONObject): UploadedImage {
            val url = o.str("url")
            val error = o.str("error").ifBlank { o.str("remind_hint") }
            return UploadedImage(
                url = ImageUrls.origin(url),
                illegal = o.boolOr("illegal"),
                needVip = error.contains("会员"),
                res = o.intOr("res", -1),
                error = error,
            )
        }
    }
}

/** 待上传的本地图片（发布笔记用）：字节 + 文件名 + MIME */
class PendingImage(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String = "image/jpeg",
) {
    val size: Int get() = bytes.size
}

/** 待上传的本地音频（发布笔记用）：服务端只认 `.mp3` */
class PendingAudio(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String = "audio/mpeg",
) {
    val size: Int get() = bytes.size
}

/** 文件上传结果（OSSUploadFile2.php） */data class UploadedFile(
    val url: String,
    val md5: String,
    val res: Int,
    val error: String,
) {
    val ok: Boolean get() = res == 0 && url.isNotBlank()

    companion object {
        fun from(o: JSONObject) = UploadedFile(
            url = o.str("url"),
            md5 = o.str("md5"),
            res = o.intOr("res", -1),
            error = o.str("error").ifBlank { o.str("remind_hint") },
        )
    }
}

/** 签到 / 积分状态（来自 GetSTMyData5） */
data class CoinState(
    val coinCount: Int,
    val signDays: Int,
    val signedToday: Boolean,
) {
    /** 平台规则：连续签到满 3 天才能对外发私信 */
    val canSendLetter: Boolean get() = signDays >= 3
    val signHint: String
        get() = if (canSendLetter) "已解锁私信"
        else "连续签到 $signDays/3 天，满 3 天解锁私信"

    companion object {
        fun from(o: JSONObject): CoinState {
            val days = o.str("get_coin_day").toIntOrNull()
                ?: o.optInt("get_coin_day", 0)
            val status = o.str("get_coin_status")
            return CoinState(
                coinCount = o.str("coin_count").toIntOrNull() ?: 0,
                signDays = days,
                signedToday = status == "1" || o.optInt("get_coin_status", 0) == 1,
            )
        }
    }
}
