package com.tb.fkst.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tb.fkst.core.Constants
import com.tb.fkst.core.FkstApiException
import com.tb.fkst.core.MediaSaver
import com.tb.fkst.core.intOr
import com.tb.fkst.core.str
import com.tb.fkst.data.Api
import com.tb.fkst.data.AuthorBrief
import com.tb.fkst.data.Buddy
import com.tb.fkst.data.CoinState
import com.tb.fkst.data.Letter
import com.tb.fkst.data.Note
import com.tb.fkst.data.Notice
import com.tb.fkst.data.Paged
import com.tb.fkst.data.PendingImage
import com.tb.fkst.data.Repository
import com.tb.fkst.data.SearchItem
import com.tb.fkst.data.ThemeStyle
import com.tb.fkst.data.UserProfile
import com.tb.fkst.data.MyStats
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 列表页的通用状态 */
data class FeedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val page: Int = -1,
    val loaded: Boolean = false,
)

fun friendlyError(t: Throwable): String = when (t) {
    is FkstApiException -> t.message ?: "请求失败"
    is UnknownHostException -> "网络不可用，请检查网络连接"
    is SocketTimeoutException -> "请求超时，请稍后重试"
    else -> t.message ?: "未知错误"
}

class AppViewModel(val repo: Repository) : ViewModel() {

    val client get() = repo.client

    // ------------------------------------------------------------ 启动 / 登录

    var booted by mutableStateOf(false)
        private set

    var loggedIn by mutableStateOf(false)
        private set

    var loginLoading by mutableStateOf(false)
        private set

    var loginError by mutableStateOf<String?>(null)
        private set

    var toast by mutableStateOf<String?>(null)

    // ------------------------------------------------------------ 外观偏好

    var dynamicColor by mutableStateOf(repo.dynamicColor)
        private set

    var themeMode by mutableStateOf(repo.themeMode)
        private set

    fun updateDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        repo.dynamicColor = enabled
    }

    fun updateThemeMode(mode: com.tb.fkst.data.ThemeMode) {
        themeMode = mode
        repo.themeMode = mode
    }

    /** 卡片样式：默认 / 玻璃透明卡片 + 壁纸 */
    var themeStyle by mutableStateOf(repo.themeStyle)
        private set

    fun updateThemeStyle(style: ThemeStyle) {
        themeStyle = style
        repo.themeStyle = style
    }

    /** 自定义壁纸的本地路径；空 = 没设置（玻璃主题下用渐变兜底） */
    var wallpaperPath by mutableStateOf(repo.wallpaperPath)
        private set

    fun setWallpaper(uri: Uri) {
        val path = repo.saveWallpaper(uri)
        if (path.isNullOrBlank()) {
            toast = "壁纸设置失败，换张图试试"
            return
        }
        wallpaperPath = path
        if (themeStyle != ThemeStyle.GLASS) updateThemeStyle(ThemeStyle.GLASS)
        toast = "壁纸已更新"
    }

    fun clearWallpaper() {
        repo.clearWallpaper()
        wallpaperPath = ""
        toast = "壁纸已清除"
    }

    var autoCheckIn by mutableStateOf(repo.autoCheckIn)
        private set

    fun updateAutoCheckIn(enabled: Boolean) {
        autoCheckIn = enabled
        repo.autoCheckIn = enabled
    }

    // ------------------------------------------------------------ 私信防撤回

    /**
     * 私信防撤回。
     *
     * 平台没有「撤回」接口，能观察到的撤回就是消息从 `GetSTLetterMessage` 的返回里消失。
     * 打开后每段会话都会在本地留一份归档，消失的消息会以「已撤回」继续留在列表里。
     * 关掉时顺手把已经攒下的本地备份清掉，避免之后误判。
     */
    var antiRecall by mutableStateOf(repo.antiRecall)
        private set

    /**
     * 注意命名：`antiRecall` 属性的 setter 会生成 `setAntiRecall(Z)V`，
     * 所以这个方法不能也叫 setAntiRecall（会撞 JVM 签名）。
     */
    fun applyAntiRecall(enabled: Boolean) {
        if (antiRecall == enabled) return
        antiRecall = enabled
        repo.antiRecall = enabled
        if (!enabled) {
            repo.clearLetterArchive()
            archive = emptyMap()
            if (letters.any { it.recalled }) letters = letters.filter { !it.recalled }
        }
        toast = if (enabled) "已开启私信防撤回" else "已关闭防撤回，本地备份已清除"
    }

    /** 每段会话的本地归档：other_mid -> Letter.encodeArchive(...) */
    private var archive: Map<String, String> = repo.letterArchive

    /** 清空防撤回留下的聊天备份 */
    fun clearLetterBackup() {
        repo.clearLetterArchive()
        archive = emptyMap()
        letters = letters.filter { !it.recalled }
        toast = "已清空防撤回留下的聊天备份"
    }

    /** 防撤回备份占了多少段会话（设置页展示用） */
    val letterBackupCount: Int get() = archive.size

    // ------------------------------------------------------------ 缓存清理

    /** 当前缓存占用（人类可读） */
    var cacheText by mutableStateOf("")

    /** 正在清理缓存 */
    var clearingCache by mutableStateOf(false)
        private set

    fun refreshCacheSize() {
        viewModelScope.launch {
            cacheText = withContext(Dispatchers.IO) {
                com.tb.fkst.core.ImageUrls.readableSize(repo.cacheSizeBytes())
            }
        }
    }

    /** 清理图片缓存与临时文件，回调里给出释放的体积文案 */
    fun clearCache(onDone: (String) -> Unit = {}) {
        if (clearingCache) return
        clearingCache = true
        viewModelScope.launch {
            try {
                val freed = withContext(Dispatchers.IO) { repo.clearCacheBytes() }
                val left = withContext(Dispatchers.IO) { repo.cacheSizeBytes() }
                cacheText = com.tb.fkst.core.ImageUrls.readableSize(left)
                val text = com.tb.fkst.core.ImageUrls.readableSize(freed)
                toast = "已清理缓存，释放 $text"
                onDone(text)
            } catch (t: Throwable) {
                toast = "清理失败：${friendlyError(t)}"
                onDone("")
            } finally {
                clearingCache = false
            }
        }
    }

    // ------------------------------------------------------------ 作者资料缓存
    //
    // 「赞过」（GetLikeShuatiNote）与「收藏」（GetCollectionShuatiNote1）返回的笔记
    // 只有 home_id，没有 nick_name / logo，所以这两个列表里作者一直显示「匿名」。
    // 这里拿 home_id 逐个去 GetSTUserData 补一份资料，并落到本地缓存。

    /** home_id -> 作者资料 */
    var authorInfo by mutableStateOf(loadAuthorCache())
        private set

    /** 本进程里拉过但没拿到的（失败/已注销），避免反复请求 */
    private val authorTried = mutableSetOf<String>()

    private fun loadAuthorCache(): Map<String, AuthorBrief> =
        repo.authorCache.mapNotNull { (k, v) -> AuthorBrief.decode(k, v)?.let { k to it } }.toMap()

    private fun persistAuthors() {
        val trimmed = authorInfo.entries.toList().takeLast(Constants.AUTHOR_CACHE_LIMIT)
        authorInfo = trimmed.associate { it.key to it.value }
        repo.authorCache = authorInfo.mapValues { it.value.encode() }
    }

    /** 列表用：优先用笔记自带的作者，缺了就用缓存补 */
    private fun withAuthor(note: Note): Note {
        if (note.authorName.isNotBlank() && note.authorAvatar.isNotBlank()) return note
        val brief = authorInfo[note.homeId] ?: return note
        return note.copy(
            authorName = note.authorName.ifBlank { brief.name },
            authorAvatar = note.authorAvatar.ifBlank { brief.avatar },
        )
    }

    fun applyAuthors(items: List<Note>): List<Note> =
        if (items.isEmpty()) items else items.map { withAuthor(it) }

    /** 把缓存里的作者信息补进已有列表（补拉成功后台刷新用） */
    private fun patchFeeds() {
        discoverFeed = discoverFeed.copy(items = applyAuthors(discoverFeed.items))
        followFeed = followFeed.copy(items = applyAuthors(followFeed.items))
        myNotesFeed = myNotesFeed.copy(items = applyAuthors(myNotesFeed.items))
        likedFeed = likedFeed.copy(items = applyAuthors(likedFeed.items))
        collectionFeed = collectionFeed.copy(items = applyAuthors(collectionFeed.items))
        userFeed = userFeed.copy(items = applyAuthors(userFeed.items))
    }

    /**
     * 后台把列表里缺作者的笔记补全（串行 + 本地缓存，只有没缓存过的才会真的发请求）。
     */
    private fun resolveAuthors(items: List<Note>) {
        val need = items.asSequence()
            .filter { it.authorName.isBlank() || it.authorAvatar.isBlank() }
            .map { it.homeId }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { authorInfo[it] == null && it !in authorTried }
            .toList()
        if (need.isEmpty()) return
        authorTried += need

        viewModelScope.launch {
            var got = false
            need.forEach { homeId ->
                val brief = runCatching {
                    val p = Api.profile(client, homeId)
                    AuthorBrief(homeId, p.nickName, p.avatar)
                        .takeIf { it.name.isNotBlank() || it.avatar.isNotBlank() }
                }.getOrNull() ?: return@forEach
                authorInfo = authorInfo + (homeId to brief)
                got = true
            }
            if (got) {
                persistAuthors()
                patchFeeds()
            }
        }
    }

    /** 清空作者资料缓存（设置页） */
    fun clearAuthorCache() {
        repo.clearAuthorCache()
        authorTried.clear()
        authorInfo = emptyMap()
        toast = "已清空作者资料缓存"
    }

    /** 单篇笔记的作者名 / 头像（文章页用；笔记自带优先，缺了用缓存） */
    fun authorNameOf(note: Note): String = withAuthor(note).authorName
    fun authorAvatarOf(note: Note): String = withAuthor(note).authorAvatar

    /** 需要时补拉某个作者（文章页打开时调一下） */
    fun ensureAuthor(note: Note) {
        if (note.authorName.isBlank() || note.authorAvatar.isBlank()) resolveAuthors(listOf(note))
    }

    /** 作者资料是否已经拿到过（决定文章页要不要显示「识别中」） */
    fun hasAuthorInfo(homeId: String): Boolean =
        authorInfo[homeId] != null || homeId in authorTried

    // ------------------------------------------------------------ 排版

    /** 文章列表排版：1 = 单列大卡，2 = 双列（一排两个） */
    var listColumns by mutableStateOf(repo.listColumns)
        private set

    fun toggleListColumns() {
        listColumns = if (listColumns == 1) 2 else 1
        repo.listColumns = listColumns
        toast = if (listColumns == 2) "排版：一排两个" else "排版：单列大卡"
    }

    /** 设置页里直接指定排版（1 单列 / 2 双列） */
    fun applyListColumns(columns: Int) {
        val v = columns.coerceIn(1, 2)
        if (v == listColumns) return
        listColumns = v
        repo.listColumns = v
        toast = if (v == 2) "排版：一排两个" else "排版：单列大卡"
    }

    // ------------------------------------------------------------ 导航目标

    /** 当前打开的文章（详情页从这里取，避免 nav 参数编码麻烦） */
    var detailNote by mutableStateOf<Note?>(null)

    /** 当前查看的用户主页 home_id */
    var userTarget by mutableStateOf("")

    fun openNote(note: Note) {
        detailNote = note
    }

    // ------------------------------------------------------------ 全屏看图

    var viewerImages by mutableStateOf<List<String>>(emptyList())
        private set

    var viewerIndex by mutableStateOf(0)
        private set

    var viewerTitle by mutableStateOf("")
        private set

    var downloadingImage by mutableStateOf(false)
        private set

    /** 打开全屏图片查看器（传进来最好已经是原图地址） */
    fun openViewer(images: List<String>, index: Int = 0, title: String = "") {
        val list = images.filter { it.isNotBlank() }
        if (list.isEmpty()) return
        viewerImages = list
        viewerIndex = index.coerceIn(0, list.lastIndex)
        viewerTitle = title
    }

    fun closeViewer() {
        viewerImages = emptyList()
        viewerIndex = 0
        viewerTitle = ""
    }

    /** 保存图片到相册（下载的就是原图，不带 CDN 缩放） */
    fun downloadImage(context: android.content.Context, url: String) {
        if (downloadingImage) return
        downloadingImage = true
        viewModelScope.launch {
            try {
                val where = MediaSaver.downloadToGallery(context, url)
                toast = "已保存到相册（$where）"
            } catch (t: Throwable) {
                toast = "保存失败：${friendlyError(t)}"
            } finally {
                downloadingImage = false
            }
        }
    }

    // ------------------------------------------------------------ 我的主页

    /** 自己主页的 tab：0 发布 / 1 赞过 / 2 收藏 */
    var myHomeTab by mutableStateOf(0)
        private set

    fun selectMyHomeTab(index: Int) {
        if (index !in 0..2 || index == myHomeTab) return
        myHomeTab = index
    }

    fun boot() {
        viewModelScope.launch {
            loggedIn = repo.restoreSession()
            booted = true
            if (loggedIn) {
                refreshMeNow()
                maybeAutoCheckIn()
            }
        }
    }

    fun login(phone: String, password: String, remember: Boolean) {
        if (phone.isBlank() || password.isBlank()) {
            loginError = "请填写手机号和密码"
            return
        }
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            try {
                repo.login(phone.trim(), password, remember)
                loggedIn = true
                refreshMeNow()
                maybeAutoCheckIn()
            } catch (t: Throwable) {
                loginError = friendlyError(t)
            } finally {
                loginLoading = false
            }
        }
    }

    fun logout() {
        repo.logout()
        loggedIn = false
        myProfile = null
        myStats = null
        discoverFeed = FeedState()
        followFeed = FeedState()
        searchFeed = FeedState()
        myNotesFeed = FeedState()
        likedFeed = FeedState()
        collectionFeed = FeedState()
        noticesFeed = FeedState()
        userFeed = FeedState()
        searchKeyword = ""
        buddies = emptyList()
        buddiesError = null
        followingFeed = FeedState()
        fansFeed = FeedState()
        letters = emptyList()
        letterPartner = null
        letterTargetMid = ""
        lettersError = null
        lettersRaw = ""
        letterRefreshing = false
        coinState = null
        socialTab = 0
        autoChecked = false
        collectedIds = emptySet()
        collectedLoaded = false
        viewerImages = emptyList()
        viewerIndex = 0
        viewerTitle = ""
        uploadingImage = false
        uploadingFile = false
        lastSentFileName = ""
        tippingNote = ""
        publishing = false
        noteCoins = emptyMap()
        repo.noteCoins = emptyMap()
        myHomeTab = 0
    }

    // ------------------------------------------------------------ 通用分页加载

    private suspend fun <T> loadInto(
        current: FeedState<T>,
        update: (FeedState<T>) -> Unit,
        reset: Boolean,
        loader: suspend (Int) -> Paged<T>,
    ) {
        if (reset) {
            update(
                FeedState(
                    items = emptyList(), loading = true, hasMore = true,
                    page = -1, loaded = true,
                )
            )
        } else {
            if (!current.hasMore || current.loading || current.loadingMore) return
            update(current.copy(loadingMore = true, error = null))
        }

        val page = if (reset) 0 else current.page + 1
        try {
            val res = loader(page)
            update(
                FeedState(
                    items = (if (reset) emptyList() else current.items) + res.items,
                    hasMore = res.hasMore,
                    page = page,
                    loaded = true,
                )
            )
        } catch (t: Throwable) {
            update(
                current.copy(
                    loading = false, loadingMore = false,
                    error = friendlyError(t), loaded = true,
                )
            )
        }
    }

    // ------------------------------------------------------------ 发现

    var category by mutableStateOf(Constants.CATEGORIES.first())
        private set

    var discoverFeed by mutableStateOf(FeedState<Note>())
        private set

    fun selectCategory(index: Int) {
        if (index !in Constants.CATEGORIES.indices) return
        if (Constants.CATEGORIES[index].key == category.key) return
        category = Constants.CATEGORIES[index]
        loadDiscover(reset = true)
    }

    fun loadDiscover(reset: Boolean = false) {
        val cat = category
        viewModelScope.launch {
            loadInto(discoverFeed, { discoverFeed = it }, reset) { page ->
                val p = Api.discover(client, cat.type, page, extra = cat.extra)
                resolveAuthors(p.items)
                Paged(applyAuthors(p.items), p.hasMore)
            }
        }
    }

    // ------------------------------------------------------------ 关注

    var followFeed by mutableStateOf(FeedState<Note>())
        private set

    fun loadFollow(reset: Boolean = false) {
        viewModelScope.launch {
            loadInto(followFeed, { followFeed = it }, reset) { page ->
                val p = Api.follow(client, page)
                resolveAuthors(p.items)
                Paged(applyAuthors(p.items), p.hasMore)
            }
        }
    }

    // ------------------------------------------------------------ 搜索

    var searchKeyword by mutableStateOf("")
        private set

    var searchFeed by mutableStateOf(FeedState<SearchItem>())
        private set

    fun search(keyword: String) {
        searchKeyword = keyword
        if (keyword.isBlank()) {
            searchFeed = FeedState()
            return
        }
        viewModelScope.launch {
            loadInto(searchFeed, { searchFeed = it }, true) { page ->
                Api.search(client, keyword, page)
            }
        }
    }

    fun loadMoreSearch() {
        if (searchKeyword.isBlank()) return
        viewModelScope.launch {
            loadInto(searchFeed, { searchFeed = it }, false) { page ->
                Api.search(client, searchKeyword, page)
            }
        }
    }

    // ------------------------------------------------------------ 我的

    var myProfile by mutableStateOf<UserProfile?>(null)
        private set

    var myStats by mutableStateOf<MyStats?>(null)
        private set

    var myNotesFeed by mutableStateOf(FeedState<Note>())
        private set

    var likedFeed by mutableStateOf(FeedState<Note>())
        private set

    /** 我收藏的笔记 */
    var collectionFeed by mutableStateOf(FeedState<Note>())
        private set

    var noticesFeed by mutableStateOf(FeedState<Notice>())
        private set

    var userFeed by mutableStateOf(FeedState<Note>())
        private set

    fun refreshMe() {
        viewModelScope.launch { refreshMeNow() }
    }

    private suspend fun refreshMeNow() {
        val mid = repo.currentMid()
        if (mid.isBlank()) return
        runCatching { Api.profile(client, mid) }.onSuccess { myProfile = it }
        runCatching { Api.myStats(client) }.onSuccess { myStats = it }
        runCatching { Api.coinState(client) }.onSuccess { coinState = it }
    }

    fun loadMyNotes(reset: Boolean = false) {
        val mid = repo.currentMid()
        viewModelScope.launch {
            loadInto(myNotesFeed, { myNotesFeed = it }, reset) { page ->
                val p = Api.userNotes(client, mid, Constants.USER_NOTE_ALL, page)
                resolveAuthors(p.items)
                Paged(applyAuthors(p.items), p.hasMore)
            }
        }
    }

    fun loadLiked(reset: Boolean = false) {
        viewModelScope.launch {
            loadInto(likedFeed, { likedFeed = it }, reset) { page ->
                // 这个接口不带作者字段，得靠 home_id 单独补（见 resolveAuthors）
                val p = Api.likedNotes(client, page)
                resolveAuthors(p.items)
                Paged(applyAuthors(p.items), p.hasMore)
            }
        }
    }

    fun loadCollection(reset: Boolean = false) {
        viewModelScope.launch {
            loadInto(collectionFeed, { collectionFeed = it }, reset) { page ->
                val p = Api.collectionNotes(client, page)
                resolveAuthors(p.items)
                Paged(applyAuthors(p.items), p.hasMore)
            }
            if (collectionFeed.loaded) {
                collectedIds = collectionFeed.items.map { it.id }.toSet()
                collectedLoaded = true
            }
        }
    }

    /** 收藏 / 取消收藏；成功后同步刷新收藏列表 */
    fun toggleCollection(noteId: String, on: Boolean, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Api.setCollection(client, noteId, on)
                collectedIds = if (on) collectedIds + noteId else collectedIds - noteId
                onDone(true)
            } catch (t: Throwable) {
                toast = if (on) "收藏失败：${friendlyError(t)}" else "取消收藏失败：${friendlyError(t)}"
                onDone(false)
            }
        }
    }

    /** 已收藏的笔记 id 集合（详情页用来判断按钮状态） */
    var collectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private var collectedLoaded = false

    fun ensureCollected() {
        if (collectedLoaded) return
        viewModelScope.launch {
            runCatching { Api.collectionNotes(client, 0).items.map { it.id }.toSet() }
                .onSuccess {
                    collectedIds = it
                    collectedLoaded = true
                }
        }
    }

    fun loadNotices(reset: Boolean = false) {
        viewModelScope.launch {
            loadInto(noticesFeed, { noticesFeed = it }, reset) { page ->
                Api.notices(client, "2", page)
            }
        }
    }

    fun loadUserNotes(homeId: String, reset: Boolean = false) {
        viewModelScope.launch {
            loadInto(userFeed, { userFeed = it }, reset) { page ->
                val p = Api.userNotes(client, homeId, Constants.USER_NOTE_ALL, page)
                resolveAuthors(p.items)
                Paged(applyAuthors(p.items), p.hasMore)
            }
        }
    }

    // ------------------------------------------------------------ 关注 / 粉丝
    //
    // 接口 `GetSTBuddies` 是分页的（一页 10 条，`over=true` 才是取完）。
    // 早先只拉了 page=0，所以关注了 40 个人也只看得到 10 个 —— 这就是「显示不全」。

    /** 0 = 我关注的（type=2），1 = 粉丝（type=3） */
    var socialTab by mutableStateOf(0)
        private set

    var followingFeed by mutableStateOf(FeedState<Buddy>())
        private set

    var fansFeed by mutableStateOf(FeedState<Buddy>())
        private set

    val socialFeed: FeedState<Buddy>
        get() = if (socialTab == 0) followingFeed else fansFeed

    val socialList: List<Buddy> get() = socialFeed.items

    private fun socialType(tab: Int = socialTab): String = if (tab == 0) "2" else "3"

    fun selectSocialTab(index: Int) {
        if (index !in 0..1) return
        if (index == socialTab && socialFeed.loaded) return
        socialTab = index
        loadSocial(reset = true)
    }

    /** 首次进入时拉一次；已加载过就不重复打接口 */
    fun loadSocialIfNeeded() {
        if (!socialFeed.loaded) loadSocial(reset = true)
    }

    /**
     * @param reset true = 从头拉第一页（首次进入 / 切 tab / 点刷新）；
     *              false = 续拉下一页（滚到底自动触发）
     */
    fun loadSocial(reset: Boolean = true) {
        val tab = socialTab      // 记下发起时的 tab，回来时别写错列
        val update: (FeedState<Buddy>) -> Unit =
            { if (tab == 0) followingFeed = it else fansFeed = it }

        viewModelScope.launch {
            loadInto(
                if (tab == 0) followingFeed else fansFeed,
                update,
                reset,
            ) { page -> Api.buddies(client, socialType(tab), page) }
        }
    }

    /** 滚到底自动续拉下一页 */
    fun loadMoreSocial() {
        val s = socialFeed
        if (!s.hasMore || s.loading || s.loadingMore) return
        loadSocial(reset = false)
    }

    // ------------------------------------------------------------ 写操作

    fun likeNote(noteId: String, like: Boolean, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Api.likeNote(client, noteId, like)
                onDone(true)
            } catch (t: Throwable) {
                toast = "点赞失败：${friendlyError(t)}"
                onDone(false)
            }
        }
    }

    fun postComment(
        noteId: String,
        text: String,
        parentId: String = "0",
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                Api.postComment(client, text, noteId, parentId)
                onDone(true)
            } catch (t: Throwable) {
                toast = "发送失败：${friendlyError(t)}"
                onDone(false)
            }
        }
    }

    /** 关注 / 取消关注；成功后把「关注与粉丝」标成待重载 */
    fun followUser(homeId: String, follow: Boolean = true, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Api.followUser(client, homeId, follow)
                followingFeed = followingFeed.copy(loaded = false)
                fansFeed = fansFeed.copy(loaded = false)
                onDone(true)
            } catch (t: Throwable) {
                toast = if (follow) "关注失败：${friendlyError(t)}" else "取消关注失败：${friendlyError(t)}"
                onDone(false)
            }
        }
    }

    // ------------------------------------------------------------ 投币
    //
    // 接口：STCoin2Note（nid + count）。平台每篇文章最多投 2 个币，
    // 服务端不给「我投过几枚」的字段，所以本地按 nid 记一份。

    /** 每篇文章的投币上限 */
    val coinPerNoteMax: Int get() = Constants.COIN_PER_NOTE_MAX

    /** 本地记录：note_id -> 我已投出的币数 */
    var noteCoins by mutableStateOf(repo.noteCoins)
        private set

    /** 正在投币的文章 id（空 = 空闲），用来给按钮转圈 */
    var tippingNote by mutableStateOf("")
        private set

    fun coinsGiven(noteId: String): Int = noteCoins[noteId] ?: 0

    /** 还能投几枚（0 表示这篇已经投满） */
    fun coinsLeft(noteId: String): Int =
        (coinPerNoteMax - coinsGiven(noteId)).coerceAtLeast(0)

    /**
     * 给文章投币。
     *
     * @param count 这次投几枚，会按「剩余额度」自动夹紧
     * @param onDone true = 成功；第二个参数是本次实际投出的数量
     */
    fun tipNote(noteId: String, count: Int = 1, onDone: (Boolean, Int) -> Unit = { _, _ -> }) {
        if (noteId.isBlank()) return
        if (tippingNote.isNotBlank()) return
        if (count <= 0) return

        val left = coinsLeft(noteId)
        if (left <= 0) {
            toast = "这篇文章已经投满 $coinPerNoteMax 个币了"
            onDone(false, 0)
            return
        }
        val real = count.coerceAtMost(left)

        if ((coinState?.coinCount ?: 0) < real) {
            toast = "硬币不够了，先去签到攒几枚吧"
            onDone(false, 0)
            return
        }

        tippingNote = noteId
        viewModelScope.launch {
            try {
                val r = Api.coinToNote(client, noteId, real)
                val res = r.intOr("res", -1)
                if (res != 0) {
                    toast = "投币失败：" + r.str("error").ifBlank {
                        r.str("remind_hint").ifBlank { "服务端返回 $res" }
                    }
                    onDone(false, 0)
                } else {
                    val map = noteCoins.toMutableMap()
                    map[noteId] = (map[noteId] ?: 0) + real
                    noteCoins = map
                    repo.noteCoins = map
                    toast = "投币成功，已投 ${map[noteId]}/$coinPerNoteMax"
                    onDone(true, real)
                }
                // 余额变了，顺手刷新一下积分状态
                refreshCoin()
            } catch (t: Throwable) {
                toast = "投币失败：${friendlyError(t)}"
                onDone(false, 0)
            } finally {
                tippingNote = ""
            }
        }
    }

    // ------------------------------------------------------------ 发布笔记
    //
    // 接口：UploadNote2（**comment 签名变体**）+ urls + title + content + type。
    // 实测（2026-09-25）能真发出去，服务端限制「两贴间隔至少 5 分钟」。

    /** 发布分区（默认第一个：日常） */
    var publishCategory by mutableStateOf(Constants.CATEGORIES.first())
        private set

    var publishing by mutableStateOf(false)
        private set

    fun selectPublishCategory(index: Int) {
        if (index in Constants.CATEGORIES.indices) publishCategory = Constants.CATEGORIES[index]
    }

    /**
     * 发布一篇笔记。
     *
     * 先把本地图片逐张传到 `stupnote` 目录（笔记配图目录，不需要会员），
     * 再把地址列表 + 标题 + 正文发给 `UploadNote2`。
     *
     * @param images 已经读成字节的本地图片，顺序即展示顺序（第一张做封面）
     */
    fun publishNote(
        title: String,
        text: String,
        images: List<PendingImage> = emptyList(),
        onDone: (Boolean) -> Unit = {},
    ) {
        if (publishing) return
        val t = title.trim()
        if (t.isEmpty()) {
            toast = "标题不能为空"
            onDone(false)
            return
        }
        publishing = true
        viewModelScope.launch {
            var ok = false
            try {
                val urls = mutableListOf<String>()
                for (img in images) {
                    val up = Api.uploadImage(
                        client, img.bytes, img.fileName, img.mimeType,
                        dirName = Constants.IMAGE_DIR_NOTE,
                        allowFallback = false,
                    )
                    when {
                        up.illegal -> {
                            toast = "图片「${img.fileName}」被平台判为违规，换一张试试"
                            return@launch
                        }
                        up.url.isBlank() -> {
                            toast = "图片上传失败：${up.error.ifBlank { "未知原因" }}"
                            return@launch
                        }
                        else -> urls += up.url
                    }
                }

                val r = Api.publishNote(
                    client = client,
                    title = t,
                    text = text.trim(),
                    imageUrls = urls,
                    type = publishCategory.type,
                )
                val res = r.intOr("res", -1)
                if (res == 0) {
                    val id = r.str("id")
                    toast = if (id.isBlank()) "发布成功" else "发布成功（id $id）"
                    // 「我的主页 / 我的笔记」下次进来重新拉，保证看得到刚发的
                    myNotesFeed = FeedState()
                    myHomeTab = 0
                    ok = true
                } else {
                    toast = "发布失败：" + r.str("error").ifBlank {
                        r.str("remind_hint").ifBlank { "服务端返回 $res" }
                    }
                }
            } catch (t2: Throwable) {
                toast = "发布失败：${friendlyError(t2)}"
            } finally {
                publishing = false
                onDone(ok)
            }
        }
    }

    // ------------------------------------------------------------ 私信

    /** 互关好友 = 私信会话列表 */
    var buddies by mutableStateOf<List<Buddy>>(emptyList())
        private set

    var buddiesLoading by mutableStateOf(false)
        private set

    var buddiesError by mutableStateOf<String?>(null)
        private set

    /** 当前打开的会话对方 mid（空 = 没打开） */
    var letterTargetMid by mutableStateOf("")
        private set

    /** 会话对方资料（GET_LETTER_MESSAGE flag=2 会带回来） */
    var letterPartner by mutableStateOf<Buddy?>(null)
        private set

    var letters by mutableStateOf<List<Letter>>(emptyList())
        private set

    var lettersLoading by mutableStateOf(false)
        private set

    var lettersError by mutableStateOf<String?>(null)
        private set

    /** GetSTLetterMessage 的原始返回，私信页「查看原始报文」用 */
    var lettersRaw by mutableStateOf("")
        private set

    var sendingLetter by mutableStateOf(false)
        private set

    /** 静默刷新正在进行（不显示 loading，只用来去重） */
    var letterRefreshing by mutableStateOf(false)
        private set

    /** 签到 / 私信解锁状态 */
    var coinState by mutableStateOf<CoinState?>(null)
        private set

    var checkingIn by mutableStateOf(false)
        private set

    val myMid: String get() = repo.currentMid()

    fun loadBuddies() {
        buddiesLoading = true
        buddiesError = null
        viewModelScope.launch {
            try {
                // 会话列表要拿全部互关好友，所以这里翻页取全
                buddies = Api.allBuddies(client, "1")
            } catch (t: Throwable) {
                buddiesError = friendlyError(t)
            } finally {
                buddiesLoading = false
            }
        }
    }

    fun openLetter(otherMid: String, partner: Buddy? = null) {
        letterTargetMid = otherMid
        letterPartner = partner
        letters = emptyList()
        lettersRaw = ""
        lettersError = null
        // 进会话先把本地归档里的东西摆出来，这样「防撤回」的历史消息立刻可见
        if (antiRecall) {
            letters = Letter.decodeArchive(archive[otherMid].orEmpty())
                .let { Letter.ordered(it) }
        }
        loadLetters()
    }

    /**
     * 拉取会话消息。
     *
     * @param silent true = 静默刷新（10 秒一次的轮询走这条）：
     *               不亮 loading、不写错误提示，只有内容真的变了才更新界面。
     */
    fun loadLetters(flag: String = "2", silent: Boolean = false) {
        val mid = letterTargetMid
        if (mid.isBlank()) return
        if (silent) {
            if (letterRefreshing || lettersLoading) return
            letterRefreshing = true
        } else {
            lettersLoading = true
            lettersError = null
        }
        viewModelScope.launch {
            try {
                val thread = Api.letters(client, mid, flag = flag)
                // 位置可能已经换到别的会话了，丢弃过期结果
                if (letterTargetMid != mid) return@launch
                applyThread(thread, silent = silent)
            } catch (t: Throwable) {
                // 静默刷新失败不打扰用户（网络抖动很常见）
                if (!silent) lettersError = friendlyError(t)
            } finally {
                if (silent) letterRefreshing = false else lettersLoading = false
            }
        }
    }

    /** 10 秒一次的静默刷新入口 */
    fun refreshLettersSilently() {
        if (letterTargetMid.isBlank()) return
        if (sendingLetter || uploadingImage || uploadingFile) return
        loadLetters(silent = true)
    }

    private fun applyThread(thread: com.tb.fkst.data.LetterThread, silent: Boolean) {
        val fresh = thread.letters

        // 防撤回：本次没返回、但本地归档里有（且在本次时间范围内）的消息，判为被撤回
        val merged = if (antiRecall) {
            val prev = Letter.decodeArchive(archive[letterTargetMid].orEmpty())
            val (all, gone) = Letter.mergeRecalled(fresh, prev)
            if (gone.isNotEmpty() && !silent) {
                toast = "防撤回：保留了 ${gone.size} 条对方撤回的消息"
            }
            all
        } else fresh

        lettersRaw = thread.raw
        // 内容没变就不动 state，避免无谓重组 + 聊天列表往下弹
        val changed = merged.size != letters.size ||
            merged.map { it.stableKey } != letters.map { it.stableKey } ||
            merged.map { it.recalled } != letters.map { it.recalled }
        if (changed) letters = merged
        // 归档里要带上「已撤回」的那些，这样它们下次还认得出来
        saveArchive(merged)

        // 会话详情里的 member 是权威资料，但它只有 id/nick_name/logo；
        // 从好友列表点进来时手上有更全的 Buddy，所以这里做合并而不是直接覆盖。
        thread.member?.let { m ->
            val cur = letterPartner
            letterPartner = when {
                cur == null -> m
                cur.homeId.isNotBlank() && cur.homeId != m.homeId -> m
                else -> cur.copy(
                    homeId = cur.homeId.ifBlank { m.homeId },
                    nickName = cur.nickName.ifBlank { m.nickName },
                    avatar = cur.avatar.ifBlank { m.avatar },
                )
            }
        }
    }

    /** 把当前会话的最新一段写进本地归档 */
    private fun saveArchive(items: List<Letter>) {
        val mid = letterTargetMid
        if (mid.isBlank() || !antiRecall || items.isEmpty()) return
        val json = Letter.encodeArchive(items)
        if (json == archive[mid]) return
        archive = archive + (mid to json)
        repo.letterArchive = archive
    }

    fun sendLetter(text: String, onDone: (Boolean) -> Unit = {}) {
        val mid = letterTargetMid
        if (mid.isBlank() || text.isBlank()) return
        sendingLetter = true
        viewModelScope.launch {
            try {
                Api.sendLetter(client, mid, text)
                onDone(true)
                // 发完重新拉一次，保证顺序和服务端一致
                loadLetters()
            } catch (t: Throwable) {
                toast = "私信发送失败：${friendlyError(t)}"
                onDone(false)
            } finally {
                sendingLetter = false
            }
        }
    }

    /** 正在上传/发送图片 */
    var uploadingImage by mutableStateOf(false)
        private set

    // ---------------- 会话置顶与备注 ----------------
    //
    // 平台没有「会话置顶」接口，置顶纯本地存；
    // 备注优先写服务端 SetSTFollowMark（需要关系 id），同时本地存一份兜底，
    // 因为服务端备注只对已存在关注关系的人生效。

    var pinnedConversations by mutableStateOf(repo.pinnedConversations)
        private set

    var remarks by mutableStateOf(repo.remarks)
        private set

    fun isPinned(homeId: String): Boolean = pinnedConversations.contains(homeId)

    fun togglePin(homeId: String) {
        if (homeId.isBlank()) return
        val now = pinnedConversations
        pinnedConversations = if (homeId in now) now - homeId else now + homeId
        repo.pinnedConversations = pinnedConversations
        toast = if (homeId in pinnedConversations) "已置顶该会话" else "已取消置顶"
    }

    /** 会话展示名：本地备注 > 服务端备注 > 昵称 */
    fun nameOf(buddy: Buddy): String = buddy.nameWith(remarks[buddy.homeId])

    fun nameOf(homeId: String): String =
        remarks[homeId]?.takeIf { it.isNotBlank() }
            ?: buddies.firstOrNull { it.homeId == homeId }?.nameWith(null)
            ?: homeId

    fun remarkOf(homeId: String): String = remarks[homeId] ?: ""

    fun setRemark(homeId: String, remark: String, relationId: String = "") {
        if (homeId.isBlank()) return
        val text = remark.trim()
        val map = remarks.toMutableMap()
        if (text.isEmpty()) map.remove(homeId) else map[homeId] = text
        remarks = map
        repo.remarks = map
        toast = if (text.isEmpty()) "已清除备注" else "备注已保存"
        if (relationId.isNotBlank()) {
            // 服务端备注失败不影响本地备注，所以静默处理
            viewModelScope.launch {
                runCatching { Api.followRemark(client, homeId, relationId, text) }
            }
        }
    }

    /** 置顶的排前面 */
    val sortedBuddies: List<Buddy>
        get() = buddies.sortedWith(
            compareByDescending<Buddy> { pinnedConversations.contains(it.homeId) }
                .thenBy { nameOf(it) }
        )

    /**
     * 发图片私信。
     * 先上传（私信图库要会员 → 自动回落到普通图库），再把地址当图片消息发出去。
     */
    fun sendLetterImage(bytes: ByteArray, fileName: String, mimeType: String) {
        val mid = letterTargetMid
        if (mid.isBlank()) return
        if (uploadingImage || uploadingFile) return
        uploadingImage = true
        viewModelScope.launch {
            try {
                val up = Api.uploadImage(client, bytes, fileName, mimeType)
                when {
                    up.illegal -> toast = "这张图被平台判为违规，换一张试试"
                    up.url.isBlank() ->
                        toast = "图片上传失败：${up.error.ifBlank { "未知原因" }}"
                    else -> {
                        val r = Api.sendLetterImage(client, mid, up.url)
                        if (r.intOr("res", -1) != 0) {
                            toast = "图片发送失败：" + r.str("error").ifBlank {
                                r.str("remind_hint").ifBlank { "服务端返回 ${r.opt("res")}" }
                            }
                        } else {
                            if (up.needVip) {
                                toast = "已发送（私信图库需会员，已自动改用普通图库）"
                            }
                            loadLetters()
                        }
                    }
                }
            } catch (t: Throwable) {
                toast = "图片发送失败：${friendlyError(t)}"
            } finally {
                uploadingImage = false
            }
        }
    }

    fun refreshCoin() {
        viewModelScope.launch {
            runCatching { Api.coinState(client) }.onSuccess { coinState = it }
        }
    }

    // ---------------- 私信发文件 ----------------

    /** 正在上传/发送文件 */
    var uploadingFile by mutableStateOf(false)
        private set

    /** 上一次发出去的文件名（用于会话里给个提示） */
    var lastSentFileName by mutableStateOf("")
        private set

    /**
     * 发文件私信。
     *
     * 流程：上传到 `OSSUploadFile2.php`（`stupnotefile` 目录）→ 发文件消息。
     * 服务端如果不认「文件消息」这种类型，[Api.sendLetterFile] 会自动退化成
     * 文本消息 + 附件链接，所以这条链路是「尽力发文件、保底发链接」。
     */
    fun sendLetterFile(bytes: ByteArray, fileName: String, mimeType: String) {
        val mid = letterTargetMid
        if (mid.isBlank()) return
        if (uploadingFile || uploadingImage) return
        uploadingFile = true
        viewModelScope.launch {
            try {
                val up = Api.uploadFile(client, bytes, fileName, mimeType)
                when {
                    up.url.isBlank() -> toast = "文件上传失败：" + up.error.ifBlank { "未知原因" }
                    else -> {
                        val (ok, fellBack, resp) = Api.sendLetterFile(client, mid, up.url, fileName)
                        when {
                            ok -> {
                                lastSentFileName = fileName
                                if (fellBack) {
                                    toast = "文件消息不被支持，已改发链接（$fileName）"
                                } else {
                                    toast = "文件已发送：$fileName"
                                }
                                loadLetters()
                            }
                            else -> toast = "文件发送失败：" + resp.str("error").ifBlank {
                                resp.str("remind_hint").ifBlank { "服务端返回 ${resp.opt("res")}" }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                toast = "文件发送失败：${friendlyError(t)}"
            } finally {
                uploadingFile = false
            }
        }
    }
    /** 自动签到只在本进程里跑一次 */
    private var autoChecked = false

    /**
     * 启动 / 登录后自动签到。
     * 平台规则：连续签到满 3 天才能对外发私信，所以默认开启，静默执行。
     */
    fun maybeAutoCheckIn() {
        if (!autoCheckIn || autoChecked) return
        autoChecked = true
        if (coinState?.signedToday == true) return
        doCheckIn(silent = true)
    }

    fun doCheckIn(silent: Boolean = false) {
        if (checkingIn) return
        checkingIn = true
        viewModelScope.launch {
            try {
                val r = Api.checkIn(client)
                val already = r.optBoolean("already", false)
                val state = runCatching { Api.coinState(client) }.getOrNull()
                coinState = state
                if (!silent) {
                    toast = if (already) {
                        "今日已签到（连续 ${state?.signDays ?: 0} 天）"
                    } else {
                        val unlocked = if (state?.canSendLetter == true) " · 已解锁私信" else ""
                        "签到成功！连续 ${state?.signDays ?: 0} 天 · 积分 ${state?.coinCount ?: 0}$unlocked"
                    }
                }
            } catch (t: Throwable) {
                if (!silent) toast = "签到失败：${friendlyError(t)}"
            } finally {
                checkingIn = false
            }
        }
    }
}
