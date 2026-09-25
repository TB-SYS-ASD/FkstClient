# fkst-client

## 简介

fkst-client 是一个教育类产品后端API的Python客户端，用于简化与教育类后端服务的交互。该项目提供了统一的API调用方式，处理参数管理、签名生成和加密流程，简化业务服务集成。

## 目录结构

请参考 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) 文件

## 环境变量(.env)

``` text
DEBUG_MODE=1
LOGIN_PHONE=your_phone_number
LOGIN_PASSWORD=your_password
```

## 安装依赖

在使用该项目之前，需要安装所需的依赖包：

```bash
pip install -r requirements.txt
```

## 使用方法

### 1. 配置环境变量

在项目根目录创建 `.env` 文件，并配置以下环境变量：

```env
LOGIN_PHONE=your_phone_number
LOGIN_PASSWORD=your_password
DEBUG_MODE=0  # (可选) 设置为1启用调试模式
```

### 2. 运行程序

使用以下命令启动程序：

```bash
python main.py
```

程序将自动执行以下流程：
1. 尝试加载保存的会话信息（如果存在）
2. 如果没有有效的会话信息，则使用 `.env` 文件中的账号密码进行登录
3. 登录成功后保存会话信息以便下次使用
4. 启动终端用户界面(TUI)进行交互操作

### 3. 调试模式

设置 `DEBUG_MODE=1` 可以启用调试模式，将输出更详细的日志信息。


## Android 客户端

除终端客户端外，项目还包含一个原生 Android 客户端（Kotlin + Jetpack Compose + Material 3，
支持莫奈动态取色），位于 [`android/`](android/) 目录，详见
[android/README.md](android/README.md)。

已构建好的安装包：
- `release/FkstClient-v1.6.0-release.apk`

构建方式（Windows 双击即可）：
```bash
android\build-apk.cmd
```

主要能力：登录、发现流（11 个分区 + 关注流二级切换）、搜索、
**发布笔记（标题 + 正文 + 最多 9 张配图 + 最多 3 段音频 + 分区）**、
文章详情（正文解密 + 收藏 + **投币** + **正文音频播放器** + **文末作者卡片**）、
全屏看图（缩放/拖动、保存**无水印原图**）、**列表排版切换（单列 / 一排两个）**、
评论与楼中楼、发评论/回复/点赞、
用户主页（关注/取关 + 私信 + **资料卡随翻页折叠**）、
**我的主页（发布 / 赞过 / 收藏，头部随翻页折叠）**、
**关注与粉丝列表（分页取全）**、我的收藏、
私信（会话列表 + 气泡对话，**新消息在最下 + 10 秒静默刷新**，支持**置顶 / 备注 / 发图片 / 发文件**，
**防撤回：对方撤回的消息在本地灰显保留**，可在设置里关）、
每日签到（连续 3 天解锁私信，支持自动签到）、我的（主页/笔记/收藏/我赞过的/消息通知）、
外观设置（莫奈取色 / 深色模式 / **透明卡片 + 自定义壁纸**）、
**存储与缓存清理**（图片缓存 + 临时文件，不动用户数据）。

> 私信受平台规则限制：服务端要求连续签到满 3 天才允许对外发私信，
> 官方 App 与 WebView 页面同样受限。详见 [API_RESEARCH.md](API_RESEARCH.md) 第 8.8 节。
>
> 图片上传协议（`OSSUploadImage4.php`）与无水印原图规则见
> [API_RESEARCH.md](API_RESEARCH.md) 第 10 节。
> `API_RESEARCH.md` 第 9 节记录了关注接口（`STFollow`，之前文档里的 `SetSTFollowMark` 其实是设置备注）
> 与收藏接口的实测结论；第 11 节是投币（`STCoin2Note`），第 12 节是文件上传与私信发文件，
> **第 14 节是发布笔记（`UploadNote2` + `DeleteShuatiNote`，已真机实测通）**，
> 第 15 节解释了「赞过/收藏显示匿名」与「关注粉丝显示不全」这两个 bug 的真实原因。
>
> 官方 APK 用**支付宝 ashield 加固**，`classes.dex` 只是壳、静态反编译拿不到方法体；
> 但字符串池是明文的，接口名可以扫出来、参数可以靠服务端「缺参提示」试出来。
> 工具在 `tools/dex_strings.py` 与 `tools/probe_endpoint.py`，说明见
> [API_RESEARCH.md](API_RESEARCH.md) 第 11.1 节。


## 感谢

- DeepSeek R1
- Qwen 3
- Qwen Coder

