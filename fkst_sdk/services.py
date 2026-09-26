"""业务封装：把接口调用整理成好用的函数。"""

import random
import time

from .client import FkstClient
from .constants import GET_NOTE_PARAMS
from .crypto import decrypt_content, strip_tags


# --------------------------------------------------------------- 账号
def login(client: FkstClient, phone: str, password: str) -> dict:
    return client.login(phone, password)


def whoami(client: FkstClient) -> dict:
    return client.request("GET_MY_DATA", all_black_member="1")


def get_user_data(client: FkstClient, home_id) -> dict:
    return client.request("GET_USER_DATA", home_id=str(home_id))


def get_linkman_list(client: FkstClient, updated_at: str = "0", page: str = "0") -> dict:
    return client.request("GET_LINKMAN_LIST", updated_at=str(updated_at), page=str(page))


def get_buddies(client: FkstClient, type_: str = "1", page: str = "0") -> list:
    """好友列表。type=1 互关（用来做私信会话列表）、2 我关注的、3 粉丝。

    ⚠️ 这是**分页**接口：一页 10 条，`over=True` 才表示取完了。
    只要第一页会漏人，要全量请用 [get_all_buddies]。
    """
    payload = client.request("GET_BUDDIES", type=str(type_), page=str(page))
    return payload.get("buddies") or []


def get_buddies_page(client: FkstClient, type_: str = "1", page: str = "0") -> dict:
    """带分页信息的好友列表：{"buddies": [...], "over": bool}"""
    payload = client.request("GET_BUDDIES", type=str(type_), page=str(page))
    return {"buddies": payload.get("buddies") or [], "over": bool(payload.get("over"))}


def get_all_buddies(client: FkstClient, type_: str = "1", max_pages: int = 20) -> list:
    """翻页取全（关注几十个人时只有这样才能拿齐）。"""
    out: list = []
    page = 0
    while page < max_pages:
        payload = get_buddies_page(client, type_, str(page))
        items = payload["buddies"]
        out.extend(items)
        if payload["over"] or not items:
            break
        page += 1
    return out


def get_user_brief(client: FkstClient, home_id) -> dict:
    """按 home_id 拿一份作者简介（昵称 / 头像 / 省份）。

    「赞过」（GetLikeShuatiNote）与「收藏」（GetCollectionShuatiNote1）两个接口
    返回的笔记只有 `home_id`，**没有 `nick_name` / `logo`**，所以在客户端里
    列表作者会全变成「匿名」。补的办法就是拿 home_id 来这里查一次并缓存。
    """
    from .constants import AUTHORLESS_NOTE_APIS  # noqa: F401  (说明用)
    data = get_user_data(client, home_id)
    info = data.get("info") or {}
    return {
        "home_id": str(home_id),
        "nick_name": info.get("nick_name") or "",
        "logo": info.get("logo") or "",
        "province": info.get("province") or "",
    }


def get_notices(client: FkstClient, type_: str = "2", page: str = "0") -> dict:
    return client.request("GET_NOTICES2", type=type_, page=page)


# --------------------------------------------------------------- 内容
def get_discover_notes(client: FkstClient, type_: str = "10", page: str = "0",
                       start_time: str = "0", **extra) -> dict:
    return client.request("GET_DISCOVER_TAG_NOTES2", type=type_, page=str(page),
                          start_time=str(start_time), **extra)


def get_follow_notes(client: FkstClient, page: str = "0", min_time: str | None = None,
                     **extra) -> dict:
    kwargs = {"page": str(page), **extra}
    if min_time:
        kwargs["min_time"] = str(min_time)
    return client.request("GET_FOLLOW_USER_NOTES2", **kwargs)


def get_user_notes(client: FkstClient, home_id, type_: str = "0", page: str = "0",
                   **extra) -> dict:
    return client.request("GET_USER_NOTES2", home_id=str(home_id), type=type_,
                          page=str(page), **extra)


def get_my_notes(client: FkstClient, page: str = "0", type_: str = "0") -> dict:
    mid = client.mid or client.dynamic_params.get("mid")
    if not mid:
        raise RuntimeError("尚未登录，拿不到自己的 mid")
    return get_user_notes(client, mid, type_, page)


def search_notes(client: FkstClient, keyword: str, page: str = "0", ct: str = "20",
                 **extra) -> list:
    """搜索笔记/讲解。返回 matches[]，字段是 nid / title / thumb（≠ 社区笔记结构）。"""
    payload = client.request("SEARCH_NOTES", keyword=keyword, page=str(page),
                             ct=str(ct), **extra)
    return payload.get("matches") or []


def get_note_detail(client: FkstClient, note_id, decrypt: bool = True) -> dict:
    """获取文章详情。返回 {note_id, secret_key, html, cipher, content}"""
    secret_key = random.randint(100000, 2000000)
    params = dict(GET_NOTE_PARAMS)
    params.update({
        "secret_key": str(secret_key),
        "timestamp": str(int(time.time() * 1000)),
        "id": str(note_id),
    })
    payload = client.request("GET_NOTE", **params)
    html = payload["data"]["html"]
    cipher = payload["data"]["content"]
    content = decrypt_content(cipher, secret_key) if (decrypt and cipher) else ""
    return {
        "note_id": str(note_id),
        "secret_key": secret_key,
        "html": html,
        "cipher": cipher,
        "content": content,
        "text": strip_tags(content),
    }


# --------------------------------------------------------------- 评论
def get_comments(client: FkstClient, note_id, page: str = "0",
                 order_type: str = "1", ct: str = "10") -> list:
    payload = client.request("GET_COMMENT_BY_NID2", nid=str(note_id), page=str(page),
                             order_type=order_type, ct=str(ct),
                             start_time=str(int(time.time())))
    return payload.get("comments") or []


def get_replies(client: FkstClient, comment_id, page: str = "0",
                order_type: str = "1", ct: str = "20") -> list:
    payload = client.request("GET_COMMENT_BY_FID2", fid=str(comment_id), page=str(page),
                             order_type=order_type, ct=str(ct),
                             start_time=str(int(time.time())))
    return payload.get("comments") or []


def post_comment(client: FkstClient, content: str, note_id,
                 parent_comment_id: str = "0") -> dict:
    """发评论（fid=0 为顶层评论，否则为回复）。"""
    content = (content or "").strip()
    if not content:
        raise ValueError("评论内容不能为空")
    return client.request("SET_NOTE_COMMENT1", content=content, nid=str(note_id),
                          fid=str(parent_comment_id))


def delete_comment(client: FkstClient, comment_id) -> dict:
    return client.request("DEL_NOTE_COMMENT", id=str(comment_id))


def like_note_comment(client: FkstClient, comment_id) -> dict:
    return client.request("SET_NOTE_COMMENT_LIKE", id=str(comment_id))


# --------------------------------------------------------------- 点赞
def like_note(client: FkstClient, note_id, like: bool = True) -> dict:
    """给笔记点赞 / 取消赞。"""
    return client.request("DISCOVER_NOTE_LIKE", nid=str(note_id),
                          status="1" if like else "0")


def get_liked_notes(client: FkstClient, page: str = "0") -> list:
    payload = client.request("GET_LIKE_SHUATI_NOTE", page=str(page))
    return payload.get("notes") or []


# --------------------------------------------------------------- 私信（信件）
def get_letters(client: FkstClient, other_mid, letter_id: str = "0",
                flag: str = "2", page: str = "0") -> dict:
    """拉取与某个用户的私信记录。

    需要双方的 mid 才能定位会话。
    flag=2 首次打开（同时返回对方 member 资料），flag=1 翻页取更早的消息；
    letter_id 传 "0" 表示从最新一页开始。

    返回 {"member": {...}|None, "letters": [...]}
    """
    if not client.mid:
        raise ValueError("未登录，无法读取私信")
    payload = client.request(
        "GET_LETTER_MESSAGE",
        my_mid=str(client.mid), other_mid=str(other_mid),
        letter_id=str(letter_id), flag=str(flag), page=str(page),
    )
    return {"member": payload.get("member"), "letters": payload.get("letters") or []}


def send_letter(client: FkstClient, other_mid, content: str, type_: str = "1") -> dict:
    """发送私信。

    ⚠️ 平台限制：账号需连续签到满 3 天才能对外发私信，
    否则服务端返回 res=2 且 remind_hint 为「您签到尚未满3天…」。
    """
    if not client.mid:
        raise ValueError("未登录，无法发送私信")
    return client.request(
        "SEND_LETTER_MESSAGE",
        my_mid=str(client.mid), other_mid=str(other_mid),
        content=content, type=str(type_),
    )


def delete_letters(client: FkstClient, letter_id) -> dict:
    return client.request("DEL_LETTER_MESSAGES", id=str(letter_id))


def send_letter_file(client: FkstClient, other_mid, file_url: str,
                     file_name: str = "") -> dict:
    """把文件地址当作私信内容发出去。

    文件消息类型（type=3）没能真机验证过，所以这里两层兜底：
    先按文件类型发，服务端不认就退化成「文本消息 + 附件链接」。
    返回 {"sent": bool, "fell_back": bool, "payload": {...}}
    """
    from .constants import LETTER_TYPE_FILE, LETTER_TYPE_TEXT
    if not client.mid:
        raise ValueError("未登录，无法发送私信")

    typed = client.request(
        "SEND_LETTER_MESSAGE", expect_res=False,
        my_mid=str(client.mid), other_mid=str(other_mid),
        content=file_url, type=LETTER_TYPE_FILE, file_name=file_name,
    )
    if str(typed.get("res")) == "0":
        return {"sent": True, "fell_back": False, "payload": typed}

    link = "[文件] " + (file_name + "\n" if file_name else "") + file_url
    fell = client.request(
        "SEND_LETTER_MESSAGE", expect_res=False,
        my_mid=str(client.mid), other_mid=str(other_mid),
        content=link, type=LETTER_TYPE_TEXT,
    )
    ok = str(fell.get("res")) == "0"
    return {"sent": ok, "fell_back": True, "payload": fell if ok else typed}


# --------------------------------------------------------------- 投币
def coin_to_note(client: FkstClient, note_id, count: int = 1) -> dict:
    """给一篇文章投币（硬币 → 文章）。

    实测（2026-09-25）：`STCoin2Note` + `nid` + `count`，通用签名。
    成功回 {"res":0}，账号 coin_count 减少，文章的 coins 字段增加。
    平台每篇文章最多 2 枚（这个上限是我们自己卡在客户端的）。
    """
    return client.request("COIN_TO_NOTE", expect_res=False,
                          nid=str(note_id), count=str(max(1, int(count))))


def can_dm(client: FkstClient) -> tuple[bool, str]:
    """检查当前账号是否具备发私信资格（连续签到天数）。"""
    data = whoami(client)
    days = int(data.get("get_coin_day") or 0)
    hint = "" if days >= 3 else f"签到 {days}/3 天，连续签到满 3 天才能发私信"
    return days >= 3, hint


# --------------------------------------------------------------- 签到
def get_coin_state(client: FkstClient) -> dict:
    """金币 / 签到状态：coin_count 余额，get_coin_day 连续签到天数，get_coin_status 今日是否已签。"""
    data = whoami(client)
    return {
        "coin_count": data.get("coin_count"),
        "get_coin_day": data.get("get_coin_day"),
        "get_coin_status": data.get("get_coin_status"),
    }


def check_in(client: FkstClient, day: str | None = None, coin: str = "1") -> dict:
    """每日签到（领金币）。

    day 省略时自动取「连续签到天数 + 1」。
    重复签到服务端回 res=2。
    """
    state = get_coin_state(client)
    if str(state.get("get_coin_status")) == "1":
        return {"res": 2, "already": True, "state": state}
    cur = int(state.get("get_coin_day") or 0)
    payload = client.request("ADD_COIN", coin=str(coin), day=str(day or (cur + 1)))
    payload["already"] = False
    return payload


# --------------------------------------------------------------- 关注 / 收藏
def follow_user(client: FkstClient, home_id, follow: bool = True) -> dict:
    """关注 / 取消关注。

    2026-09-25 实测：
      STFollow + home_id + id + status  → res 0
      status=1 关注（返回 res=0）
      status=2 取关（返回 {"follow_status": 0, "res": 0}）
    注意 SetSTFollowMark 不是关注接口，而是「设置关注备注」。
    """
    return client.request(
        "FOLLOW_USER",
        home_id=str(home_id), id=str(home_id), status="1" if follow else "2",
    )


def set_follow_remark(client: FkstClient, home_id, relation_id, mark: str) -> dict:
    """设置关注备注（id 用 GetSTBuddies 返回的关系 id）。"""
    return client.request(
        "SET_FOLLOW_MARK",
        home_id=str(home_id), id=str(relation_id), mark=str(mark),
    )


def get_collection_notes(client: FkstClient, page: str = "0") -> list:
    """我收藏的笔记。"""
    payload = client.request("GET_COLLECTION_NOTES", page=str(page))
    return payload.get("notes") or []


def update_note_images(client: FkstClient, nid, urls) -> dict:
    """改已发笔记的配图（**全量覆盖**，没传进去的旧图会被摘掉）。

    服务端对不存在的 id 回 res=2，是真有校验的接口。
    标题和正文改不了 —— 服务端没开放对应接口。
    """
    import json as _json
    return client.request(
        "UPDATE_NOTE_URLS", expect_res=False,
        id=str(nid), urls=_json.dumps(list(urls), ensure_ascii=False),
    )


def collect_note(client: FkstClient, nid, on: bool = True) -> dict:
    """收藏 / 取消收藏一条笔记（nid + status，status=1 收藏、0 取消）。

    2026-09-25 实测：这才是真正落库的接口。收藏后 GetCollectionShuatiNote1
    里能查到该 id，取消后消失（可逆验证过）。旧的 `UpdateSTCollection`
    返回 res=0 但不落库，是假成功，别再用。
    """
    return client.request(
        "SET_NOTE_COLLECTION", expect_res=False,
        nid=str(nid), status="1" if on else "0",
    )


# --------------------------------------------------------------- 发布 / 删除笔记
def build_note_content(text: str, audio_urls=None) -> str:
    """发布笔记的 `content` 字段 —— **就是纯文本正文本身，不要包成 JSON**。

    2026-09-25 复测修正了旧结论：服务端把这个字段当**正文文本**存，不做协议解析。
    官方笔记在列表接口里返回的 `content` 就是纯文本（如 `'好久不见'`），
    `GetNote` 解密出来是 `<p>好久不见</p>`。

    早先按 `NOTE_CONTENT_TPL` 那坨 JSON 提交，整串 JSON 会被原样当成正文存下来，
    列表和详情页都会显示出这坨 JSON。

    音频没有对应字段，改用每段一行 `[音频] <url>` 追加在正文末尾。
    """
    from .constants import AUDIO_MARK
    body = (text or "").strip()
    marks = "\n".join(f"{AUDIO_MARK} {u}" for u in (audio_urls or []) if u)
    if not marks:
        return body
    return f"{body}\n{marks}" if body else marks


def publish_note(client: FkstClient, title: str, text: str = "",
                 image_urls=None, audio_urls=None, type_: str = "10") -> dict:
    """发布一篇社区笔记。

    2026-09-25 实测走 `UploadNote2`，**comment 签名变体**（通用签名回「非法请求2」）：

        urls    : 已上传图片地址列表（JSON 数组字符串，第一张成为封面）
        title   : 标题（必填，为空服务端回「缺少参数 title」）
        content : build_note_content(text, audio_urls) —— 纯文本正文
        type    : 分区 type，取值见 constants.CATEGORIES

    成功 → {"res": 0, "id": "5145261"}。

    ⚠️ 频率限制：两贴间隔至少 5 分钟，否则 res=2「发布频繁，两贴发布间隔至少5分钟」。
    图片请先 `upload_image(..., dir_name="stupnote")`、音频先 `upload_audio(...)`
    拿到 url 再传进来。
    """
    import json as _json
    title = (title or "").strip()
    if not title:
        raise ValueError("标题不能为空")

    urls = _json.dumps([str(u) for u in (image_urls or []) if u], ensure_ascii=False)
    return client.request(
        "PUBLISH_NOTE", expect_res=False,
        title=title,
        content=build_note_content(text, audio_urls),
        urls=urls,
        type=str(type_),
    )


def delete_note(client: FkstClient, note_id) -> dict:
    """删除自己发的笔记（DeleteShuatiNote + nid，通用签名，成功 → {"res":0}）。"""
    return client.request("DELETE_NOTE", expect_res=False, nid=str(note_id))


# --------------------------------------------------------------- 图片上传
def upload_image(client, path, dir_name="stupnote", filename=None, mime="image/jpeg"):
    """上传本地图片文件，返回服务端响应（含 url / illegal）。

    dir_name 取值见 constants：stupnote（笔记）、stupletter（私信图，需会员）、stuplogo。

    用法：
        r = upload_image(client, "a.jpg")
        if r.get("res") == 0 and not r.get("illegal"):
            print(r["url"])           # http://imgcdn.yaerxing.com/upimage/stupnote/…
    """
    from pathlib import Path as _P
    p = _P(path)
    data = p.read_bytes()
    name = filename or p.name
    return client.upload_image(data, filename=name, mime=mime, dir_name=dir_name)


# --------------------------------------------------------------- 音频上传
def upload_audio(client, path, filename=None, mime="audio/mpeg"):
    """上传本地音频（发布笔记时用），返回服务端响应（含 url）。

    实测（2026-09-25）`OSSUploadAudio2.php`：
      - 通用签名 + 文件字段 `file`，**不需要 dir_name**（传了会被忽略）
      - **只收 `.mp3`**：wav / m4a 一律 `{"res":1,"error":"upload audio failed"}`
      - 服务端按**扩展名**判断，不校验内容
      - 成功 → `{"res":0,"url":"http://imgcdn.yaerxing.com/audio/2026/09/25/<随机>.mp3"}`

    用法：
        r = upload_audio(client, "voice.mp3")
        if r.get("res") == 0:
            print(r["url"])

    笔记正文并没有音频字段，所以拿到 url 后要让 `publish_note` 把
    `[音频] <url>` 拼进正文（见 `build_note_content`）。
    """
    from pathlib import Path as _P
    p = _P(path)
    data = p.read_bytes()
    name = filename or p.name
    return client.upload_audio(data, filename=name, mime=mime)


# --------------------------------------------------------------- 试卷库
#
# 2026-09-26 实测。官方的在线答题是 H5，第三方一律「非法访问-1」，
# 所以这里做的是「试卷库」：按年级/教材版本浏览真实试卷、搜试卷、收藏，
# 并且**能直接把题目、答案、解析读出来**（GetZJPaperById5，只对 type=1 的同步卷有效）。
#
# ⚠️ GetShuatiPaper5 / GetZJPaperById5 / GetSearchPapers7 的响应里**没有 res 字段**，
#    这几个函数内部统一走 expect_res=False。

def get_papers(client, page=0, f_gradeid="", version_id=""):
    """试卷列表（GetShuatiPaper5）→ {"papers": [...], "over": bool}

    实测只有 `f_gradeid`（年级）和 `version_id`（教材版本）会生效，
    且 `version_id` 必须跟 `f_gradeid` 一起传（单独传 0 条）。
    `subject` / `xd` / `papertype` 传了会被服务端忽略。
    """
    params = {"page": str(page)}
    if f_gradeid:
        params["f_gradeid"] = str(f_gradeid)
    if version_id:
        params["version_id"] = str(version_id)
    return client.request("GET_PAPERS", expect_res=False, **params)


def get_paper_detail(client, pid, type_="1", aid="0"):
    """试卷详情（GetZJPaperById5）→ {"paper": {..., "questionlist": [...]}, "tag": ...}

    `pid` 与 `paperid` 都填试卷 id，`type` 填试卷自带的 type。
    ⚠️ **只有 type=1（同步卷）拿得到题目**，其它 type 回 res=1；
    id 不存在时 `questionlist` 为 None（真有校验，不是无脑成功）。
    """
    return client.request(
        "GET_PAPER_DETAIL", expect_res=False,
        pid=str(pid), paperid=str(pid), type=str(type_), aid=str(aid),
    )


def get_paper_versions(client, subject, f_gradeid, filter_="1"):
    """教材版本列表（GetSTFilterData）→ [{"version": "人教版（新教材）", "version_id": "196579"}, …]"""
    r = client.request(
        "GET_PAPER_VERSIONS",
        filter=str(filter_), subject=str(subject), f_gradeid=str(f_gradeid),
    )
    return r.get("filters") or []


def collect_paper(client, pid, type_="1", on=True):
    """收藏 / 取消收藏试卷（CollectShuatiPaper：status + type + pid）。

    可逆验证过：收藏 pid=80446 → 收藏列表出现；取消 → 消失。
    """
    return client.request(
        "COLLECT_PAPER", expect_res=False,
        status="1" if on else "0", type=str(type_), pid=str(pid),
    )


def get_paper_collections(client, page=0, type_=None):
    """我收藏的试卷（GetCollectionShuatiPaper5）→ {"papers": [...], "over": bool}

    type 有白名单：0 / 1 / 2 / 12 有效，其它值（如考研卷的 22）回 res=1。
    """
    from .constants import PAPER_COLLECT_TYPE
    return client.request(
        "GET_PAPER_COLLECTIONS",
        type=str(type_ or PAPER_COLLECT_TYPE), page=str(page),
    )


def search_papers(client, keyword, page=0, ct=20):
    """搜试卷（GetSearchPapers7，**comment 签名变体**）→ matches[]

    返回字段是 pid / topic_title / logo / type / tag1，跟列表接口的 papers[] 不同。
    搜出来的多是考研（22）/ 专升本（14）这类卷子，**看不了题目**。
    """
    return client.request(
        "SEARCH_PAPERS", expect_res=False,
        keyword=keyword, page=str(page), ct=str(ct),
    )
