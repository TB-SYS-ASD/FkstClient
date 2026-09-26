# FkstClient

「疯狂刷题」（`com.yaerxing.fkst`）的第三方 **Android 客户端**。

Kotlin + Jetpack Compose + Material 3，支持莫奈动态取色。

> ⚠️ 非官方客户端，与官方无关联。所有内容与服务均由官方服务端提供。

## 下载安装

已构建好的安装包：

| 版本 | 路径 |
|---|---|
| v1.12.0（当前正式版） | `FkstClient-v1.12.0-release.apk` |
| v1.11.5 | `FkstClient-v1.11.5-release.apk` |
| v1.11.4 | `FkstClient-v1.11.4-release.apk` |
| v1.8.0 ~ v1.11.3（历史归档） | `release/` |

## 构建

Windows 双击即可：

```bash
android\build-apk.cmd
```

或手动：

```bash
cd android
gradlew assembleRelease
```

产物在 `android/app/build/outputs/apk/release/`。

**要点**：
- 需要 JDK 17+ 与 Android SDK
- `local.properties` 由 `build-apk.cmd` 自动生成（不入库）
- 签名需自备 `android/app/dev.keystore`（不入库，**未提供则构建出的包无法覆盖安装**）

## 功能

底部导航五个 tab：**试卷 / 发现 / 搜索 / 私信 / 我的**。

### 试卷库
- 按年级 / 教材版本浏览真实试卷（年级映射是实测校准的，见 `core/Constants.kt` 注释）
- 搜试卷、收藏试卷
- 试卷详情：题目 + 答案 + 解析
- **H5 实时练习**：用本账号合法会话拼官方 `paperExercises-v18` 页面，在 WebView 里真能答题 / 交卷
- 我的答题记录（回看做过的题）

### 发现 / 搜索
- 11 个分区筛选（日常 / 好物 / 试卷 / 难题趣题 / 学习经验 / 绘画 / 学习Plog / 手工种植 / 飞花令 / 作文随笔 / 书法）
- 关注流（二级切换）
- 文章搜索
- 列表排版切换（单列 / 一排两个）

### 笔记详情
- 正文解密（三层包体剥离）
- 收藏（真接口 `CollectShuatiNote`——旧接口返回成功但不落库）
- 点赞、**投币**（每篇上限 2 枚）
- **正文音频播放器**（`[音频] <url>` 约定，本客户端扩展）
- 文末作者卡片
- 全屏看图（缩放 / 拖动 / 保存**无水印原图**）
- 评论与楼中楼、发评论 / 回复 / 点赞

### 发布与编辑
- 发布笔记：标题 + 正文 + 最多 9 张配图 + 最多 3 段音频（仅 mp3）+ 分区
- 编辑已发笔记的**配图**（标题 / 正文服务端未开放）
- 删除自己的笔记

### 用户与社交
- 用户主页（关注 / 取关 + 私信 + 资料卡随翻页折叠）
- 我的主页（发布 / 赞过 / 收藏，头部随翻页折叠）
- 关注与粉丝列表（分页取全）
- 我的收藏、我赞过的

### 私信
- 会话列表 + 气泡对话，新消息在最下 + **10 秒静默刷新**
- 置顶 / 备注（备注优先写服务端 `SetSTFollowMark`）
- 发图片 / 发文件
- **防撤回**：对方撤回的消息在本地灰显保留，可在设置里关

### 其他
- 每日签到（连续 3 天解锁私信，支持自动签到）
- 消息通知（按动作类型分类）
- 首次启动引导（用户政策，只出现一次）
- 更新检查（GitHub Releases，启动静默 + 设置页手动）
- 外观设置（莫奈取色 / 深色模式 / 透明卡片 + 自定义壁纸）
- 存储与缓存清理

## 目录结构

```
android/                 Android 客户端（主线）
├── app/src/main/java/com/tb/fkst/
│   ├── core/            协议层：签名、加解密、登录、H5 签名、常量表
│   ├── data/            Api / Repository / Models
│   └── ui/              AppNav / AppViewModel / screens / components
├── build-apk.cmd        一键构建
└── README.md            构建与实现细节

fkst_sdk/                Python SDK（v1 协议：MD5 签名，42 端点）
tools/                   逆向工具
├── dex_strings.py       从加固 APK 的 dex 里扫字符串
├── probe_endpoint.py    端点探测（靠服务端「缺参提示」试参数）
└── probe_paper_h5.py    试卷 H5 接口探测

release/                 历史 APK 归档（v1.8.0 ~ v1.11.3）
release-notes/           各版本发布说明（v1.5.0 ~ v1.12.0）
API_RESEARCH.md          协议逆向完整记录（18 节）
```

## 协议参考

`API_RESEARCH.md`（56KB，18 节）是协议层的完整成果。重点章节：

| 节 | 内容 |
|---|---|
| 8.8 | 私信（连续签到 3 天的平台限制） |
| 9 | 关注接口（`STFollow`；`SetSTFollowMark` 其实是设备注） |
| 10 | 图片上传（`OSSUploadImage4.php`）与无水印原图规则 |
| 11 / 11.1 | 投币 + 逆向工具说明 |
| 12 | 文件上传与私信发文件 |
| 14 | 发布笔记（`UploadNote2` + `DeleteShuatiNote`） |
| 15 | 「赞过 / 收藏显示匿名」「关注粉丝显示不全」的原因 |
| 17 | 拿到脱壳 dex 后的修正 |
| 18 | 试卷库（`GetShuatiPaper5` / `GetZJPaperById5` / `CollectShuatiPaper` / `GetSearchPapers7`） |

**签名三变体**（`core/Signer.kt`）：
- `default` —— 字典序 KV 拼接 + `SIGN_SECRET` + `MD5(tpl)[5:21]`，再 MD5 大写
- `note` —— H5 详情用，`call_id` 换 `timestamp`、`app_c` 换 `app_v`
- `comment` —— 只签 `api_key + call_id + openid`

**官方 APK 是支付宝 ashield 加固**：`classes.dex` 只是壳，静态反编译拿不到方法体；
但字符串池明文，接口名能扫出来、参数能靠服务端「缺参提示」试出来。

## 已知限制

1. **刷题模块做不到**：题库接口不接受任何筛选参数（选什么知识点都是同一批题），
   在线答题页是官方 H5、对第三方恒回「非法访问-1」，也没有提交答题记录的接口。
   1.9.0 起改为**试卷库**方案（只读题目 + 答案解析），交卷 / 在线答题依旧做不到。
2. **私信需连续签到满 3 天**（平台规则，官方 App 与 WebView 同样受限）。
3. **每篇笔记最多投 2 枚币**（服务端限制）。
4. **发布间隔至少 5 分钟**（服务端限制，否则 `res=2`）。
5. **笔记标题 / 正文不可编辑**：翻遍 dex 只有 `UpdateUploadNoteUrls` /
   `UpdateUploadNoteStatus` / `UpdateNoteTag`（需管理员），没有改正文的接口。
6. **音频上传只收 mp3**（wav / m4a 一律 `upload audio failed`）。
7. **私信图片目录 `stupletter` 需会员**，非会员自动回落到 `stupnote`。

## 致谢

- DeepSeek R1
- Qwen 3
- Qwen Coder
