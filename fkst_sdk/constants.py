"""常量：域名、密钥、设备指纹、端点定义。"""

API_BASE = "https://api.yaerxing.com/"
WEB_BASE = "https://www.yaerxing.com"

# 官方 APP 内置常量（客户端硬编码，2026-09 实测仍有效）
API_KEY = "17bf6ed3b808eb7dcfa5wa0f1f0cf1de"
SIGN_SECRET = "9bldwb2d5d02e81h"
PWD_SALT = "Γ_Κ-ζ.Τfkst"

# 登录密码 = md5(明文密码 + PWD_SALT)

DEVICE_PARAMS = {
    "api_key": API_KEY,
    "appid": "wx2bd42ba7f4c547f5",
    "app_c": "171",
    "app_v": "2.0.2",
    "channel": "none",
    "platform_id": "2",
    "device_imei": "f448c5eaf564af4dc63d5c0587e68290",
    "rom": "OPPO",
    "model": "PJJ110",
    "brand": "OPPO",
    "os_v": "29",
    "oam": "0",
    "url_name": "",
    "device_token": "",
    "identity": "171171dguf117cf2.a011f178egua59bd3dest.2194st1h",
    "um_token": "AjyrWarcqPA-F-J60x70BmVVl8f0BWZzsx2WtcdvgSJm",
}

DEFAULT_DYNAMIC_PARAMS = {
    "unionid": "guest",
    "openid": "guest",
    "mid": "1",
}

# GET_NOTE（H5 文章详情）专用参数
GET_NOTE_PARAMS = {
    "adolescent_model": "0",
    "api_key": API_KEY,
    "app_v": "171",          # 官方此处填的其实是 app_c，实测有效
    "appid": "wx2bd42ba7f4c547f5",
    "channel": "none",
    "font_size": "2",
    "os_v": "29",
    "platform_id": "2",
    "rom": "OPPO",
    "version": "2",
}

# 笔记分区（services/social_service.py::get_social_categories 同源）
CATEGORIES = {
    "daily": ("日常", "10", {}),
    "goods": ("好物", "7", {}),
    "paper": ("试卷", "12", {"subject_tag": "0", "grade_tag": "0"}),
    "hard": ("难题趣题", "6", {"grade_tag": "0"}),
    "experience": ("学习经验", "2", {"xd_tag": "0"}),
    "drawing": ("绘画", "9", {}),
    "plog": ("学习Plog", "13", {}),
    "handmade": ("手工种植", "8", {}),
    "feihualing": ("飞花令", "11", {}),
    "essay": ("作文随笔", "16", {}),
    "calligraphy": ("书法", "14", {}),
}


class ErrorCode:
    """服务端 res 码（实测归纳）"""
    OK = 0
    BAD_PASSWORD = 1          # 密码不正确
    FIELD_MISSING = 1         # "openid field missing" 等
    GUEST_ONLY = 4            # 需要登录
    URL_ILLEGAL = 1           # 签名缺失/错误：{"error":"url illegal!"}


# ---------------------------------------------------------------------------
# 端点定义
#   path            : 相对 API_BASE 的方法名
#   sign            : default | note | comment
#   required_params : 业务必需参数
#   default_params  : 默认值
#   skip_device     : 不携带设备参数
#   exclude_dynamic : 需要剔除的动态参数
# ---------------------------------------------------------------------------
ENDPOINTS = {
    # --- 账号 ---
    "LOGIN": {
        "path": "STAccountLogin3",
        "required_params": ["phone_number", "password", "verify_type"],
        "default_params": {"verify_type": "1"},   # 1=密码登录（2 会走验证码）
    },
    "GET_ST_PARAMS": {
        "path": "GetSTParams",
        "required_params": [],
    },
    "GET_MY_DATA": {
        "path": "GetSTMyData5",
        "required_params": ["all_black_member"],
        "default_params": {"all_black_member": "1"},
    },
    "GET_USER_DATA": {
        "path": "GetSTUserData",
        "required_params": ["home_id"],
    },
    "GET_LINKMAN_LIST": {
        "path": "GetSTLinkmanList",
        "required_params": ["updated_at", "page"],
        "default_params": {"updated_at": "0", "page": "0"},
    },
    "GET_BUDDIES": {                 # 好友/关注/粉丝：type=1 互关, 2 关注, 3 粉丝
        "path": "GetSTBuddies",
        "required_params": ["type", "page"],
        "default_params": {"type": "1", "page": "0"},
    },
    "GET_BLACK_LIST": {"path": "GetSTBlackList", "required_params": []},
    "GET_NOTICES2": {
        "path": "GetSTNotices2",
        "required_params": ["type", "page"],
        "default_params": {"type": "2", "page": "0"},
    },

    # --- 私信（"信件"）---
    # 读：默认签名。flag=2 首次打开会话（额外返回对方 member 信息），flag=1 翻页
    "GET_LETTER_MESSAGE": {
        "path": "GetSTLetterMessage",
        "required_params": ["my_mid", "other_mid", "letter_id", "flag", "page"],
        "default_params": {"letter_id": "0", "flag": "2", "page": "0"},
    },
    # 发：comment 签名变体（与 GetSearchNotes 同款）
    "SEND_LETTER_MESSAGE": {
        "path": "SendSTLetterMessage",
        "sign": "comment",
        "required_params": ["type", "my_mid", "other_mid", "content"],
        "default_params": {"type": "1"},
    },
    "DEL_LETTER_MESSAGES": {
        "path": "DelSTMessages",
        "required_params": ["id"],
    },

    # --- 签到（每日领金币；连续签到 3 天解锁私信）---
    "ADD_COIN": {
        "path": "AddSTCoin",
        "required_params": ["coin", "day"],
        "default_params": {"coin": "1", "day": "1"},
    },

    # --- 投币（硬币 → 文章）---
    # 2026-09-25 实测：nid（文章 id）+ count（投出的币数），通用签名。
    # 成功 {"res":0}；账号 coin_count 减 count，文章 coins 字段 +count。
    # 缺参时服务端依次回 "count field missing" / "nid field missing"。
    "COIN_TO_NOTE": {
        "path": "STCoin2Note",
        "required_params": ["nid", "count"],
        "default_params": {"count": "1"},
    },

    # --- 社区 ---
    "GET_DISCOVER_TAG_NOTES2": {
        "path": "GetDiscoverTagNotes2",
        "required_params": ["type", "start_time", "page"],
        "default_params": {"start_time": "0", "page": "0"},
    },
    "GET_DISCOVER_NOTES3": {
        "path": "GetDiscoverNotes3",
        "required_params": ["type", "page"],
        "default_params": {"page": "0"},
    },
    "GET_FOLLOW_USER_NOTES2": {
        "path": "GetFollowUserNotes2",
        "required_params": ["page"],
        "default_params": {"page": "0"},
    },
    "GET_USER_NOTES2": {
        "path": "GetSTUserNotes2",
        "required_params": ["type", "home_id", "page"],
        "default_params": {"type": "0", "page": "0"},
    },
    "SEARCH_NOTES": {
        # 注意：搜索用的是 comment 签名变体，返回字段是 matches（不是 notes）
        "path": "GetSearchNotes",
        "sign": "comment",
        "required_params": ["keyword", "page", "ct"],
        "default_params": {"page": "0", "ct": "20"},
    },

    # --- 文章详情（H5，密文正文）---
    "GET_NOTE": {
        "method": "GET",
        "base_url": WEB_BASE,
        "path": "/shuati/discoverNoteDetail-v5",
        "sign": "note",
        "required_params": ["id"],
        "skip_device": True,
        "exclude_dynamic": ["openid"],
        "default_params": {"adolescent_model": "0", "font_size": "2", "version": "2"},
    },

    # --- 评论 ---
    "GET_COMMENT_BY_NID2": {
        "path": "GetSTCommentByNid2",
        "required_params": ["nid", "order_type", "start_time", "ct", "page"],
        "default_params": {"order_type": "1", "ct": "10", "page": "0"},
    },
    "GET_COMMENT_BY_FID2": {
        "path": "GetSTCommentByFid2",
        "required_params": ["fid", "object_type", "start_time", "ct", "page"],
        "default_params": {"object_type": "2", "ct": "20", "page": "0"},
    },
    "SET_NOTE_COMMENT1": {
        "path": "SetSTNoteComment1",
        "sign": "comment",
        "required_params": ["content", "fid", "nid"],
        "default_params": {"fid": "0"},
    },
    "DEL_NOTE_COMMENT": {
        "path": "DelSTNoteComment",
        "required_params": ["id"],
    },
    "SET_NOTE_COMMENT_LIKE": {
        "path": "SetSTNoteCommentLike",
        "required_params": ["id"],
    },
    "SET_FOLLOW_MARK": {             # 设置关注备注（不是关注/取关！）
        "path": "SetSTFollowMark",
        "sign": "comment",
        "required_params": ["home_id", "id", "mark"],
    },
    "FOLLOW_USER": {                 # 关注/取关：status=1 关注、2 取关（2026-09-25 实测）
        "path": "STFollow",
        "required_params": ["home_id", "id", "status"],
        "default_params": {"status": "1"},
    },
    "GET_COLLECTION_NOTES": {        # 我收藏的笔记
        "path": "GetCollectionShuatiNote1",
        "required_params": ["page"],
        "default_params": {"page": "0"},
    },
    "UPDATE_NOTE_URLS": {            # 改已发笔记的配图：id + urls（全量覆盖）
        # 2026-09-25 实测：对不存在的 id 会回 res=2 —— 真有校验，不是无脑成功。
        # 标题和正文**没有**对应接口，改不了。
        "path": "UpdateUploadNoteUrls",
        "required_params": ["id", "urls"],
    },
    "SET_NOTE_COLLECTION": {         # 收藏/取消收藏：nid + status(1=收藏, 0=取消)
        # 2026-09-25 从官方端 dex 挖出来并实测：这条**真的落库**。
        # 旧的 UpdateSTCollection 也返回 res=0，但收藏列表里查不到（假成功）。
        "path": "CollectShuatiNote",
        "required_params": ["status", "nid"],
    },

    # --- 点赞 ---
    "DISCOVER_NOTE_LIKE": {          # 笔记点赞/取消：nid + status(1=赞, 0=取消)
        "path": "DiscoverNoteLike",
        "required_params": ["nid", "status"],
        "default_params": {"status": "1"},
    },
    "GET_LIKE_SHUATI_NOTE": {        # 我赞过的笔记列表
        "path": "GetLikeShuatiNote",
        "required_params": ["page"],
        "default_params": {"page": "0"},
    },

    # --- 图片上传（multipart） ---    # 2026-09-25 实测：通用签名 + dir_name（白名单目录）+ file（文件字段）
    # 成功 → {"res":0,"illegal":false,"url":"http://imgcdn.yaerxing.com/upimage/<dir>/…"}
    # stupletter（私信图）需要会员；不在白名单的目录回「未创建文件夹:xxx」
    "UPLOAD_IMAGE": {
        "path": "OSSUploadImage4.php",
        "required_params": ["dir_name"],
        "default_params": {"dir_name": "stupnote"},
    },

    # --- 文件上传（multipart）---
    # 2026-09-25 实测：与图片同款（通用签名 + dir_name + file 字段），落到 upfile/ 目录。
    # 可传 pdf / txt；zip 会被拒（{"res":1,"error":"不允许的文件类型!"}）；
    # stupletter 目录不收文件（{"res":1,"error":"非法路径"}）。
    "UPLOAD_FILE": {
        "path": "OSSUploadFile2.php",
        "required_params": ["dir_name"],
        "default_params": {"dir_name": "stupnotefile"},
    },

    # --- 音频上传（multipart）---
    # 2026-09-25 实测 OSSUploadAudio2.php：
    #   通用签名 + 文件字段 file，**不需要 dir_name**（传了会被忽略）
    #   **只认 .mp3**（按扩展名判断，内容不校验）：
    #     成功 → {"res":0,"url":"http://imgcdn.yaerxing.com/audio/2026/09/25/<随机>.mp3"}
    #     其它 → {"res":1,"error":"upload audio failed"}
    "UPLOAD_AUDIO": {
        "path": "OSSUploadAudio2.php",
    },

    # --- 发布 / 删除笔记 ---
    # 2026-09-25 实测：UploadNote2 用的是 **comment 签名变体**（通用签名只会回「非法请求2」）。
    #   urls    : 已上传图片地址的 JSON 数组字符串，第一张成为封面（落进 logo 字段）
    #   title   : 标题（必填）
    #   content : 官方那套包体 JSON（见 NOTE_CONTENT_TPL）
    #   type    : 分区 type
    #   成功 → {"res":0,"id":"5145261"}
    #   少参数 → {"res":1,"remind_hint":"缺少参数 urls"}（**不是** "xxx field missing"）
    #   发太频 → {"res":2,"remind_hint":"发布频繁，两贴发布间隔至少5分钟"}
    "PUBLISH_NOTE": {
        "path": "UploadNote2",
        "sign": "comment",
        "required_params": ["title"],
        "default_params": {"type": "10"},
    },
    # 删除自己发的笔记：nid（通用签名），成功 → {"res":0}
    "DELETE_NOTE": {
        "path": "DeleteShuatiNote",
        "required_params": ["nid"],
    },

    # --- 试卷库（2026-09-26 实测，客户端 1.9.0 的「试卷」板块） ---
    #
    # 官方的在线答题是 H5（questionExercise-v17 / paperExercises-v18 / simpleUsePaper），
    # 第三方一律「非法访问-1」，所以只能做「试卷库」：浏览 / 筛选 / 收藏 + **读题目答案解析**。
    #
    # ⚠️ GetShuatiPaper5 / GetZJPaperById5 / GetSearchPapers7 的响应**没有 res 字段**，
    #    调用时必须 expect_res=False，否则会被当成失败。
    "GET_PAPERS": {                  # 试卷列表：page 分页，f_gradeid + version_id 筛选
        # 实测：只有这两个参数真正生效（version_id 必须搭配 f_gradeid，单独传 0 条）；
        # subject / xd / papertype 传了会被忽略。
        "path": "GetShuatiPaper5",
        "default_params": {"page": "0"},
    },
    "GET_PAPER_DETAIL": {            # 试卷详情：pid + paperid(同 pid) + type + aid=0
        # 返回 paper.questionlist[]，每组 {qtype, question[]}，
        # 题目带 question_text / answer_text / explanation_text（都是 HTML）。
        # ⚠️ 只对 **type=1（同步卷）** 有效，其它 type 回 res=1；不存在的 id 回 questionlist=null。
        "path": "GetZJPaperById5",
        "required_params": ["pid", "type"],
        "default_params": {"aid": "0"},
    },
    "GET_PAPER_VERSIONS": {          # 教材版本列表：filter=1 + subject + f_gradeid
        "path": "GetSTFilterData",
        "required_params": ["subject", "f_gradeid"],
        "default_params": {"filter": "1"},
    },
    "COLLECT_PAPER": {               # 收藏/取消收藏试卷：status(1/0) + type + pid
        # 实测可逆：收藏 pid=80446 → GetCollectionShuatiPaper5 里出现；取消 → 消失
        "path": "CollectShuatiPaper",
        "required_params": ["status", "type", "pid"],
    },
    "GET_PAPER_COLLECTIONS": {       # 我收藏的试卷：type 白名单 0/1/2/12（22 会 res=1）
        "path": "GetCollectionShuatiPaper5",
        "default_params": {"type": "1", "page": "0"},
    },
    "SEARCH_PAPERS": {               # 搜试卷：**comment 签名变体**，返回 matches[]
        "path": "GetSearchPapers7",
        "sign": "comment",
        "required_params": ["keyword", "page"],
        "default_params": {"page": "0", "ct": "20"},
    },
}

# 试卷库筛选用的年级（f_gradeid）。
#
# 实测校准：拿每个 f_gradeid 拉一页，按返回卷子的标题反推年级与学科
# （例：f_gradeid=7 全是「四年级」，f_gradeid=15 全是「八年级数学」）。
# subject 是这批卷子自带的学科码（3 数学 / 4 英语 / 7 化学），拉版本列表时要带上。
# 只留校准得准的项 —— f_gradeid=17/25 返回的卷子年级对不上，没放进来。
PAPER_GRADES = [
    {"id": "", "label": "最新", "xd": "", "subject": "4"},
    {"id": "1", "label": "一年级", "xd": "1", "subject": "4"},
    {"id": "3", "label": "二年级", "xd": "1", "subject": "4"},
    {"id": "5", "label": "三年级", "xd": "1", "subject": "4"},
    {"id": "7", "label": "四年级", "xd": "1", "subject": "4"},
    {"id": "9", "label": "五年级", "xd": "1", "subject": "4"},
    {"id": "11", "label": "六年级", "xd": "1", "subject": "4"},
    {"id": "13", "label": "七年级·数学", "xd": "2", "subject": "3"},
    {"id": "15", "label": "八年级·数学", "xd": "2", "subject": "3"},
    {"id": "19", "label": "八年级·英语", "xd": "2", "subject": "4"},
    {"id": "21", "label": "九年级·化学", "xd": "2", "subject": "7"},
    {"id": "23", "label": "高中·数学", "xd": "3", "subject": "3"},
]

# 收藏列表查的是同步卷（type=1）
PAPER_COLLECT_TYPE = "1"

# ⚠️ 已废弃（2026-09-25 复测）：这只是当初对「官方草稿格式」的猜测，
#   服务端**并不按这个结构解析** —— `content` 就是纯文本正文。
#   按它提交会把整串 JSON 当正文存下来。保留在文档里供后续研究参考。
NOTE_CONTENT_TPL = {
    "version": 1,
    "text": "",
    "update_count": 0,
    "up_count": 0,
    "sw_title": [],
    "sw_content": [],
    "sw": [],
    "si_urls": [],
    "si_label": [],
}

# 平台限制：两篇笔记之间至少间隔 5 分钟
NOTE_PUBLISH_MIN_INTERVAL_SEC = 300

# 正文里引用音频的标记前缀。
#
# 笔记正文没有音频字段（扫过 363 篇社区笔记，零音频痕迹），所以音频地址只能写进正文。
# 约定每段音频占一行：`[音频] http://imgcdn.yaerxing.com/audio/2026/09/25/xxx.mp3`
# 客户端读到这一行就渲染成播放器；官方端只会显示成一行普通文字。
AUDIO_MARK = "[音频]"

# 「赞过 / 收藏」这两个列表接口**不返回作者昵称与头像**，只有 home_id，
# 需要另外用 GetSTUserData 按 home_id 补资料。
AUTHORLESS_NOTE_APIS = ("GetLikeShuatiNote", "GetCollectionShuatiNote1")

# 上传目录（dir_name）取值
IMAGE_DIR_NOTE = "stupnote"        # 笔记配图
IMAGE_DIR_LETTER = "stupletter"    # 私信图片（需会员）
IMAGE_DIR_LOGO = "stuplogo"        # 头像
FILE_DIR_NOTE = "stupnotefile"     # 笔记附件 / 私信文件（唯一接受文件的目录）

# 私信消息类型（SendSTLetterMessage 的 type）
# 文本 1 / 图片 2 是推断值，文件 3 是外推值：三个都还没被真机验证
# （要连续签到满 3 天才能发私信，09-27 解锁）。发文件做了退化：type=3 不被接受时
# 自动改成「文本消息 + 附件链接」。
LETTER_TYPE_TEXT = "1"
LETTER_TYPE_IMAGE = "2"
LETTER_TYPE_FILE = "3"

# 每篇文章的投币上限
COIN_PER_NOTE_MAX = 2
