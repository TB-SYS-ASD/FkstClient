"""机器人配置：.env 读账号，config.json 读行为规则。"""

import json
import os
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / ".env"
CONFIG_FILE = ROOT / "bot" / "config.json"
STATE_FILE = ROOT / "bot" / "state.json"


def load_env(path: Path = ENV_FILE):
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())


@dataclass
class ScanConfig:
    interval_seconds: int = 300
    notes_per_scan: int = 5
    comments_per_note: int = 20
    max_pages: int = 1
    # only_new_comments: 只回复机器人启用之后产生的新评论（避免翻旧账）
    only_new_comments: bool = True
    # 手动指定起始时间戳（秒）；留空则用首次运行时刻
    since_timestamp: str = ""


@dataclass
class ReplyConfig:
    enabled: bool = True
    mode: str = "keyword"              # all | keyword
    only_if_contains: list = field(default_factory=list)
    skip_if_contains: list = field(default_factory=lambda: ["举报", "投诉", "违法"])
    skip_authors: list = field(default_factory=list)
    skip_note_author: bool = True       # 不回博主自己的评论
    min_content_length: int = 3         # 过滤 "O_o" 这类无意义短评
    skip_pure_mention: bool = True      # 过滤 "@某某" 这种纯@
    templates: dict = field(default_factory=dict)
    max_replies_per_run: int = 3
    max_replies_per_day: int = 20
    min_seconds_between_actions: int = 45


@dataclass
class TopCommentConfig:
    """主动在指定笔记下发顶层评论。"""
    enabled: bool = False
    note_ids: list = field(default_factory=list)
    templates: list = field(default_factory=list)
    max_per_day: int = 3


@dataclass
class AutoLikeConfig:
    """自动给白名单作者的笔记点赞（含历史笔记）。"""
    enabled: bool = False
    max_per_run: int = 10
    max_per_day: int = 10
    min_seconds_between: int = 3
    skip_already_liked: bool = True


@dataclass
class SafetyConfig:
    dry_run: bool = True
    request_interval: float = 1.5
    log_actions: bool = True


@dataclass
class CheckinConfig:
    """每日签到。连续签到满 3 天才会解锁「发私信」权限，建议常开。"""
    enabled: bool = True


@dataclass
class LetterConfig:
    """私信：读取好友来信并按规则回复。

    平台限制：账号需连续签到满 3 天才能发私信；
    未达门槛时只读取、不发送。
    """
    enabled: bool = True
    auto_reply: bool = True
    templates: dict = field(default_factory=dict)   # {"default": [...], "keyword_rules": [...]}
    max_replies_per_run: int = 3
    max_replies_per_day: int = 20
    min_seconds_between_actions: int = 60
    skip_if_contains: list = field(default_factory=lambda: ["举报", "投诉", "违法"])
    # 限制只回复白名单用户（owner_home_ids）的私信；关闭则回复所有互关好友
    owners_only: bool = True


@dataclass
class BotConfig:
    owner_home_ids: list = field(default_factory=list)
    owner_names: list = field(default_factory=list)
    scan: ScanConfig = field(default_factory=ScanConfig)
    reply: ReplyConfig = field(default_factory=ReplyConfig)
    top_comment: TopCommentConfig = field(default_factory=TopCommentConfig)
    auto_like: AutoLikeConfig = field(default_factory=AutoLikeConfig)
    checkin: CheckinConfig = field(default_factory=CheckinConfig)
    letter: LetterConfig = field(default_factory=LetterConfig)
    safety: SafetyConfig = field(default_factory=SafetyConfig)

    @classmethod
    def load(cls, path: Path = CONFIG_FILE) -> "BotConfig":
        raw = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        cfg = cls(
            owner_home_ids=[str(x) for x in raw.get("owner_home_ids", [])],
            owner_names=raw.get("owner_names", []),
        )
        for name, holder in (("scan", cfg.scan), ("reply", cfg.reply),
                             ("top_comment", cfg.top_comment),
                             ("auto_like", cfg.auto_like), ("checkin", cfg.checkin),
                             ("letter", cfg.letter), ("safety", cfg.safety)):
            for k, v in (raw.get(name) or {}).items():
                if hasattr(holder, k):
                    setattr(holder, k, v)
        return cfg
