# 疯狂刷题 · Android 客户端

基于本项目 Python 版协议分析（`fkst_sdk/`）实现的原生 Android 客户端。
**Kotlin + Jetpack Compose + Material 3**，支持 **莫奈（Material You）动态取色**。

---

## 一、功能清单

底部导航五项：**试卷 / 发现 / 搜索 / 私信 / 我的**（原「关注」tab 已并入发现页的二级切换）

**第一次打开 App** 会先走两步引导（之后不再出现）：
1. **用户政策**：非官方第三方客户端的使用须知与隐私说明，勾选同意才能继续；
2. **为什么没有「刷题」模块**：讲清楚题库接口不接受筛选参数、在线答题页对第三方拒绝访问
   这两个服务端限制 —— 做过一版刷题界面又删掉了，这里给用户一个交代，
   同时说明现在改成了**试卷库**（能看题目和解析，但不能在线交卷）。

| 模块 | 能力 |
|---|---|
| 首次引导 | 用户政策（同意一次，持久化）+ 刷题模块说明页，只在第一次打开时出现 |
| 试卷库 | 底部导航第一位（`PaperLibraryScreen`）：**按年级 / 教材版本筛选浏览真实试卷**（`GetShuatiPaper5`）、**搜试卷**（`GetSearchPapers7`，comment 签名）、**收藏**（`CollectShuatiPaper`，可逆验证过）、「我收藏的试卷」列表；点进详情页能看**整份卷子的题目 + 答案 + 解析**（`GetZJPaperById5`，按大题分组，答案默认收起、可一键展开）。限制：只有 `type=1` 的同步卷有题目；**在线交卷做不到**（官方 H5 恒回「非法访问-1」） |
| 更新检查 | 启动时**静默**从 GitHub Releases（`releases` 列表，自行挑首个 `v?x.y.z` 规范 tag）查一次新版本（pre-release 也能被检测到，故测试版更新提示照常工作）；有更新弹窗（去下载 / 跳过此版本 / 稍后），设置页也有手动「检查更新」按钮；tag 解析不出 `v?x.y.z` 或网络失败都静默跳过，绝不打扰 |
| 登录 | 手机号 + 密码（`STAccountLogin3`，`verify_type=1`），会话持久化，记住手机号 |
| 发现 | 顶部「推荐 / 关注」二级切换；推荐 = 11 个分区（日常 / 好物 / 试卷 / 难题趣题 / 学习经验 / 绘画 / 学习Plog / 手工种植 / 飞花令 / 作文随笔 / 书法），分页 + 滚动到底自动加载 |
| 搜索 | 文章 / 题目搜索（`GetSearchNotes`，返回 `matches`），分页 |
| 文章详情 | H5 密文正文**解密**展示、正文 JSON 自动拆成纯文本、**正文里的 `[音频] <url>` 行渲染成播放器**、多图预览、点赞、**收藏**、**投币（每篇最多 2 枚）**、**文末作者卡片（点击进作者主页）** |
| 发布笔记 | **「我的」→ 发布笔记** 或我的主页顶栏的笔形按钮：标题（必填）+ 正文 + 最多 9 张配图（第一张当封面）+ **最多 3 段音频（服务端只收 mp3）** + 分区，走 `UploadNote2` + `OSSUploadAudio2.php` |
| 编辑 / 删除自己的笔记 | 详情页右上角「更多」（只对本人笔记出现）：**编辑配图**（`UpdateUploadNoteUrls`，可删图、可加新图，**全量覆盖**保存）+ **删除笔记**（二次确认）。标题和正文服务端没开放接口，改不了 |
| 图片查看 | 点文章里的图进全屏查看：**双指缩放 / 拖动 / 双击放大**、左右滑动切图、**保存原图到相册（无压缩无水印）** |
| 排版 | 列表页顶栏右侧的**排版按钮**：单列大卡 ↔ 一排两个（双列网格）；设置页也有同样的开关，偏好全局持久化 |
| 评论 | 评论列表分页、楼中楼（`replies`）与「展开 N 条回复」、发评论、回复评论、评论点赞；**发评论可附带一张图片**（上传到笔记图库后随评论提交 `content_url`），评论与回复里的配图在列表里展示、点击全屏查看 |
| 消息通知 | 不再只显示标题，按动作类型还原语义（「X 评论了 / 赞了 / 投币了你的笔记」+ 原文 + 相关文章标题），系统消息走中性样式；顶部按 **全部 / 评论 / 点赞 / 投币 / 系统** 筛选；**点整条通知跳转到对应文章详情** |
| 用户主页 | 资料卡（昵称/头像/地区/关注/粉丝/发布/答题）+ TA 的笔记 + **关注/取关**（`STFollow`）+ 私信入口；**资料卡随翻页折叠收起** |
| 我的主页 | 点「我的」页顶部资料卡进入，三个 tab：**发布 / 赞过 / 收藏**；统计项可点进关注与粉丝；**资料卡与切换条随翻页一起折叠** |
| 关注与粉丝 | 两个 tab 分别列出「我关注的（`GetSTBuddies type=2`）」和「粉丝（type=3）」，**分页取全（滚到底自动续拉）**，可直接进主页或私信 |
| 收藏 | 「我的收藏」列表（`GetCollectionShuatiNote1`）+ 文章页一键收藏（走 `CollectShuatiNote`，**不是** `UpdateSTCollection` —— 后者返回 `res:0` 但不落库，是个假成功的接口） |
| 私信 | 底部导航 tab；会话列表（互关好友）、气泡对话界面、收发消息；**新消息在最下 + 每 10 秒静默刷新**；**会话置顶**、**好友备注**、**发送图片**、**发送文件**、**防撤回**；受平台「连续签到 3 天」限制 |
| 签到 | 每日签到领积分，连续 3 天解锁私信；「我的」和「私信」页都有签到按钮，**支持自动签到**（打开 App 自动完成，可在设置里关） |
| 我的 | 资料卡（点进我的主页）、四格统计（关注/粉丝可点进列表）、签到卡、我的主页、**发布笔记**、我的笔记、我的收藏、我赞过的、关注与粉丝、私信、消息通知、外观设置、退出登录 |
| 外观 | 莫奈取色开关、深色模式（跟随系统/浅色/深色）、**卡片样式（默认 / 透明卡片）**、**自定义壁纸**、**列表排版（单列 / 一排两个）**、配色预览、自动签到开关、**私信防撤回开关**、**存储与缓存**、关于（版本号取 `BuildConfig`，检查更新在这里） |

### 关于「排版按钮」

`ui/components/NoteFeedList.kt` 一份代码管两种排版：`columns = 1` 走
`LazyColumn` + `NoteCard`，`columns = 2` 走 `LazyVerticalGrid(Fixed(2))` + `NoteGridCard`。
发现 / 关注 / 搜索 / 我的主页 / 我的笔记 都吃同一个偏好（`Repository.listColumns`），
顶栏按钮和设置页的开关都改它，改完立刻生效。

`NoteFeedList` 还有一个可选的 `header` 参数：传进去的内容会作为列表第一项渲染
（双列时用 `GridItemSpan(maxLineSpan)` 占满整行）。**主页的「资料卡随翻页折叠」就是靠它做的** ——
把资料卡放进列表里，往下翻自然被划走，而不是钉在顶上；配合
`TopAppBarDefaults.enterAlwaysScrollBehavior` + `Modifier.nestedScroll(...)`，
顶栏也会在往下滑时隐藏、往回滑时立刻出现。

### 关于「发布笔记」

`ui/screens/PublishNoteScreen.kt`：标题（必填）+ 正文 + 最多 9 张配图 + 分区。
点「发布」后先把每张图传到 `stupnote`（笔记配图目录，不需要会员），
再把 `urls` + `title` + `content` + `type` 一并发给 `UploadNote2`。

协议上有两个坑（见 `API_RESEARCH.md` 第 14 节）：

- `UploadNote2` 用的是 **comment 签名变体**，用通用签名只会回 `{"res":1,"remind_hint":"非法请求2"}`；
- 服务端限制 **两篇间隔至少 5 分钟**，发太频回 `res=2`，界面里把这条写进了提示卡。

`content` 字段拼的是官方那套包体 JSON（`Api.buildNoteContent()`），
因为列表接口的正文就存在这个字段里、`Note.decodeContent()` 也是从里面抽纯文本的。

### 关于「私信新消息在最下 + 10 秒静默刷新」

- **排序**：`Letter.ordered()` 按 `createdAt` 稳定升序（老的在上、新的贴着输入框）。
  服务端返回顺序不保证，全都没时间戳时保持原顺序。
- **刷新**：会话页 `LaunchedEffect(vm.letterTargetMid)` 里 `while(true) { delay(10s); vm.refreshLettersSilently() }`。
  静默 = 不亮 loading、不写错误提示、内容没变就不动 state（避免无谓重组和列表跳动）。
- **滚动**：只有用户本来就在底部（`listState.canScrollForward == false`）时才自动跟随新消息，
  正在往上翻历史时不会被拽下去。

### 关于「私信防撤回」

平台没有撤回接口。客户端能观察到的「撤回」= 这条消息从 `GetSTLetterMessage` 的返回里消失了。

打开开关后，每段会话都会在本地留一份归档（`Repository.letterArchive`，存原始报文 + 已算好的方向），
下次刷新时发现某条不见了，就把它标成 `recalled = true` 灰显留在列表里，并标注
「对方撤回了一条消息 · 本地保留」。设置页和会话右上角菜单都能开关，关掉时顺手清空备份。

⚠️ 判定有个必须绕开的坑：`GetSTLetterMessage` 是**按页返回最新 N 条**的，
归档里比「本页最老那条」更早的消息只是没被这一页覆盖，**并不是被撤回**。
所以判定时用本页最老的 `created_at` 当水位线，更早的一律不算；整页都没有时间戳时干脆不判。

### 关于「缓存清理」

设置页的「存储与缓存」卡片，`core/CacheCleaner.kt` 负责实际动作：

- 清 Coil 的内存缓存 + 磁盘缓存（`cacheDir/image_cache`）+ `cacheDir` / `externalCacheDir` 下的临时文件；
- **不动**用户数据：壁纸（`filesDir`）、备注 / 置顶 / 投币记录（SharedPreferences）、账号登录态；
- 也刻意不碰 `codeCacheDir`（ART 编译产物，删了不丢数据但会拖慢启动）。

另外给了两个独立入口：「清空防撤回备份」和「清空作者缓存」。

### 关于「赞过 / 收藏里的匿名」

不是 UI 的问题，是**接口真的不返回作者**：

```
GetLikeShuatiNote 的 notes[0] 键 = ["id","title","type","thumb","tagids","home_id","link_id"]
```

有 `home_id`，没有 `nick_name` / `logo`，所以早先只能显示「匿名」。
现在 `AppViewModel.resolveAuthors()` 会拿 `home_id` 去 `GetSTUserData` 补资料：

- **去重**：同一作者只查一次；
- **持久化缓存**（`Repository.authorCache`）：跨次启动有效；
- **后台串行补拉**：列表先用缓存/「用户 <mid>」占位渲染，补到之后 `patchFeeds()` 刷新各列表。

`GetSTUserData` 不支持批量（`home_id` 传逗号拼接会回 `{"res":0,"open_status":3}`），所以只能一个一个来。

### 关于「关注 / 粉丝显示不全」

`GetSTBuddies` 是分页接口（一页 10 条，`over=true` 才是取完），早先只拉了 `page=0`，
所以关注了 40 个人也只看得到 10 个。现在：

- `Api.buddies()` 返回 `Paged<Buddy>`（`hasMore = !over`）；
- `SocialScreen` 滚到底自动续拉，底部显示「已加载 N / 总数」；
- tab 上的数字用资料里的总数（`myProfile.followCount/fansCount`），不是已加载条数；
- 私信会话列表需要全量互关好友，用 `Api.allBuddies()` 循环取完。

### 关于「文末作者卡片」

文章正文结束后会有一张作者卡片（头像 + 昵称 + mid），点一下进 TA 的主页。
作者名同样走 `vm.authorNameOf(note)`：笔记自带优先，缺了就用补拉到的缓存，
所以从「赞过 / 收藏」点进来的文章也能正确显示作者。

### 关于「投币」

接口是 `STCoin2Note` + `nid` + `count`（通用签名），成功回 `{"res":0}`，
账号积分 `coin_count` 减少、文章的 `coins` 字段增加（**真机实测确认会落库**）。

笔记对象里只有「这篇文章总共收到多少币」，没有「我投过几枚」，
所以「每篇最多 2 枚」这个上限由客户端自己记（`Repository.noteCoins`，按 nid 存），
投满后按钮置灰。协议细节见根目录 `API_RESEARCH.md` 第 11 节。

### 关于「私信发文件」

「+」按钮现在是个菜单：发送图片 / 发送文件。
文件走 `OSSUploadFile2.php`（`dir_name=stupnotefile`，实测只能传文档类，zip 会被服务端拒），
发完之后**先按文件消息类型发，服务端不认就自动退化成「文本消息 + 附件链接」**，
所以对方一定收得到。详见 `API_RESEARCH.md` 第 12 节。

### 关于「透明卡片 + 壁纸」主题
`FkstTheme(themeStyle = ThemeStyle.GLASS)` 会把整套 `ColorScheme` 的
背景与容器角色调成半透明（`Color.copy(alpha = …)`），文字色保持不透明，
于是卡片/列表/顶栏都会透出底层的壁纸；没设壁纸时用主题色渐变兜底。

壁纸走 `ActivityResultContracts.GetContent()` 选择，选中后**复制到应用私有目录**
（`filesDir/wallpaper_*.jpg`），不依赖外部存储权限，换壁纸时清掉旧的。

### 关于图片与文件上传

私信发图会调 `OSSUploadImage4.php`（`dir_name` + `file` 的 multipart）。
平台把私信图库 `stupletter` 设成了会员功能，所以 App 做了一层**自动回落**：
被拒时改用普通图库 `stupnote` 上传，保证非会员也能把图发出去。

发文件走 `OSSUploadFile2.php`，只有 `stupnotefile` 目录收文件（`stupletter` 会回「非法路径」），
而且服务端按扩展名做了黑名单（zip 被挡，pdf / txt 可以）。
两套上传在 `FkstClient` 里共用一个 `upload()` 实现，只是端点与目录不同。
协议细节与踩坑过程见根目录 `API_RESEARCH.md` 第 10、12 节。

> **关于私信的平台限制**：服务端强制「连续签到满 3 天」才允许对外发私信，
> 这是服务端风控，官方 App 与 WebView 页面同样受限。
> 未达门槛时 App 仍可正常浏览会话与历史消息，输入框会置灰并提示还差几天。
> 详见根目录 `API_RESEARCH.md` 第 8.8 节。

### 关于「我的消息靠左」这个 bug（v1.4.1 修复）

服务端**不返回 `from_mid`**，消息方向用的是 `is_self`。早先的解析拿
`from_mid == myMid` 判断「是不是我自己发的」，字段根本取不到，于是所有气泡都被当成
「对方发的」，自己发出去的消息全挤在左边还带着对方头像。

现在方向判定集中在 `data/Models.kt` 的 `Letter.resolveDirection()`，按可靠性四级兜底：

1. `is_self`（真正的方向字段，1/0、true/false、"是/否" 都认）；
2. `my_mid` / `other_mid` 同时等于「自己 / 对方」（说明这两个字段是发送者 / 接收者）；
3. `to_mid` 这类老写法，只有它在整段会话里出现过两种取值才敢用；
4. 全认不出来时按「别人发的」渲染 —— 宁可靠左，也不要乱认。

同一版还修了两个相关的小问题：

- `GetSTLetterMessage` 的 `member` 对象只有 `id`/`nick_name`/`logo`，**没有 `home_id`**
  （这里的 `id` 就是 home_id），导致标题栏显示裸的 `mid`。现在用 `Buddy.fromMember()` 解析，
  并和好友列表里的 `Buddy` 做合并（好友列表的 `id` 反而是「关系 id」，两个入口不能混用）。
- 图片气泡原来只设了 `height(200.dp)`，宽度跟着图片固有尺寸跑，竖图会被挤成细长条。
  现在按图片宽高比 + 最大宽度 220dp 渲染。

私信页右上角菜单里加了「**查看原始报文**」，可以把 `GetSTLetterMessage` 的原文复制出来，
以后字段再变直接看原文，不用猜。

---

## 二、莫奈配色（Material You）

`ui/theme/Theme.kt` 里的 `FkstTheme`：

```kotlin
val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> DarkColors
    else -> LightColors
}
```

- **Android 12（API 31）及以上**：走 `dynamicLight/DarkColorScheme`，由系统从壁纸提取主色，
  整套 `ColorScheme`（primary / secondary / tertiary / surface 系列 / surfaceContainer* / inverse*）
  全部自动生成，主题色随壁纸联动，深色模式也由同一套算法派生。
- **Android 11 及以下**，或用户在设置里关掉「莫奈取色」：回落到 `Color.kt` 里手写的
  Material 3 蓝色方案（浅色 + 深色两套，含 surfaceContainer 全量角色）。
- 状态栏 / 导航栏图标深浅由 `WindowCompat.getInsetsController` 跟随主题自动切换。

---

## 三、技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Kotlin | 1.9.24 | |
| Android Gradle Plugin | 8.5.2 | |
| Gradle | 8.7 | |
| Compose BOM | 2024.06.00 | |
| Material 3 | 1.2.1 | `material3` + `material-icons-extended` |
| Navigation Compose | 2.7.7 | |
| Coil | 2.6.0 | 图片加载 |
| compileSdk / targetSdk | 34 | Android 14 |
| minSdk | 24 | Android 7.0 |
| JVM target | 17 | 用 JDK 17+ 构建（JDK 21 实测可用） |

网络层刻意**不引入 OkHttp**：用 `HttpURLConnection` + Kotlin 协程手写，
JSON 用系统自带的 `org.json`。好处是依赖少、体积小、构建快，坏处是多了点样板代码。

---

## 四、目录结构

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── local.properties            # sdk.dir（不入库）
└── app/
    ├── build.gradle.kts
    ├── dev.keystore            # 固定签名，便于覆盖安装（不入库）
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                # 主题、图标（自适应图标 + 低版本兜底）
        └── java/com/tb/fkst/
            ├── FkstApp.kt          # Application，持有 Repository
            ├── MainActivity.kt     # 单 Activity，edge-to-edge
            ├── core/               # 协议层（与 Python fkst_sdk 一一对应）
            │   ├── Constants.kt        # 域名 / 密钥 / 设备指纹 / 端点表
            │   ├── Signer.kt           # 三种签名变体 + 密码加盐
            │   ├── Crypto.kt           # 正文解密（base64→XOR→base64→urldecode）
            │   ├── FkstClient.kt       # 参数组装 / 签名 / 限频 / 重试 / 会话 / 图片与文件上传
            │   ├── ImageUrls.kt        # 原图换算 + 图片/文件扩展名判定
            │   ├── MediaSaver.kt       # 图片下载到系统相册
            │   ├── CacheCleaner.kt     # 缓存统计与清理（Coil + 临时目录）
            │   ├── UpdateChecker.kt   # GitHub Releases 版本检查（启动静默 + 手动）
            │   └── JsonExt.kt          # org.json 安全取值扩展
            ├── data/
            │   ├── Models.kt       # Note / Comment / Reply / UserProfile / Letter /
            │   │                   # Paper / PaperDetail / PaperQuestion / PaperVersion
            │   ├── Api.kt          # 业务封装（挂起函数，含试卷库那几个）
            │   └── Repository.kt   # 会话与偏好持久化（SharedPreferences）
            └── ui/
                ├── AppViewModel.kt # 全局状态（FeedState 泛型分页）
                ├── AppNav.kt       # NavHost + 底部导航
                ├── theme/          # Color.kt（后备配色）/ Theme.kt（莫奈 + 透明卡片）
                ├── components/     # NoteCard / NoteGridCard / NoteFeedList /
                │                   # Wallpaper / 排版按钮 / 头像 / 状态占位
                └── screens/        # Login / Discover / Search / NoteDetail /
                                    # NoteList / MyHome / Social / User / Me /
                                    # LetterList / LetterChat / ImageViewer /
                                    # Notices / Settings / PublishNote /
                                    # EditNote（改自己笔记的配图） /
                                    # PaperLibrary（试卷库）/ PaperDetail（题目+答案解析）/
                                    # Onboarding（首次引导：政策 + 刷题说明）
```

---

## 五、构建

前置：JDK 17+、Android SDK（`platforms;android-34` + `build-tools;34.0.0`）。

### Windows 一键构建

双击 `android/build-apk.cmd`（或命令行执行 `android\build-apk.cmd`）。
脚本会自动写好 `local.properties` 再调 `gradlew.bat assembleRelease`。
如果 JDK / SDK 不在默认位置，改脚本开头的两个变量即可。

### 命令行

```bash
# 1) 指向本机 SDK（build-apk.cmd 会代劳）
echo "sdk.dir=/path/to/AndroidSdk" > android/local.properties

# 2) 构建
cd android
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/AndroidSdk
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

产物：`android/app/build/outputs/apk/release/app-release.apk`

- release 用仓库内 `app/dev.keystore` 签名（口令 `android`，别名 `androiddebugkey`），
  方便后续版本**覆盖安装升级**。
- 如果换签名，请删掉手机上的旧版本再装，否则会因为签名不一致而安装失败。

---

## 六、和 Python 端的一致性

Android 端的签名与解密逻辑是 `fkst_sdk/` 的逐行移植。为了确保不出偏差，
仓库里留了一个 JVM 对照测试 `android/VerifyKotlinLogic.java`：
它复刻 `Signer.kt` / `Crypto.kt` 的算法，与 Python SDK 产出的向量逐一比对。

```bash
cd android
../tools/javac -encoding UTF-8 -d out VerifyKotlinLogic.java
java -cp out VerifyKotlinLogic
```

覆盖 4 项：通用签名、发评论签名、密码加盐 MD5（含希腊字母盐）、正文解密。
改动 `Signer.kt` / `Crypto.kt` 后建议重跑一次。

> 移植时踩到的一个坑：Java 的 `URLDecoder.decode` 会把 `+` 变成空格，
> 而 Python 的 `urllib.parse.unquote` 不会。所以 Kotlin 侧先把 `+` 转义成 `%2B` 再解码，
> 否则正文里出现 `C++`、`1+1` 这类内容会被凭空吃掉加号。

---

## 七、已知限制

1. 未做本地缓存 / 离线阅读，每次进入页面都重新请求（图片缓存由 Coil 管，可在设置页清理）。
2. **编辑笔记只能改配图**，标题和正文改不了 —— 服务端压根没开放接口。
   这条是从官方端 dex（2.3.3 完整脱壳代码）里逐一核对过的：
   笔记编辑相关的接口只有 `UpdateUploadNoteUrls` / `UpdateUploadNoteStatus` /
   `UpdateNoteTag` 三个，其中
   - `UpdateUploadNoteUrls`（id + urls）—— **能用**，对不存在的 id 会回 `res=2`，
     是真有校验的接口，已做进客户端；
   - `UpdateUploadNoteStatus`（id + score + status）—— **没做**，对不存在的 id
     也回 `res=0`（无脑成功），而且 score / status 的取值语义没有任何线索；
   - `UpdateNoteTag` —— 实测回「非本区管理员」，要管理员权限，普通用户用不了。
   另外 `UpdateUploadNoteFilter` 已经 404 下线。存草稿同样没做。
   ⚠️ 配图保存是**全量覆盖**：提交什么就是什么，界面里删掉的旧图不会保留。
3. 主动发顶层评论的入口没做，评论只支持在文章下回复。
4. 官方在 2.3.3 版本已对 api_key / 签名盐做字符串加密，并且用**支付宝 ashield 加固**
   （`classes.dex` 只剩 `com.ashield.Stub`）。当前协议依然可用，但不保证长期有效；
   想看接口清单只能扫字符串池（`tools/dex_strings.py`），参数得靠服务端缺参提示试（`tools/probe_endpoint.py`）。
   签名实现在 native（`com.yaerxing.fkst.security.NativeHelper`），dex 里**找不到**
   `F.K*$t` 模板串和 api_key —— 现在这套签名是抓包逆向出来的。

   **关于「换盐」的排查结论（2026-09-25）**：脱壳后的 dex 里有一个
   `api.yaerxing.com-f11cb6c45e3317e3d624038a657d5ad1-`，看着像新盐，
   但**实测拿它签名会被回 `url illegal!`**；现用的 `9bldwb2d5d02e81h` 一切正常。
   所以那个 32 位串**不是** `api_sig` 的盐，用途不明（大概率是 native 侧
   推送 / IM / 统计用的），不影响当前协议。
5. 私信消息类型（文本 1 / 图片 2 / 文件 3）**都还没被真机验证过**——
   账号要连续签到满 3 天才允许发私信。发文件已做降级兜底，解锁后改
   `Constants.LETTER_TYPE_*` 一处常量即可收紧。
   （消息**方向**已定位到服务端字段 `is_self`，见上面「关于「我的消息靠左」这个 bug」一节；
   这条和 type 值无关，渲染时也是按 `content` 的扩展名判图/文件的。）
6. **音频是本客户端自己的扩展**。服务端的笔记正文并没有音频字段
   （扫过 11 个分区共 363 篇社区笔记，`content` / `urls` / `title` 里零音频痕迹），
   所以音频只能以正文里一行 `[音频] <url>` 的形式存在 —— 我们这边渲染成播放器，
   **官方客户端会显示成一行普通文字**。上传接口 `OSSUploadAudio2.php` 只接受 **mp3**，
   而且是按**文件扩展名**判断的（内容不校验，wav 改名成 .mp3 也能传上去）。
7. 正文渲染做了一层兼容：`Note.readable()` 会反复解包体 JSON 再剥 HTML 标签。
   这是为了兼容 v1.5.0 及更早版本发出去的笔记 —— 那会儿把 `content` 包成了 JSON，
   被服务端当成正文存了下来，读回来是双层嵌套，详情页会直接显示一坨 JSON。
   v1.6.0 起改为提交纯文本，新发的笔记不会再出现这个现象。
8. **更新检查强依赖 GitHub 可达**。api.github.com 在部分网络下连不上，
   查不到就静默跳过（启动时）/ 提示失败（手动检查），不会阻塞任何功能。
   另外 `releases/latest` 只认**最新发布的正式版**：tag 必须是 `v?x.y.z`
   这种规范格式（之前那个叫 `qwq` 的 Release 解析不出来，会被静默跳过），
   并且**别勾 pre-release**，否则 latest 取不到。
9. **首启引导的两个页面只认 SharedPreferences 标记**。「清除应用数据」后
   政策页会再弹一次（这是预期行为）；覆盖安装（不清数据）则两个页面都不会再出现。
10. **刷题 H5 答题页仍然进不去**（2026-09-25 复测）：
   `questionExercise-v17` 恒回「请求不合法-1」、`verifyShareQuestionExercise-v2`
   恒回「非法访问」，换 `id` / `qid` / `ids` / `paper_id` / `kid` / `xd+subject`
   等各种参数组合**提示完全不变**，说明卡在登录态 / 签名这一层而不是参数名。
   所以在线答题依旧做不了，1.9.0 起改成**试卷库**：题目走 `GetZJPaperById5`
   （pid + paperid + type + aid，缺一个就拿不到），能看题 / 答案 / 解析，
   但**交卷和在线答题没有可用接口**。细节见 `API_RESEARCH.md` 第 18 节。
