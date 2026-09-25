# 疯狂刷题（fkst）API 逆向与接入研究报告

> 研究时间：2026-09-25
> 研究材料：本项目源码 + 官方 APK 2.0.8（本机 Downloads 内的 `com.yaerxing.fkst_2.0.8.zip`）+ 官方 APK 2.3.3（从官方版本接口下载）+ 线上接口实测
> 结论一句话：协议已经完全跑通，匿名即可读取社区内容；登录后可以发评论，具备做机器人的全部基础条件。
>
> **隐私说明**：为便于公开，本文档中出现的账号 `mid` / `home_id` / 昵称 / 本机绝对路径
> 均已替换为演示值（`10000001` / `10000002` / `示例用户`），非真实账号信息。

---

## 一、客户端基本信息

| 项目 | 值 |
|------|-----|
| 应用 | 疯狂刷题（`com.yaerxing.fkst`） |
| 后端主域 | `https://api.yaerxing.com/`（POST，`application/x-www-form-urlencoded`） |
| Web/H5 域 | `https://www.yaerxing.com/shuati/*`（GET，部分返回 HTML） |
| 资源域 | `imgcdn.yaerxing.com`、`audiocdn.yaerxing.com` |
| 最新版本 | 2.3.3（VersionCode 197，2026-06-03 上传） |
| 本项目基于 | 2.0.x 抓包实现的协议 |
| 请求 UA | `okhttp/4.9.0` |

版本查询接口（无需登录）：

```
GET https://api.yaerxing.com/FKSTVersion?code=197&rom=OPPO
→ {"VersionCode":197,"VersionName":"2.3.3","DownloadUrl":"...","ApkMd5":"0ebcc7fe..."}
```

---

## 二、请求协议（已实测通过）

所有 `api.yaerxing.com` 接口的 POST body 由三部分参数合并而成：

**1. 固定设备参数（`config/settings.py::DEVICE_PARAMS`）**

```
api_key=17bf6ed3b808eb7dcfa5wa0f1f0cf1de
appid=wx2bd42ba7f4c547f5
app_c=171          app_v=2.0.2        platform_id=2
channel=none       rom=OPPO           model=PJJ110      brand=OPPO
os_v=29            oam=0              url_name=（空）
device_imei=f448c5eaf564af4dc63d5c0587e68290
identity=171171dguf117cf2.a011f178egua59bd3dest.2194st1h
um_token=AjyrWarcqPA-F-J60x70BmVVl8f0BWZzsx2WtcdvgSJm
```

**2. 动态参数（登录后覆盖）**：`unionid` / `openid` / `mid` / `device_token`；未登录默认 `unionid=guest, openid=guest, mid=1`。

**3. 业务参数 + 时间戳**：`call_id` = 毫秒时间戳（字符串）。

### 签名算法（通用，`core/signer.py::generate_signature`）

```
1. 除 api_sig 外，所有参数按 key 字典序 → "key1value1key2value2..."
2. 拼接固定密钥 SECRET = 9bldwb2d5d02e81h
3. 取 call_id 后四位 cid，计算 md5("f0{cid}com.yaerxing.fkst{app_c}F.K*$t")[5:21]
4. api_sig = MD5(step2 + step3).upper()
```

### 三个不同的签名变体

| 端点 | 差异 |
|------|------|
| 通用（绝大多数接口） | 上述算法 |
| `GET_NOTE`（H5 文章详情） | 用 `timestamp` 后四位代替 `call_id`，盐串为 `f0{ts}{app_v}F.K*$t`，且不带设备参数 |
| `SET_NOTE_COMMENT1`（发评论） | **只取 3 个参数**拼接：`api_key`+`call_id`+`openid`，其余参数不参与签名 |

### 实测验证

```
POST /GetSTNotices2（guest）            → {"res":0,"notices":[]}
POST /GetDiscoverTagNotes2（guest）      → {"res":0,"flags":[...],"notes":[{id,title,content,urls,...}]}
POST /GetSTParams（guest）               → {"quick_login":true,"verify_code":"512101",...}
POST /GetShuaTiTotal6（guest）           → res=4 "unionid field missing" / 带 guest 参数则返回会员数据 + 旧版本提醒
```

结论：`res=0` 成功；缺参时返回 `{"res":N,"error":"xxx field missing"}`，字段名会直接告诉你缺什么，**非常适合调试探测**。

---

## 三、内容加密（H5 文章正文）

`GET https://www.yaerxing.com/shuati/discoverNoteDetail-v5` 返回 HTML，正文密文在
`<div class="note-content">…</div>` 中，解密流程（`services/social_service.py`）：

```
key = md5(str(secret_key) + "17bf6ed3b808eb7dcfa5wa0f1f0cf1de")[8:16].lower()
明文 = urldecode( base64decode( XOR( base64decode(密文), key ) ) )
secret_key 为 100000~2000000 的随机整数，随请求一起发出
```

注意：匿名访问该接口目前得到的是「提示/空页」，需带上有效登录态（unionid/openid/mid）才返回正文。

---

## 四、接口清单（从 APK 2.3.3 提取，共 263 个候选，去除误报后约 150 个真实业务接口）

### 账号 / 会话
`STAccountLogin3`（手机号+MD5 加盐密码，`utils/encryption.py`）、`GetSTAccounts`、`LoginDevices`、`DeleteUser2`、`GetSTMyData5`、`GetSTUserData`、`UpdateSTMember`、`GetSTParams`、`GetMemberCityByIP(1)`、`GetSTNoticeConfig` / `UpdateSTNoticeConfig`、`GetSTNotices2`、`GetGZNewVersion` / `SetGZNewVersion`、`GetPublicKey`

### 社区 / 笔记（机器人核心区）
| 功能 | 接口 |
|------|------|
| 发现流 | `GetDiscoverNotes3`、`GetDiscoverTagNotes2` |
| 关注流 | `GetFollowUserNotes2` |
| 用户文章 | `GetSTUserNotes2` |
| 文章详情 | `GET www.yaerxing.com/shuati/discoverNoteDetail-v5` |
| 搜索 | `GetSearchNotes`、`GetSearchUsers`、`GetSearchPapers7`、`GetSearchGroups` |
| 评论列表 | `GetSTCommentByNid2`（按笔记）、`GetSTCommentByFid2`（按父评论/回复）、`GetSTCommentByEid`、`GetSTCommentByWid1`、`GetSTCommentByQid2`、`GetSTComments`、`GetSTCommentDetail`、`GetSTNoteCommentDetail` |
| 发评论/回复 | `SetSTNoteComment1`（`content`+`nid`+`fid`，`fid=0` 为顶层评论） |
| 删评论 | `DelSTNoteComment`、`DelSTComment` |
| 点赞 | `SetSTNoteCommentLike`（评论点赞）、`GetLikeShuatiNote` |
| 关注 | `SetSTFollowMark` |
| 标签 | `UpdateNoteTag` |

### 私信 / 好友 / 通知
`GetSTLetterMessage`、`SendSTLetterMessage`、`DelSTMessages`、`GetSTBuddies`、`GetSTLinkmanList`、`DeleteSTLinkman`、`GetSTBlackList`、`GetSTDanMu`（弹幕）、`AddSTCoin`（签到）

> 私信实测参数见第 8.8 节。要点：读用**通用签名**，发用 **comment 签名变体**；
> 且平台限制「连续签到满 3 天」才允许对外发私信。

### 作品 / 创作
`GetSTMyWorks`、`GetSTCreatorWorks`、`GetSTCreatorWorkDetails`、`GetSTCreatorCategories`、`SetSTWorkComment1`、`GetSTWorkCommentDetail`、`DelSTWorkComment`、`SetSTWorkCommentLike`、`UpdateWorks`、`UploadNote` / `UploadPaper`（APP 内 Activity）

### 试卷 / 刷题
`GetShuaTiTotal6`、`GetShuatiPaper5`、`GetAnswerShuatiPaper5`、`GetBKShuatiPaper4`、`GetXKShuatiPaper`、`GetZGKShuatiPaper2`、`GetZJPaperById5`、`GetZJQuestionByID2`、`GetErrorQuestions3`、`GetMyShuatiQuestion`、`GetRecordShuatiQuestion1`、`GetCollectionShuatiPaper5`、`GetCollectionShuatiQuestion1`、`GetSTMyPapers`、`GetSTMyFolderPapers`、`GetSTMyOpenPapers`、`GetSTPaperPackage(Detail)`、`GetSTPaperPdfUrl`、`GetSTErrorQuestionFolder`、`GetSTEmptyExplainQuestions`、`UpdateSTPaperAnswer`、`AddSTFolderQuestion`

### 筛选 / 分类
`GetSTFilterData`、`GetDXSTFilterData` / `GetDXSTCategory`、`GetXCGSTCategory`、`GetCollegeSTFilterData`、`GetCollegeBookSTFilterData`、`GetAcademySTFilterData2`、`GetMySTFilterData`、`GetVocationSTFilterData2`、`GetSetsCategories` / `UpdateSetsCategories`

### 积分 / 订单 / 会员
`AddSTCoin`、`AddSTExperience`、`GetSTOrders`、`GetSTPayResult`、`GetSTFastCards` / `UpdateFastCards`、`GetSTVCards` / `DelSTVCards`、`GetSTGoals` / `AddSTGoals` / `DeleteSTGoals`、`GetSTSharedStatus` / `UpdateShuatiShare`

### 文件上传（OSS）
`OSSUploadFile2.php`、`OSSUploadImage4.php`、`OSSUploadAudio2.php`、`OSSUploadVideo2.php`、`OSSUploadFile2.php`、`OSSImportPaper.php`

### WebView 页面（H5，非 API）
`www.yaerxing.com/shuati/` 下：`letter-v3`（私信页）、`messageBoard`、`noteGuide`、`helpDetail`、`importModel`、`paperExercises-v18`、`questionExercise-v17`、`wrongQuestionExercise-v2` 等

---

## 五、2.0.8 vs 2.3.3 的关键差异（对接时必须注意）

1. **敏感常量已从新包中消失**：`9bldwb2d5d02e81h`（签名盐）、`17bf6ed3...`（api_key）、`F.K*$t` 模板串在 2.3.3 中已搜不到，推测做了字符串加密/动态拼装。老算法**目前仍在服务端生效**（已实测），但存在被废弃的风险。
2. **部分老接口名在新包中消失**：`GetShuaTiTotal6`、`GetSTMyData5`、`GetSTNotices2`、`GetSTCommentByQid2`、`GetZJPaperById5`、`DeleteUser2`。实测这些老接口仍可调用（`GetShuaTiTotal6` 会附带「当前版本不再维护，请更新」的提示）。
3. **`app_v` 语义混乱**：`DEVICE_PARAMS.app_v=2.0.2`，而 `GET_NOTE` 用的 `app_v=171`（实际是 `app_c`）。实测两者都能通过，说明服务端校验宽松。
4. **新版本把 `com.yaerxing.fkst`、`call_id`、`api_sig`、`secret_key` 保留在明文中**，说明签名体系结构未变。

---

## 六、做机器人的可行性评估

**可以做**（无需逆向新算法，现有协议即可）：
- 匿名/登录读取发现流、关注流、指定用户文章、文章正文解密、评论列表 → 监控类机器人
- 登录后发评论与回复（`SetSTNoteComment1`）、点赞、关注 → 互动类机器人
- 搜索笔记/用户/试卷 → 检索类机器人
- 私信读取（`GetSTLetterMessage`）→ 消息类机器人；发私信未见独立 API，疑似走 H5 `letter-v3`

**限制与风险**：
1. `res` 非 0 即失败；发评论返回含 `coin_count`（积分）与 `size_score`（内容评分），说明**服务端有内容评分机制**，机器可识别灌水/重复内容，建议限频、内容去重。
2. 未发现公开的限流文档，但接口有 `verify_code`（`GetSTParams` 返回 `512101`）等风控字段，需实测安全频率。
3. 账号安全：新设备/新 IP 登录可能触发验证；建议机器人账号使用固定 `device_imei` / `identity`，并复用 `user_session.json` 会话，避免频繁登录。
4. 合规：这是第三方非官方接入，涉及未成年人教育平台内容，自动化互动务必遵守平台规则，避免刷屏、引流、批量骚扰。

---

## 七、接入方案建议（待确认后实施）

1. 把现有 `core/signer.py` + `core/param_manager.py` + `core/api_client.py` 抽出为一个无 TUI 依赖的 SDK（`fkst_sdk/`），支持：固定设备指纹、会话持久化、自动重登、请求重试与限频。
2. 补齐 `api_endpoints.py` 中的新端点定义（尤其评论/关注/搜索/私信）。
3. 机器人层用插件式结构：`monitor`（轮询新笔记/评论）→ `filter`（关键词/正则/去重）→ `action`（评论/回复/通知你）。
4. 先用**只读**模式跑 24 小时，确认限流与稳定性，再开启写操作。

---

## 八、接入实现（已完成并实测）

### 8.1 登录的正确姿势（踩坑记录）

`STAccountLogin3` 的 `verify_type`：
- `verify_type=1` → **密码登录**，返回 `{"unionid":...,"openid":...,"mid":...,"res":0}`
- `verify_type=2` → 验证码登录，用密码去打会返回 `{"res":1,"remind_hint":"密码不正确"}`（本项目原代码默认 2，所以一直登不上）

同时确认：**api_sig 会被服务端校验**（去掉签名返回 `{"res":1,"error":"url illegal!"}`），即现有签名算法是有效的。

机器人账号实测：`mid=10000001`，昵称 `示例小号`，关注 1 人 → `home_id=10000002`（示例用户）。

### 8.2 新增代码

```
fkst_sdk/            无界面依赖的 SDK（只用标准库 urllib）
  constants.py       域名 / 密钥 / 设备指纹 / 端点定义
  signer.py          三种签名 + 密码加密
  crypto.py          正文解密 + 去标签
  client.py          参数组装、限频、重试、会话持久化、登录
  services.py        业务封装（笔记 / 评论 / 用户 / 搜索）
bot/                 评论机器人
  config.json        白名单、回复模板、频率上限、dry_run 开关
  config.py state.py engine.py main.py
explore_readonly.py  只读探查脚本
explore_follow.py    找出关注的账号及其笔记
.env                 FKST_PHONE / FKST_PASSWORD（已被 .gitignore 忽略）
```

### 8.3 实测通过的写操作（已回滚验证）

| 操作 | 接口 | 结果 |
|------|------|------|
| 发顶层评论 | `SetSTNoteComment1`（fid=0） | res=0，返回 id，`size_score=2`；读回可见 |
| 回复评论 | `SetSTNoteComment1`（fid=父评论id） | res=0；在 `GetSTCommentByFid2` 中可见 |
| 删除评论 | `DelSTNoteComment`（id=评论id） | res=0，复查已消失 |

评论对象字段：`id / nid / fid / content / created_at / home_id / nick_name / zan_ct / comment_ct / replies[] / is_self / is_top`；
回复对象额外带 `relay_content`（被回复的原内容）。

### 8.4 机器人运行方式

```bash
cd C:\path\to\fkst-client
python -m bot.main --once --dry-run   # 演练：只打印将要发送的内容
python -m bot.main --once             # 真跑一轮（受 config.json 的 dry_run 控制）
python -m bot.main                    # 常驻循环，默认 300 秒一轮
```

内置安全约束：
1. 只操作 `owner_home_ids` 白名单作者（当前 `10000002`）的笔记，其他笔记硬跳过；
2. `state.json` 记录已回复的评论 id，重启不重复回复；
3. 只回复「启用之后」的新评论（时间水位 `since`，避免翻旧账刷屏）；
4. 过滤博主自己的评论、纯 @提及、长度 < 3 的无意义短评、含「举报/投诉」等敏感词；
5. 单轮最多 3 条、每天最多 20 条、两条动作间隔 ≥ 45 秒；
6. `dry_run=true` 时绝不发送。

### 8.5 点赞能力（新增）

- 接口：`DiscoverNoteLike`，参数 `nid` + `status`（`1`=点赞，`0`=取消赞）
- 查询自己赞过的笔记：`GetLikeShuatiNote`（`page`）→ `{"notes":[...]}`
- SDK：`S.like_note(client, nid, like=True)` / `S.get_liked_notes(client)`
- 机器人：`config.json` 的 `auto_like` 段控制自动补赞
  （`enabled` / `max_per_run` / `max_per_day` / `min_seconds_between` / `skip_already_liked`），
  已赞列表在服务端和 `state.json` 双重去重
- 实测：用户主号 10000002 的 10 篇笔记全部点赞成功，`like_count` 各 +1

### 8.6 定时运行

已配置 WorkBuddy 定时任务「fkst 评论机器人（每小时一轮）」，每小时自动执行
`python -m bot.main --once --live --log-dir logs`，日志按天写入 `logs/bot_YYYYMMDD.log`。

想改成更密的频率（例如 30 分钟），在**你自己的终端**里注册一个 Windows 计划任务即可
（本机安全策略禁止我调用 schtasks）：

```bat
schtasks /create /tn "FkstBot" /tr "\"C:\path\to\fkst-client\bot\run_once.cmd\"" /sc MINUTE /mo 30 /f
schtasks /run /tn "FkstBot"      :: 立即试跑一次
schtasks /query /tn "FkstBot"    :: 查看状态
schtasks /delete /tn "FkstBot" /f :: 取消
```

`bot/run_once.cmd` 已经写好，双击也能跑一轮真实发送。

### 8.7 仍需你拍板的

1. 回复话术目前固定为 `awa`（`bot/config.json` → `reply.templates.default`），要不要按关键词分场景
2. 是否只回复特定关键词的评论（`only_if_contains`），还是所有新评论都回
3. 主动发顶层评论仍是关闭状态（`top_comment.enabled=false`），需要指定笔记 id 再开

---

## 8.8 私信（信件）—— 协议已打通，但受平台规则限制

### 结论先说

私信**技术上完全可行**，协议已全部逆向并实测：

| 能力 | 接口 | 签名 | 参数 | 状态 |
| --- | --- | --- | --- | --- |
| 会话对象（好友） | `GetSTBuddies` | 通用 | `type`(1 互关/2 关注/3 粉丝)、`page` | ✅ 实测通 |
| 读消息 | `GetSTLetterMessage` | **通用** | `my_mid`、`other_mid`、`letter_id`、`flag`、`page` | ✅ 实测通 |
| 发消息 | `SendSTLetterMessage` | **comment** | `type`、`my_mid`、`other_mid`、`content` | ⚠️ 签名通过，被业务规则拦 |
| 删消息 | `DelSTMessages` | 通用 | `id` | 未实测 |
| 每日签到 | `AddSTCoin` | 通用 | `coin`、`day` | ✅ 实测通 |

### 关键发现

**1）`flag` 的语义**（试出来的，`flag=0` 会返回「错误参数」）

- `flag=2` → 首次打开会话，额外返回 `member`（对方资料：`id` / `nick_name` / `logo`）
- `flag=1` → 翻页取更早的消息
- 响应结构：`{"member": {...}, "letters": [...], "res": 0}`

```
接口: POST https://api.yaerxing.com/GetSTLetterMessage
签名: 通用（default）
参数: my_mid=10000001, other_mid=10000002, letter_id=0, flag=2, page=0
返回: {"member":{"id":"10000002","nick_name":"示例用户","logo":"..."},"letters":[],"res":0}
```

**2）发私信用的是 comment 签名变体**（和 `GetSearchNotes` 同款）

用通用签名发会被拒：`{"res":1,"error":"url illegal！"}`；
换成 comment 签名（只签 `api_key` + `call_id` + `openid`）后服务端就接受了。

**3）真正的门槛：平台规则「连续签到满 3 天」**

签名过了之后，服务端返回的是业务错误：

```json
{"res": 2, "remind_hint": "您签到尚未满3天，暂时无法和TA私信~"}
```

这是**服务端强制的风控规则**，不是我们代码的问题 ——
用官方 WebView 页面 `www.yaerxing.com/shuati/letter-v3` 请求，返回的 HTML 是同一句
「你签到尚未满3天，暂时无法和TA私信~」（`/img/empty.png` 空状态图）。

也就是说：**任何客户端（包括官方 App）在这个账号签满 3 天前都发不出私信。**

### 签到接口（用来解锁）

```
接口: POST https://api.yaerxing.com/AddSTCoin
参数: coin=1, day=1        # day = 连续签到天数
返回: {"res": 0}           # 成功；重复签到返回 {"res": 2}
```

签到后 `GetSTMyData5` 里的字段会变：

| 字段 | 含义 | 签到前一天 | 签到后 |
| --- | --- | --- | --- |
| `coin_count` | 积分余额 | 9 | 10 |
| `get_coin_day` | 连续签到天数 | 0 | 1 |
| `get_coin_status` | 今日是否已签 | 0 | 1 |

机器人的签到记录：**2026-09-25 第 1 天**，预计 **09-27** 累计满 3 天后私信自动解锁。

### 代码位置

- Python SDK：`fkst_sdk/` 的 `get_buddies` / `get_letters` / `send_letter` /
  `delete_letters` / `can_dm` / `get_coin_state` / `check_in`
- Android：`data/Api.kt` 的 `buddies` / `letters` / `sendLetter` / `checkIn` / `coinState`，
  界面 `ui/screens/LetterListScreen.kt`（会话列表）、`LetterChatScreen.kt`（对话）
- 机器人：`bot/config.json` 的 `checkin`（每日自动签到）与 `letter`（私信自动回复）

### 单条消息的字段（2026-09-25 晚补：已定位到 `is_self`）

会话目前是空的（机器人账号还没有任何一条私信），所以**单条消息的字段名没法直接看到**。
但用户侧 App 实测暴露了两个硬伤，顺藤摸瓜把字段名挖出来了：

**现象**：自己发出去的消息全部渲染在左边（气泡带对方头像），"我的" 和 "对方的" 分不出来；
会话标题栏显示 `mid` 后面是空的。

**定位**（在加固 APK 的**明文字符串池**里找到的，代码体看不到但字符串没加密）：

| 串 | 说明 |
| --- | --- |
| `letters` | 响应里的消息数组（和实测 `{"letters":[]}` 对上） |
| `letter_id` | 会话 id，对应请求参数 `letter_id` |
| `is_self` | **消息方向的真正字段**，字符串池里紧挨着 `is_read` |
| `is_read` | 已读 |
| `letter_type_101` / `letter_type_102` | 两种气泡布局（`LetterType101Binding` / `LetterType102Binding`） |

另外 APK 里还带着建表 SQL，可以看到客户端本地缓存的表结构：

```sql
create table shuati_letter_record (
    id integer primary key autoincrement,
    lid int(10) unique,          -- 消息 id
    my_mid int(10)  not null,
    other_mid int(10)  not null,
    type int(2)  not null,
    status int(2)  not null,
    content text,
    created_at bigint(11),
    updated_at bigint(11)
)
```

`my_mid` / `other_mid` 是**会话维度**的（永远是自己 / 对方），所以它们区分不了方向 ——
真正区分方向的就是 `is_self`。（`my_mid` / `other_mid` 也一并解析出来当兜底。）

**顺带修掉的第二个 bug**：`GetSTLetterMessage` 的 `member` 对象只有
`id` / `nick_name` / `logo`，**没有 `home_id`**，而这里的 `id` 就是 home_id：

```json
{"member":{"id":"10000002","nick_name":"示例用户","logo":"..."},"letters":[],"res":0}
```

之前 `Buddy.from()` 只认 `home_id`，所以会话页拿到的 `partner.homeId` 是空串 →
标题栏显示裸的 `mid`、备注/置顶都对不上键。现在拆成两个解析器：
`Buddy.from()`（好友列表，`id` 是**关系 id**）和 `Buddy.fromMember()`（会话详情，`id` 是 **home_id**），
两者混用会把 homeId 变成关系 id，务必别搞反。

**代码位置**：`data/Models.kt` 的 `Letter.resolveDirection()`（方向判定，四级兜底）
与 `Buddy.fromMember()`；`data/Api.kt` 的 `letters()` 会把 `client.mid` / `other_mid` 传进去。

**还没在真机上复核**：等 09-27 签到解锁、能真发一条过去之后确认。
App 的私信页右上角菜单里加了「**查看原始报文**」，可以直接把 `GetSTLetterMessage`
的原文复制出来，字段对不上时一眼就能看出问题，不用再猜。

自动签到已经挂进机器人的定时任务，到点会自动签到，不用手动管。





---

## 九、关注 / 收藏接口（2026-09-25 补测）

这次把「关注按钮」修好，顺手把收藏协议也摸了一遍。

### 9.1 关注 / 取消关注：`STFollow`（不是 `SetSTFollowMark`！）

之前文档里把 `SetSTFollowMark` 当成关注接口，**是错的**。实测结论：

| 接口 | 签名 | 必需参数 | 语义 |
| --- | --- | --- | --- |
| `STFollow` | **通用** | `home_id` + `id` + `status` | 真·关注/取关 |
| `SetSTFollowMark` | comment | `home_id` + `id` + `mark` | 设置**关注备注**（`id` 是 `GetSTBuddies` 返回的关系 id） |

`STFollow` 的实测行为：

```
POST /STFollow  home_id=10000003&id=10000003&status=1
→ {"res":0}                       // is_follow 0 → 1，对方 fans_count +1
POST /STFollow  home_id=10000003&id=10000003&status=2
→ {"follow_status":0,"res":0}     // is_follow 1 → 0，fans_count 还原
```

坑点记录：

- `home_id` 和 `id` 传**同一个**目标 home_id，`status=1` 关注、`status=2` 取关（`status=0` 是无效值，返回 `res:0` 但什么都不做）。
- 缺 `status` 时报 `status field missing`；这一版用**通用签名**，用 comment 签名会回 `url illegal!`。
- `SetSTFollowMark` 用通用签名同样回 `url illegal!`，必须用 comment 签名；它对没有关系的人调用会返回 `res:0` 但什么都不改（因为没有关系记录）。
- 探测路径时 `404` 是干净的「不存在」信号，可以拿来爆破接口名（`STFollow` 就是这么找到的）。

验证方式：`GetSTUserData` 的 `is_follow` / `follow_status` 与目标 `fans_count` 都会跟着变。
测试用的两个用户已用 `status=2` 还原，没有留下多余关注。

### 9.2 收藏

| 接口 | 说明 |
| --- | --- |
| `GetCollectionShuatiNote1` | 我收藏的笔记列表，`page` 分页，返回 `notes[]`（结构与社区笔记一致）+ `over` |
| `UpdateSTCollection` | 收藏 / 取消收藏 |

`UpdateSTCollection` 的参数是靠服务端的缺参提示一个个试出来的：

```
version → scene → status → object_id → object_type
```

- `scene=1` 是唯一被接受的取值，其他值直接回 `{"res":1,"remind_hint":"非法收藏"}`
- `status=1/0`、`object_type=1..10` 都返回 `{"res":0}`

**⚠️ 未解决的问题**：接口返回 `res:0`，但 `GetCollectionShuatiNote1` 仍为空、
笔记的 `favorite_ct` 也没变，说明写入没有真正落库（可能还缺一个未暴露的上下文参数，
或者该账号的收藏功能被服务端限制）。
所以 Android 端的「收藏」按钮目前是**乐观更新**：本地状态会切换，列表接口也能正常渲染，
但**能否真的写进服务端还没验证通过**。等确认后再回来收紧这块。

### 9.3 代码位置

- Kotlin：`core/Constants.kt` 的 `FOLLOW_USER` / `GET_COLLECTION_NOTES` / `UPDATE_COLLECTION` / `SET_FOLLOW_REMARK`
- Kotlin：`data/Api.kt` 的 `followUser` / `collectionNotes` / `setCollection` / `followRemark`
- Python：`fkst_sdk/services.py` 的 `follow_user` / `set_follow_remark` / `get_collection_notes` / `update_collection`
- 界面：`ui/screens/UserScreen.kt`（关注按钮）、`ui/screens/SocialScreen.kt`（关注与粉丝）、
  `ui/screens/NoteDetailScreen.kt`（收藏按钮）、`ui/screens/DiscoverScreen.kt`（推荐/关注二级切换）


---

## 十、图片上传与「无水印原图」（2026-09-25 实测）

这一节把「私信发图片」和「保存无水印图片」这两件事的底层协议彻底跑通了。

### 10.1 上传接口：`OSSUploadImage4.php`

上传接口在 **API 域名**下（不在 CDN 上），是一个 PHP 脚本：

```
POST https://api.yaerxing.com/OSSUploadImage4.php
Content-Type: multipart/form-data

（普通字段）… 全套设备参数 + 动态参数 + call_id + dir_name + api_sig
（文件字段） file = 图片二进制
```

| 项目 | 值 |
| --- | --- |
| 签名 | **通用签名**（不是 comment 变体） |
| 路径参数 | **`dir_name`**（见下方坑点） |
| 文件字段名 | **`file`** |
| 成功返回 | `{"res":0,"illegal":false,"url":"http://imgcdn.yaerxing.com/upimage/<dir>/2026/09/25/<ts>_<mid>_<rand>.png"}` |
| 内容违规 | `{"res":0,"illegal":true,"url":…,"error":"图片涉嫌违规，请更换"}` |
| 要会员 | `{"res":2,"error":"请开通会员"}` |
| 目录不存在 | `{"res":2,"error":"未创建文件夹:xxx"}` |

**踩了一路的坑（记录一下，别再犯）**

`dir_name` 这个名字是爆破出来的，期间试过 200 多个候选名，全部返回
`{"res":2,"error":"指定路径为空"}`：`path` / `dir` / `folder` / `save_path` /
`file_path` / `filepath` / `upload_dir` / `object_key` / `url_name` / `table` …
甚至连「query string 传参」「塞 header」「filename 里带目录」「数组形式 `data[path]`」
都试过，全都不行。

最后是靠 `OSSUploadFile2.php`（同系列的文件上传）当**探针**破的局
——它即使路径为空也会把文件写进 `upfile//2026/09/25/…` 并把 url 回显出来，
于是可以拿「返回的 url 里有没有出现我传的值」当判断条件，一次性跑 78 个候选名，
命中了 `dir_name`。

其它经验：

- 路径不对时**文件名已经生成、文件也已经落 OSS**，只是目录是空的，别被 `res:2` 骗了。
- `OSSUploadFile2.php` 收 `dir_name` 但会回「不允许的文件类型!」；传图统一用 `OSSUploadImage4.php`。
- `dir_name` 是**服务端白名单**，从 APK 里挖到的目录名有：
  `stupnote` / `stupletter` / `stuplogo` / `stuppaper` / `stupquestion` / `stupcomment` /
  `stupanswer` / `stupformula` / `stworkthumb` / `stupnotefile`。
- `stupletter`（私信图）**需要会员**，非会员账号会被挡。
- 一次请求就传 `file` 字段名 + `dir_name`，服务端并不校验图片宽高比例，1x1 会判违规。

### 10.2 无水印原图规则

CDN 上的图片有两种形态：

```
http://imgcdn.yaerxing.com/resize_540x540/upimage/stupnote/2026/09/25/xxx.jpg   ← 接口返回的缩略图
http://imgcdn.yaerxing.com/upimage/stupnote/2026/09/25/xxx.jpg                  ← 原始上传文件
```

也就是说：**把路径里的 `/resize_<宽>x<高>/` 段删掉，就是原图**。
接口返回的 `urls[]` 与 `thumb` 全是带 `resize_` 前缀的版本，
而头像字段 `logo` 本身就是原图形态（`…/upimage/stuplogo/…`），可以互相印证。

Android 端把这个规则收在 `core/ImageUrls.kt` 的 `ImageUrls.origin()`，
列表/详情用缩略图，**全屏查看与保存走原图**。

### 10.3 代码位置

- Kotlin：`core/ImageUrls.kt`（原图换算）、`core/MediaSaver.kt`（存相册）、
  `core/FkstClient.kt` 的 `uploadImage()`（multipart 组装 + 发送）、
  `core/Constants.kt` 的 `UPLOAD_IMAGE` / `IMAGE_DIR_*`
- Kotlin：`data/Api.kt` 的 `uploadImage()`（含会员目录回落）/ `sendLetterImage()`
- Python：`fkst_sdk/client.py` 的 `upload_image()`、`fkst_sdk/services.py` 的 `upload_image()`、
  `fkst_sdk/constants.py` 的 `UPLOAD_IMAGE` / `IMAGE_DIR_*`

### 10.4 还没验证到的部分（如实说明）

- `SendSTLetterMessage` 的**图片消息**用的是 `type=2` + `content=图片地址`，
  这个推断来自文本消息是 `type=1`。因为机器人账号**连续签到只有 1 天**
  （平台要求满 3 天才允许发私信），暂时没法真发一条出去验证。
  预计 **09-27** 解锁后可以复核，届时如果字段不是 `type=2`，改一处常量即可。
- 私信图库（`stupletter`）要会员，所以 Android 端做了一个**自动回落**：
  会员目录被拒时改用 `stupnote` 上传，保证非会员也能发出图片
  （收到的人看到的还是图片，只是图库不同）。

---

## 十一、投币「硬币 → 文章」（2026-09-25 实测，全通）

这一节把「每篇文章投币」跑通了，顺带说清楚官方 APK 的加固情况。

### 11.1 先说 APK 加固（为什么不能直接反编译）

官方 APK（2.0.8 / 2.3.3 都一样）用**支付宝 ashield 加固**，特征是包里带：

```
lib/<abi>/libashield.so
lib/<abi>/libenvid-ashield-sdk.so
```

`classes.dex` 只是一层壳：

```
magic = "dex\n035"
string_ids_size = 121
class_defs_size = 1          ← 只有一个类
唯一类名 = Lcom/ashield/Stub;
```

jadx / apktool 反编译出来只有 `com.ashield.Stub`，看不到任何业务方法体
（试过 jadx 1.5.1，21MB 的 dex 21 秒跑完，产出 1 个 java 文件）。
壳后面跟着约 21MB 的加密载荷（分块熵 ≈ 8.0，不是明文 dex、也不是压缩流），
静态脱壳要逆 `libashield.so` 的原生虚拟机，代价很大；
真正可行的路子只有**动态脱壳**（root 机 / 模拟器 + FART、BlackDex、youpk 一类 dump 工具）。

**但是有个好消息**：dex 的**字符串池是明文的**。
接口名、资源名、中文文案全都能直接扫出来，
所以「靠 APK 挖接口名」这条路完全走得通——只是拿不到参数名。

> 挖串脚本：`tools/dex_strings.py`
> ```bash
> python tools/dex_strings.py fkst_2.3.3.apk                 # 列全部候选接口
> python tools/dex_strings.py fkst_2.3.3.apk --grep Coin     # 只看含 Coin 的串
> ```

参数名则用**服务端缺参提示**爆破，脚本 `tools/probe_endpoint.py`：

```
POST /STCoin2Note            → {"res":1,"error":"count field missing"}
POST /STCoin2Note count=1    → {"res":1,"error":"nid field missing"}
POST /STCoin2Note count=1 nid=1 → {"res":0}
```

### 11.2 投币接口：`STCoin2Note`

| 项目 | 值 |
| --- | --- |
| 路径 | `POST https://api.yaerxing.com/STCoin2Note` |
| 签名 | **通用签名** |
| 必需参数 | `nid`（文章 id）、`count`（投出的币数） |
| 成功 | `{"res":0}` |
| 缺参 | `{"res":1,"error":"count field missing"}` → 补上后 `"nid field missing"` |

**实测证据（真的扣了币、也真的加到了文章上）：**

```
投币前：账号 coin_count = 7,  文章 5145000 的 coins = 0
POST /STCoin2Note  nid=5145000&count=1  →  {"res":0}
投币后：账号 coin_count = 6,  文章 5145000 的 coins = 1   ✅
```

所以这个接口不是「返回 res=0 但没落库」那种假成功（收藏接口目前就是那个状态）。

补充要点：

- **每篇文章的上限（2 枚）是客户端自己卡的**。笔记对象里只有总数 `coins`，
  没有「我投过几枚」的字段，所以 Android / SDK 都按 `nid` 本地记一份。
- 笔记对象里 `coins` 就是硬币总数（社区笔记 / 用户笔记接口都会返回）。
- 同一个文件里还有几个相关的接口名，留着以后用：
  `ExchangeSTCoin`（硬币兑换）、`ExchangeSTMedal`（勋章兑换）、`STMedalDetails`（勋章明细）、
  `up_pic_coin` / `upPictureCoinCount`（传图消耗硬币）。
  另外字符串池里能看到完整文案：「您的硬币余量不足，您可使用勋章兑换硬币」，
  说明平台是**硬币 + 勋章**双货币体系。

### 11.3 代码位置

- Kotlin：`core/Constants.kt` 的 `COIN_TO_NOTE` / `COIN_PER_NOTE_MAX`、
  `data/Api.kt` 的 `coinToNote()`、`data/Models.kt` 的 `Note.coinCount`、
  `data/Repository.kt` 的 `noteCoins`（本地记录）、`ui/AppViewModel.kt` 的
  `tipNote()` / `coinsLeft()`、`ui/screens/NoteDetailScreen.kt` 的投币按钮
- Python：`fkst_sdk/constants.py` 的 `COIN_TO_NOTE`、`fkst_sdk/services.py` 的 `coin_to_note()`

---

## 十二、文件上传与「私信发文件」（2026-09-25 实测）

### 12.1 上传接口：`OSSUploadFile2.php`

和图片上传（第 10 节）是同一套：**API 域名 + multipart + 通用签名 + `dir_name` + 文件字段 `file`**，
只是落到 `upfile/` 而不是 `upimage/`。

```
POST https://api.yaerxing.com/OSSUploadFile2.php
Content-Type: multipart/form-data
（普通字段）… 全套设备参数 + 动态参数 + call_id + dir_name + api_sig
（文件字段） file = 文件二进制
```

| 情况 | 响应 |
| --- | --- |
| 成功（`dir_name=stupnotefile`，`.pdf`） | `{"res":0,"url":"http://imgcdn.yaerxing.com/upfile/stupnotefile/2026/09/25/1790320754_10000001_4785.pdf","md5":"f05285f2…"}` |
| 成功（`.txt`） | 同上，`res:0` |
| `.zip` | `{"res":1,"url":"…zip","error":"不允许的文件类型!"}` |
| `dir_name=stupletter` + 文件 | `{"res":1,"url":"…pdf","error":"非法路径"}` |

结论：

- **只有 `stupnotefile` 目录收文件**；`stupletter` 是纯图片目录，塞文件会回「非法路径」。
- 服务端按**扩展名黑名单**拦（zip 被挡），pdf / txt 这类文档可以。
- 注意即使 `res:1` **文件也已经传到 OSS 了**（url 照样回显），只是服务端不认这个目录/类型——
  这点和图片上传一样，别被 `res` 骗了。
- **图片**还是要走 `OSSUploadImage4.php`，`OSSUploadFile2.php` 传图会回「不允许的文件类型!」。

### 12.2 私信发文件：尽力发文件、保底发链接

私信消息类型目前在 APK 字符串池里只能挖到两条气泡布局：
`letter_type_101` / `letter_type_102`（外加 `ext_msg_type` / `msg_type` / `undo_msg_type_v1`）。
**没能确认文本/图片/文件各自的 type 到底取什么值**——
因为平台限制「连续签到满 3 天」才能发私信，机器人账号当时只签了 1 天。

所以客户端按这个策略实现（`data/Api.kt::sendLetterFile`）：

1. 先按 `type=3` + `content=文件地址`（外加 `file_name`）发；
2. 服务端不认就把内容改成 **文本消息 + 附件链接**（`[文件] 名字\n<url>`）再发一次。

这样最坏情况也只是对方收到一条带链接的消息，功能不会哑掉。
09-27 签到解锁后跑一次实测，把 `Constants.LETTER_TYPE_*` 三个常量一改就行
（代码里已经集中在一个地方）。

### 12.3 代码位置

- Kotlin：`core/Constants.kt` 的 `UPLOAD_FILE` / `FILE_DIR_*` / `LETTER_TYPE_*`、
  `core/FkstClient.kt` 的 `uploadFile()`（与 `uploadImage()` 共用一个 `upload()` 实现）、
  `core/ImageUrls.kt` 的 `isImageUrl()` / `isFileUrl()` / `baseNameOf()`、
  `data/Api.kt` 的 `uploadFile()` / `sendLetterFile()`、
  `data/Models.kt` 的 `UploadedFile` 与扩展后的 `Letter`（`file` / `fileName` / `isFile`）、
  `ui/screens/LetterChatScreen.kt` 的「+」菜单（图片 / 文件）与 `FileBubble`
- Python：`fkst_sdk/client.py` 的 `upload_file()`、`fkst_sdk/services.py` 的 `send_letter_file()`

---

## 十三、原版 APK 里还躺着哪些接口（本次新挖到的）

用 `tools/dex_strings.py` 扫 2.3.3 的字符串池，除了前面几节已经用上的，
下面这些是这次新浮出来、看着有用的（**都还没实测，仅登记**）：

| 分类 | 接口名 | 猜测用途 |
| --- | --- | --- |
| 投币 / 货币 | `STCoin2Note`（已实测）、`ExchangeSTCoin`、`ExchangeSTMedal`、`STMedalDetails` | 投币、硬币/勋章兑换、勋章明细 |
| 置顶 | `STNoteTop`、`STNoteCommentTop`、`STWorkCommentTop`、`STSetTop` | 笔记 / 评论 / 作品置顶 |
| 举报 | `STTipOff`、`PushSTBlackList` | 举报、拉黑 |
| 翻译 / 词典 | `STTranslateWord`、`STQueryData` | 划词翻译 |
| 抽奖 | `UseSTDraw` | 抽奖（`LetterDrawPopup` 私信里那个） |
| 作品 | `SetSTWorkComment1`、`SetSTWorkCommentLike`、`SeeSTVCard`、`STVCardSetting`、`RealizeSTGoals` | 作品区互动 |
| 纠错 / 反馈 | `SetSTExplain`、`SetSTExplainComment`、`SetSTExplainCommentLike`、`STQuestionFeedBack2` | 题目讲解区 |

> 想继续扩，直接 `python tools/dex_strings.py <apk> --grep <关键词>` 就行。
> 拿到名字后用 `python tools/probe_endpoint.py <接口名>` 靠缺参提示补参数。

顺带用探测脚本验了一个（工具本身的自测）：

```
$ python tools/probe_endpoint.py STMedalDetails
第 1 轮  {}                 → {"res":1,"error":"page field missing"}
第 2 轮  {page:1}           → {"res":1,"error":"tab field missing"}
第 3 轮  {page:1, tab:1}    → {"over":true,"details":[],"res":0}   ✅
```

即**勋章明细 = `STMedalDetails` + `page` + `tab`**（tab 大概对应
`medal_detail_const` / `medal_detail_get` / `medal_detail_paper` 三类勋章）。

---

## 十四、发布笔记：`UploadNote2`（2026-09-25 实测，全通）

### 14.1 怎么把它挖出来的

先在 2.3.3 的字符串池里搜 `Upload`：

```
$ python tools/dex_strings.py apk233/classes.dex --grep Upload
  ...
  GetCheckUploadNotes
  UpdateUploadNoteFilter / UpdateUploadNoteStatus / UpdateUploadNoteUrls
  UploadNote2          ← 真正的发布接口
```

`UpdateUploadNote*` 那一组是官方客户端「先本地存草稿、再补图/改状态」用的，
单次直接发布只需要 `UploadNote2` 一个。

### 14.2 参数是问出来的

`UploadNote2` 的报错文案和别的接口**不一样**，缺参提示是
「缺少参数 xxx」而不是「xxx field missing」：

```
$ python tools/probe_endpoint.py UploadNote2
第 1 轮  {}                    → {"res":1,"remind_hint":"非法请求2"}      ← 签名不对
（换 comment 签名变体）
第 1 轮  {}                    → {"res":1,"remind_hint":"缺少参数 urls"}
第 2 轮  {urls:"x"}            → {"res":1,"remind_hint":"缺少参数 title"}
第 3 轮  {urls:"x", title:"a"} → {"res":0,"id":"5145261"}                ✅ 直接发出去了
```

要点：
- **签名要用 `comment` 变体**（和 `GetSearchNotes` / `SendSTLetterMessage` 同款）。
  用通用签名只会得到 `{"res":1,"remind_hint":"非法请求2"}`，看不出缺什么。
- 服务端只强制 `urls` + `title`，`content` / `type` / `tagids` 全都有默认值。

### 14.3 各参数怎么填

| 参数 | 说明 |
| --- | --- |
| `urls` | 已上传图片地址的 **JSON 数组字符串**，如 `["http://imgcdn.yaerxing.com/upimage/stupnote/…jpg"]`；第一张作为封面 |
| `title` | 标题，必填，空则回「缺少参数 title」 |
| `content` | 官方那套包体 JSON：`{"version":1,"text":"正文","update_count":0,"up_count":0,"sw_title":[],"sw_content":[],"sw":[[],[]],"si_urls":[],"si_label":[]}` |
| `type` | 分区 type，取值同 `Constants.CATEGORIES`（日常 10 / 好物 7 / 试卷 12 …） |

`content` 之所以是这个形状，是因为**列表接口把正文塞在这个字段里，我们的
`Note.decodeContent()` 也是从 `text` / `sw_content` 里抽纯文本的** —— 发布时按同构拼回去就行。

### 14.4 频率限制

```
{"res":2,"remind_hint":"发布频繁，两贴发布间隔至少5分钟"}
```

同一账号 **5 分钟内只能发一篇**。客户端里做不了什么，只能把提示文案说清楚。

### 14.5 端到端验证（真发 + 真删）

```
upload → {"res":0,"url":"http://imgcdn.yaerxing.com/upimage/stupnote/2026/09/25/…jpg"}
publish → {"res":0,"id":"5145308"}
readback (GetSTUserNotes2) → title=【联调】客户端发布链路校验
                             urls=["http://imgcdn.yaerxing.com/upimage/stupnote/…jpg"]
                             type=10
delete (DeleteShuatiNote + nid) → {"res":0}
```

顺带解掉一个误解：笔记对象里的 `logo` 字段是**作者的头像**，
不是封面 —— 本次联调账号自己的头像就是字符串 `"x"`，所以每篇笔记的
`logo` 都显示 `x`，跟 `urls` 无关。

### 14.6 删除自己的笔记

```
POST DeleteShuatiNote  nid=<笔记 id>   （通用签名）
→ {"res":0}
```

缺参会回 `{"res":1,"error":"nid field missing"}`。

### 14.7 代码位置

- Android：`data/Api.kt` 的 `publishNote()` / `deleteNote()` / `buildNoteContent()`，
  `ui/AppViewModel.kt` 的 `publishNote()`（负责逐张传图再发），
  `ui/screens/PublishNoteScreen.kt`（标题 / 正文 / 最多 9 图 / 分区）
- Python：`fkst_sdk/services.py` 的 `publish_note()` / `delete_note()` / `build_note_content()`

---

## 十五、两个列表类 Bug 的真实原因（2026-09-25 复测）

### 15.1 「赞过 / 收藏」里作者全是「匿名」

不是 UI 的问题，是**接口真的不返回作者**：

```
GET GetLikeShuatiNote  page=0
→ 顶层字段：notes / ids / over / res
→ notes[0] 的键：["id","title","type","thumb","tagids","home_id","link_id"]
```

有 `home_id`，但没有 `nick_name` / `logo`，所以客户端只能显示「匿名」。
`GetCollectionShuatiNote1` 同款结构。

解决办法：拿 `home_id` 去 `GetSTUserData` 补一份资料，并在本地缓存。

```
GET GetSTUserData  home_id=10000002
→ {"info":{"nick_name":"示例用户","logo":"http://imgcdn.yaerxing.com/upimage/stuplogo/…jpg",…}}
```

注意 `GetSTUserData` **不支持批量**：`home_id` 传逗号拼接会回 `{"res":0,"open_status":3}`
（没有 `info`），所以只能一个一个来 —— 所以客户端做了两层优化：
去重（同一作者只查一次）+ 持久化缓存（`Repository.authorCache`）+ 后台串行补拉
（列表先用缓存/占位渲染，补到就刷）。

### 15.2 「关注 / 粉丝」显示不全

`GetSTBuddies` 是分页接口，一页 10 条，`over=true` 才表示取完：

```
GET GetSTBuddies type=2 page=0 → {"over":true,"buddies":[ …1 条… ],"res":0}
GET GetSTBuddies type=2 page=1 → {"over":true,"buddies":[],"res":0}
```

早先客户端只拉了 `page=0`，所以关注了 40 个人也只看得到 10 个。
修法：`Api.buddies()` 返回 `Paged<Buddy>`（`hasMore = !over`），
列表滚到底自动续拉；私信会话列表要全量，用 `Api.allBuddies()` 循环取完。

### 15.3 代码位置

- Android：`data/Api.kt` 的 `likedNotes()`（改用 `over` 判断还有没有下一页）、
  `buddies()` / `allBuddies()`；`ui/AppViewModel.kt` 的 `resolveAuthors()` /
  `withAuthor()` / `loadSocial()` / `loadMoreSocial()`
- Python：`fkst_sdk/services.py` 的 `get_all_buddies()` / `get_user_brief()`

---

## 十六、v1.5.0 新增的本地能力（不走服务端）

这几条纯客户端实现，记一下设计取舍：

| 功能 | 做法 |
| --- | --- |
| 私信新消息在最下 + 10 秒静默刷新 | 消息按 `createdAt` 升序稳定排序；轮询只更新变化了的数据，不亮 loading、失败不提示 |
| 私信防撤回 | 平台没有撤回接口，能观察到的「撤回」= 消息从 `GetSTLetterMessage` 里消失。每段会话本地归档（`Repository.letterArchive`），消失的消息标 `recalled=true` 灰显保留 |
| 缓存清理 | 清 Coil 内存/磁盘缓存 + cacheDir/externalCacheDir 临时文件；不动壁纸、备注、置顶、投币记录、登录态（`core/CacheCleaner.kt`） |

防撤回有个必须注意的坑：`GetSTLetterMessage` 是**按页返回最新 N 条**的，
所以归档里比「本页最老那条」更早的消息只是没被这一页覆盖，**不是被撤回**。
判定时用本页最老的 `created_at` 当水位线，比它更早的一律不算撤回；
整页都没有时间戳时干脆不判 —— 宁可漏判也不能冤枉。
