"""机器人引擎：扫描白名单作者的笔记 → 发现新评论 → 生成并发送回复。

安全约束（硬编码，不可由配置绕过）：
1. 只允许在白名单作者（owner_home_ids）的笔记下操作，其余笔记一律跳过。
2. 每天回复 / 评论总量、单次运行数量、两次动作最小间隔均有上限。
3. dry_run 为真时只打印不发送。
"""

import random
import re
import time
from dataclasses import dataclass
from datetime import datetime

from fkst_sdk import FkstClient, FkstAPIError, services as S
from bot.config import BotConfig
from bot.state import BotState


@dataclass
class Action:
    kind: str            # reply | comment | like | checkin | letter
    note_id: str
    note_title: str
    target_comment_id: str
    target_author: str
    target_content: str
    text: str
    other_mid: str = ""   # 私信动作专用：会话对方 mid


class BotEngine:
    def __init__(self, client: FkstClient, cfg: BotConfig, state: BotState, log=print):
        self.client = client
        self.cfg = cfg
        self.state = state
        self.log = log
        self.bot_home_id = str(client.mid or "")
        self.bot_nick = ""
        self._note_cache: dict = {}
        self.actions: list[Action] = []
        if cfg.scan.since_timestamp:
            state.set_since(int(cfg.scan.since_timestamp))
        # 只处理水位之后的评论；关闭开关时水位置 0（= 全部历史评论）
        self.since = state.since if cfg.scan.only_new_comments else 0

    # ------------------------------------------------------------------ 素材
    def fetch_owner_notes(self):
        notes = []
        for home_id in self.cfg.owner_home_ids:
            try:
                r = S.get_user_notes(self.client, home_id, "0", "0")
            except FkstAPIError as e:
                self.log(f"  ! 读取 {home_id} 的笔记失败: {e}")
                continue
            for n in r.get("notes") or []:
                n["_owner"] = str(home_id)
                notes.append(n)
        notes.sort(key=lambda n: int(n.get("created_at") or 0), reverse=True)
        return notes[: self.cfg.scan.notes_per_scan]

    def fetch_comments(self, note_id):
        comments = []
        for page in range(self.cfg.scan.max_pages):
            try:
                part = S.get_comments(self.client, note_id, page=str(page),
                                      ct=str(self.cfg.scan.comments_per_note))
            except FkstAPIError as e:
                self.log(f"  ! 读取评论失败 (nid={note_id}): {e}")
                break
            if not part:
                break
            comments.extend(part)
            if len(part) < self.cfg.scan.comments_per_note:
                break
        return comments

    # ------------------------------------------------------------------ 规则
    def pick_reply(self, comment: dict) -> str | None:
        cfg = self.cfg.reply
        content = _plain(comment.get("content") or "")
        author = str(comment.get("home_id") or "")

        if author == self.bot_home_id:
            return None
        if author in [str(x) for x in cfg.skip_authors]:
            return None
        if cfg.skip_note_author and author in [str(x) for x in self.cfg.owner_home_ids]:
            return None
        if len(content) < cfg.min_content_length:
            return None
        if cfg.skip_pure_mention and _is_pure_mention(content):
            return None
        for kw in cfg.skip_if_contains:
            if kw and kw in content:
                return None
        if cfg.only_if_contains and not any(kw in content for kw in cfg.only_if_contains):
            return None

        rules = cfg.templates.get("keyword_rules") or []
        for rule in rules:
            if any(kw in content for kw in rule.get("match", [])):
                pool = rule.get("replies") or []
                if pool:
                    return random.choice(pool)
        pool = cfg.templates.get("default") or ["谢谢你的评论～"]
        return random.choice(pool)

    def replied_by_bot(self, comment: dict) -> bool:
        """顶层评论的 replies 里是否已有本机器人发的回复。"""
        for rep in comment.get("replies") or []:
            if str(rep.get("home_id") or "") == self.bot_home_id:
                return True
            if str(rep.get("nick_name") or "") == self.bot_nick:
                return True
        return self.state.already_replied(comment.get("id"))

    # ------------------------------------------------------------------ 主流程
    def scan_once(self) -> list[Action]:
        self.actions = []

        # 签到不依赖白名单，先做（连续 3 天才能解锁发私信）
        if self.cfg.checkin.enabled and not self.state.checked_in_today:
            self.actions.append(Action(
                kind="checkin", note_id="", note_title="", target_comment_id="",
                target_author="", target_content="", text="每日签到",
            ))

        if not self.cfg.owner_home_ids:
            self.log("!! 未配置 owner_home_ids，出于安全考虑不执行任何动作")
            return self.actions

        self.bind_nick()
        notes = self.fetch_owner_notes()
        self.log(f"扫描到 {len(notes)} 篇白名单作者的笔记")
        per_run = 0
        for note in notes:
            if per_run >= self.cfg.reply.max_replies_per_run:
                break
            if self.state.day_replies >= self.cfg.reply.max_replies_per_day:
                self.log("今日回复已达上限，停止")
                break
            nid = str(note.get("id"))
            comments = self.fetch_comments(nid)
            for c in comments:
                if per_run >= self.cfg.reply.max_replies_per_run:
                    break
                if c.get("is_del") in ("1", 1) or c.get("status") not in (None, "0", 0):
                    continue
                if self.cfg.scan.only_new_comments and int(c.get("created_at") or 0) <= self.since:
                    continue
                if self.replied_by_bot(c):
                    continue
                text = self.pick_reply(c)
                if not text:
                    continue
                self.actions.append(Action(
                    kind="reply", note_id=nid, note_title=note.get("title", ""),
                    target_comment_id=str(c.get("id")),
                    target_author=str(c.get("nick_name") or c.get("home_id")),
                    target_content=_plain(c.get("content") or "")[:60],
                    text=text.format(nick=c.get("nick_name") or "同学"),
                ))
                per_run += 1

        for note_id in self.cfg.top_comment.note_ids if self.cfg.top_comment.enabled else []:
            if self.state.already_commented(note_id):
                continue
            if self.state.day_comments >= self.cfg.top_comment.max_per_day:
                break
            pool = self.cfg.top_comment.templates or ["来打卡啦～"]
            self.actions.append(Action(
                kind="comment", note_id=str(note_id), note_title="", target_comment_id="0",
                target_author="", target_content="", text=random.choice(pool),
            ))

        if self.cfg.auto_like.enabled:
            self.actions.extend(self.collect_likes(notes))

        if self.cfg.letter.enabled:
            self.actions.extend(self.collect_letter_replies())

        return self.actions

    # ---------------------------------------------------------------- 私信
    def collect_letter_replies(self) -> list[Action]:
        """读取好友私信，按规则生成回复。

        平台限制：账号需连续签到满 3 天才能发私信；
        未达门槛时只读不发（避免每轮都撞同一个错误）。
        """
        cfg = self.cfg.letter
        actions = []
        if not cfg.auto_reply:
            return actions

        try:
            allowed, hint = S.can_dm(self.client)
        except FkstAPIError as e:
            self.log(f"  ! 私信状态检查失败: {e}")
            return actions
        if not allowed:
            self.log(f"  · 私信暂不可发（{hint}），本轮只读不回复")
            can_send = False
        else:
            can_send = True

        try:
            buddies = S.get_buddies(self.client, "1")
        except FkstAPIError as e:
            self.log(f"  ! 读取好友列表失败: {e}")
            return actions

        owners = [str(x) for x in self.cfg.owner_home_ids]
        for b in buddies:
            if len(actions) >= cfg.max_replies_per_run:
                break
            if self.state.day_letter_replies + len(actions) >= cfg.max_replies_per_day:
                self.log("今日私信回复已达上限，停止")
                break
            mid = str(b.get("home_id") or "")
            if not mid:
                continue
            if cfg.owners_only and mid not in owners:
                continue
            try:
                thread = S.get_letters(self.client, mid)
            except FkstAPIError as e:
                self.log(f"  ! 读取与 {mid} 的私信失败: {e}")
                continue
            for msg in thread.get("letters") or []:
                if len(actions) >= cfg.max_replies_per_run:
                    break
                sender = str(msg.get("home_id") or msg.get("from_mid") or "")
                if sender == self.bot_home_id:          # 自己发的不回
                    continue
                if str(msg.get("is_del") or "0") in ("1", 1):
                    continue
                if self.state.already_replied_letter(msg.get("id")):
                    continue
                content = _plain(msg.get("content") or "")
                if any(kw and kw in content for kw in cfg.skip_if_contains):
                    continue
                text = self.pick_template(cfg.templates, content)
                if not text:
                    continue
                actions.append(Action(
                    kind="letter", note_id="",
                    note_title="", target_comment_id=str(msg.get("id") or ""),
                    target_author=str(b.get("nick_name") or mid),
                    target_content=content[:60], text=text, other_mid=mid,
                ))
        if actions and not can_send:
            self.log(f"  · 有 {len(actions)} 条待回私信，但要等签到满 3 天才能发")
            return []
        return actions

    @staticmethod
    def pick_template(templates: dict, content: str) -> str | None:
        """按关键词规则挑话术，没有命中就用 default。"""
        templates = templates or {}
        for rule in templates.get("keyword_rules") or []:
            if any(kw and kw in content for kw in (rule.get("match") or [])):
                pool = rule.get("replies") or []
                if pool:
                    return random.choice(pool).format(content=content)
        pool = templates.get("default") or []
        return random.choice(pool) if pool else None

    def collect_likes(self, notes) -> list[Action]:
        """给白名单作者的笔记点赞（含历史笔记）。"""
        cfg = self.cfg.auto_like
        actions = []
        liked_on_server = set()
        if cfg.skip_already_liked:
            try:
                liked_on_server = {str(x.get("id")) for x in S.get_liked_notes(self.client)}
            except FkstAPIError as e:
                self.log(f"  ! 读取已赞列表失败: {e}")

        for note in notes:
            if len(actions) >= cfg.max_per_run:
                break
            if self.state.day_likes + len(actions) >= cfg.max_per_day:
                self.log("今日点赞已达上限，停止")
                break
            nid = str(note.get("id"))
            if nid in liked_on_server or self.state.already_liked(nid):
                continue
            actions.append(Action(
                kind="like", note_id=nid, note_title=str(note.get("title") or ""),
                target_comment_id="", target_author="", target_content="",
                text=f"点赞（当前 {note.get('like_count')} 赞）",
            ))
        return actions

    def execute(self, actions: list[Action] | None = None, dry_run: bool | None = None):
        actions = actions if actions is not None else self.actions
        dry = self.cfg.safety.dry_run if dry_run is None else dry_run

        if not actions:
            self.log("没有需要执行的动作")
            return []

        done = []
        for act in actions:
            note = self._note_by_id(act.note_id)
            owner = str(note.get("home_id") or note.get("_owner") or "")
            if owner and owner not in [str(x) for x in self.cfg.owner_home_ids]:
                self.log(f"× 跳过（非白名单作者 {owner}）: 笔记 {act.note_id}")
                continue

            if act.kind == "like":
                wait_for = self.cfg.auto_like.min_seconds_between
            elif act.kind == "letter":
                wait_for = self.cfg.letter.min_seconds_between_actions
            else:
                wait_for = self.cfg.reply.min_seconds_between_actions
            wait = wait_for - self.state.seconds_since_action()
            if wait > 0 and not dry:
                self.log(f"  … 等待 {wait:.0f}s 以控制频率")
                time.sleep(wait)

            _label = {"reply": "回复", "comment": "发评论", "like": "点赞",
                      "checkin": "签到", "letter": "回私信"}.get(act.kind, act.kind)
            if act.kind in ("like", "checkin"):
                head = f"{'[演练] ' if dry else ''}{_label} → {act.text}"
            else:
                head = (f"{'[演练] ' if dry else ''}{_label} "
                        f"| 笔记 {act.note_id} 《{act.note_title[:16]}》")
            if act.kind == "reply":
                self.log(f"{head} | 对象 {act.target_author}: 「{act.target_content}」")
                self.log(f"    → 发送: 「{act.text}」")
            elif act.kind == "letter":
                self.log(f"{head} | 来自 {act.target_author}: 「{act.target_content}」")
                self.log(f"    → 回复: 「{act.text}」")
            elif act.kind == "like":
                self.log(head)
            elif act.kind == "checkin":
                self.log(head)
            else:
                self.log(f"{head} → 发送顶层评论: 「{act.text}」")

            if dry:
                continue
            try:
                if act.kind == "reply":
                    S.post_comment(self.client, act.text, act.note_id, act.target_comment_id)
                    self.state.mark_replied(act.target_comment_id)
                elif act.kind == "like":
                    S.like_note(self.client, act.note_id)
                    self.state.mark_liked(act.note_id)
                    time.sleep(self.cfg.auto_like.min_seconds_between)
                elif act.kind == "checkin":
                    res = S.check_in(self.client)
                    self.state.mark_checked_in()
                    state = res.get("state") or S.get_coin_state(self.client)
                    self.log(f"    ✓ 签到完成：连续 {state.get('get_coin_day')} 天，"
                             f"金币 {state.get('coin_count')}"
                             + ("（今日已签）" if res.get("already") else ""))
                elif act.kind == "letter":
                    S.send_letter(self.client, act.other_mid, act.text)
                    self.state.mark_replied_letter(act.target_comment_id or act.target_content)
                else:
                    S.post_comment(self.client, act.text, act.note_id)
                    self.state.mark_commented(act.note_id)
                if act.kind != "checkin":
                    done.append(act)
                    self.log("    ✓ 已发送")
            except FkstAPIError as e:
                self.log(f"    ✗ 执行失败: {e}")
                if "url illegal" in str(e):
                    self.log("    ! 签名被拒，可能官方已升级签名算法，需要重新对齐")
        return done

    # ------------------------------------------------------------------ 工具
    def _note_by_id(self, note_id):
        if not self._note_cache:
            for n in self.fetch_owner_notes():
                self._note_cache[str(n.get("id"))] = n
        return self._note_cache.get(str(note_id), {})

    def bind_nick(self):
        try:
            me = S.get_user_data(self.client, self.bot_home_id)
            self.bot_nick = (me.get("info") or {}).get("nick_name") or ""
        except Exception:
            self.bot_nick = ""
        return self.bot_nick


def _plain(text: str) -> str:
    """去掉 @提及 的 HTML 包装，方便关键词匹配。"""
    text = re.sub(r"<span[^>]*class='fkst-at'[^>]*>(.*?)</span>", r"\1", text or "")
    return re.sub(r"<[^>]+>", "", text).strip()


_MENTION_RE = re.compile(r"^(@[^\s@]+[\s,，、]*)+$")


def _is_pure_mention(text: str) -> bool:
    """整条评论只有 @某某，没有任何实义内容。"""
    return bool(_MENTION_RE.match(text.strip()))


def now() -> str:
    return datetime.now().strftime("%H:%M:%S")
