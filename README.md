# FkstClient

**疯狂刷题（fkst）第三方客户端** —— Python 协议 SDK + 原生 Android 客户端。

项目包含两部分：

| 部分 | 技术栈 | 说明 |
|---|---|---|
| `fkst_sdk/` + `bot/` + 终端客户端 | Python 3 | 协议封装、接口探测工具、TUI 客户端、评论/点赞机器人 |
| [`android/`](android/) | Kotlin + Jetpack Compose + Material 3 | 原生 Android 客户端，支持莫奈（Material You）动态取色 |

> 界面截图与详细功能清单见 [android/README.md](android/README.md)。

---

## ⚠️ 免责声明

- 本项目**仅供学习与技术研究**，用于研究 Android 应用的网络协议与客户端实现。
- 项目与「疯狂刷题」官方及其开发方**无任何关联**，非官方产品。
- 请勿用于任何商业用途、批量刷量、骚扰他人或违反平台用户协议的行为。
- 使用本项目产生的一切后果由使用者自行承担，**建议使用小号测试**。
- 若权利方认为本项目侵犯其权益，请联系删除。

继续使用即表示你已阅读并同意上述内容。

---

## 📦 下载安装

已构建好的安装包：

- 最新版：`release/FkstClient-v1.5.0-release.apk`（历史版本见 GitHub Releases）

要求 Android 7.0（API 24）及以上。

> 安装包使用自签证书，安装时系统会提示「未知来源应用」，请手动允许。

---

## ✨ 功能一览

### Android 客户端

底部导航四项：**发现 / 搜索 / 私信 / 我的**。

- **登录** —— 手机号 + 密码，会话持久化
- **发现** —— 推荐（11 个分区）/ 关注 二级切换，分页 + 滚动到底自动加载
- **搜索** —— 文章 / 题目搜索，分页
- **文章详情** —— H5 密文正文解密展示、多图预览、点赞、收藏、投币、**文末作者卡片**（点击进作者主页）
- **发布笔记** —— 标题 + 正文 + 最多 9 张配图 + 分区
- **图片查看** —— 全屏双指缩放 / 拖动 / 双击放大，保存**无水印原图**到相册
- **排版** —— 单列大卡 ↔ 双列网格，偏好持久化
- **评论** —— 分页、楼中楼、发评论 / 回复 / 点赞
- **用户主页 / 我的主页** —— 资料卡**随翻页折叠**；发布 / 赞过 / 收藏 三个 tab
- **关注与粉丝** —— 分页取全，滚到底自动续拉
- **私信** —— 会话列表 + 气泡对话，新消息在最下、**每 10 秒静默刷新**，支持置顶 / 备注 / 发图片 / 发文件，**防撤回**（对方撤回的消息本地灰显保留）
- **签到** —— 每日签到，连续 3 天解锁私信，支持自动签到
- **外观** —— 莫奈取色、深色模式、透明卡片、自定义壁纸
- **存储与缓存清理** —— 图片缓存 + 临时文件，不触碰用户数据

### Python 端

- `fkst_sdk/` —— 三种签名变体、正文解密、主要接口的 Python 封装
- `main.py` —— 终端 TUI 客户端（Rich）
- `bot/` —— 评论自动回复 / 自动点赞机器人（默认 `dry_run=true`，只对白名单作者生效）
- `tools/` —— 加固 APK 字符串提取、接口参数探测脚本

---

## 🚀 快速开始

### Android 客户端

```bat
:: Windows：双击即可，脚本会自动写 local.properties
android\build-apk.cmd
```

或用命令行：

```bash
cd android
./gradlew assembleRelease
# 产物：android/app/build/outputs/apk/release/app-release.apk
```

环境要求：JDK 17+、Android SDK（compileSdk 34）。
Gradle 会读取 `JAVA_HOME` / `ANDROID_HOME`；若未设置，`build-apk.cmd` 里有两个可改的默认路径。

### Python 端

```bash
pip install -r requirements.txt

# 配置账号
cp .env.example .env      # 然后填入手机号与密码

python main.py            # 启动终端客户端
```

机器人（默认只读不写，`bot/config.json` 里 `safety.dry_run = true`）：

```bash
# 先编辑 bot/config.json，把 owner_home_ids 填成你自己的 home_id
python -m bot.main --once --live --log-dir logs
```

> **注意**：`owner_home_ids` 默认为空。为空时程序出于安全考虑**不执行任何动作**，
> 只会读取内容。请务必先填好白名单再开启写操作。

---

## 🧩 项目结构

```
.
├── android/              # 原生 Android 客户端（Kotlin + Compose）
│   ├── app/src/main/java/com/tb/fkst/
│   │   ├── core/         # 签名、加解密、HTTP、缓存清理等基础设施
│   │   ├── data/         # 数据模型、API 封装、本地仓库
│   │   └── ui/           # Compose 界面（screens / components / theme）
│   ├── build-apk.cmd     # 一键构建脚本（Windows）
│   └── README.md         # Android 端详细文档
├── fkst_sdk/             # Python 协议 SDK（签名 / 解密 / 接口封装）
├── core/                 # 请求、会话、分页、参数管理等基础设施
├── services/             # 业务服务层（登录 / 题库 / 社交 / 用户）
├── models/               # 数据模型
├── bot/                  # 评论与点赞机器人
├── tools/                # dex 字符串提取、接口探测
├── release/              # 已构建的 APK
├── API_RESEARCH.md       # ⭐ 协议逆向与接口实测报告
└── PROJECT_STRUCTURE.md  # Python 端结构说明
```

---

## 📚 协议研究文档

[`API_RESEARCH.md`](API_RESEARCH.md) 是项目的核心资料，记录了完整的探索过程：

- 固定设备参数、三种签名变体的算法与模板串
- 正文密文（base64 → XOR → base64 → urldecode）解密流程
- 各接口的必需参数与返回结构实测
- 加固 APK（支付宝 ashield）的字符串提取思路，以及「服务端缺参提示」探测法
- **发布笔记** `UploadNote2`、删除笔记 `DeleteShuatiNote`、投币 `STCoin2Note` 等较隐蔽接口的完整参数
- 若干「看起来是 UI bug、实际是接口不返回字段」的排查记录

> 文档中出现的账号 ID、昵称等均为**演示值**，非真实账号。

---

## 🔐 隐私与设备参数

`config/settings.py` 与 `android/.../core/Constants.kt` 中有一组从官方 APK 逆向得到的固定设备参数，
包括 `api_key`、`appid`、`device_imei`、`identity`、`um_token` 等。

- 这些是**官方 App 自带的公开参数**，删除后客户端无法与服务端通信，因此保留在源码里。
- 但它们原本取自某台真实设备。如果担心多人共用同一套设备指纹被风控识别，
  建议替换成你自己抓包得到的值。
- 仓库中**不包含**任何真实账号密码、登录会话（`openid` / `unionid` / `mid`）或个人凭据，
  相关文件已列入 `.gitignore`。

---

## ⚠️ 已知限制

1. 私信受平台规则限制：服务端要求账号**连续签到满 3 天**才允许对外发私信，
   官方 App 与 WebView 页面同样受限，非本项目缺陷。
2. 官方 APK 使用支付宝 ashield 加固，`classes.dex` 仅为壳，静态反编译拿不到方法体；
   接口名可从明文字符串池中扫出，参数需靠服务端「缺少参数 xxx」提示逐个试出。
3. 发布笔记目前只支持「新建」，**不支持修改已有笔记 / 存草稿**。
4. 未做离线缓存，每次进入页面都会重新请求（图片缓存由 Coil 管理，可在设置中清理）。

---

## 🙏 致谢

- 原 Python 客户端项目 `fkst-client`（本项目的协议分析基础）
- DeepSeek R1 / Qwen 3 / Qwen Coder（逆向分析与代码辅助）
- Jetpack Compose / Material 3 / Coil 等开源项目

---

## 📄 License

[MIT](LICENSE)
