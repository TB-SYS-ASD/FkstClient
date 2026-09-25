"""运行状态：已处理评论、当日动作计数。"""

import json
import time
from datetime import date
from pathlib import Path


class BotState:
    def __init__(self, path: Path):
        self.path = Path(path)
        self.data = {"replied_comments": [], "top_commented_notes": [],
                     "liked_notes": [],
                     "replied_letters": [],
                     "day": "", "day_replies": 0, "day_comments": 0, "day_likes": 0,
                     "day_letter_replies": 0, "checked_in_day": "",
                     "last_action_at": 0}
        if self.path.exists():
            try:
                self.data.update(json.loads(self.path.read_text(encoding="utf-8")))
            except (json.JSONDecodeError, OSError):
                pass
        self._rollover()

    def _rollover(self):
        today = date.today().isoformat()
        if self.data.get("day") != today:
            self.data["day"] = today
            self.data["day_replies"] = 0
            self.data["day_comments"] = 0
            self.data["day_likes"] = 0
            self.data["day_letter_replies"] = 0

    # ---------------------------------------------------------------- 查询
    @property
    def day_replies(self) -> int:
        self._rollover()
        return self.data["day_replies"]

    @property
    def day_comments(self) -> int:
        self._rollover()
        return self.data["day_comments"]

    def already_replied(self, comment_id) -> bool:
        return str(comment_id) in self.data["replied_comments"]

    def already_commented(self, note_id) -> bool:
        return str(note_id) in self.data["top_commented_notes"]

    def seconds_since_action(self) -> float:
        return time.time() - float(self.data.get("last_action_at") or 0)

    # ---------------------------------------------------------------- 时间水位
    @property
    def since(self) -> int:
        """只处理这个时间戳之后的评论。首次运行时写入当前时间。"""
        if not self.data.get("since"):
            self.data["since"] = int(time.time())
            self.save()
        return int(self.data["since"])

    def set_since(self, ts: int):
        self.data["since"] = int(ts)
        self.save()

    # ---------------------------------------------------------------- 记录
    def mark_replied(self, comment_id):
        cid = str(comment_id)
        if cid not in self.data["replied_comments"]:
            self.data["replied_comments"].append(cid)
        self.data["replied_comments"] = self.data["replied_comments"][-2000:]
        self._rollover()
        self.data["day_replies"] = self.day_replies + 1
        self.data["last_action_at"] = time.time()
        self.save()

    def mark_commented(self, note_id):
        nid = str(note_id)
        if nid not in self.data["top_commented_notes"]:
            self.data["top_commented_notes"].append(nid)
        self._rollover()
        self.data["day_comments"] = self.day_comments + 1
        self.data["last_action_at"] = time.time()
        self.save()

    # ---------------------------------------------------------------- 点赞
    @property
    def day_likes(self) -> int:
        self._rollover()
        return self.data["day_likes"]

    def already_liked(self, note_id) -> bool:
        return str(note_id) in self.data["liked_notes"]

    def mark_liked(self, note_id):
        nid = str(note_id)
        if nid not in self.data["liked_notes"]:
            self.data["liked_notes"].append(nid)
        self.data["liked_notes"] = self.data["liked_notes"][-2000:]
        self._rollover()
        self.data["day_likes"] = self.day_likes + 1
        self.data["last_action_at"] = time.time()
        self.save()

    def save(self):
        self.path.write_text(json.dumps(self.data, ensure_ascii=False, indent=2),
                             encoding="utf-8")


# ---------------------------------------------------------------- 签到
    @property
    def checked_in_today(self) -> bool:
        return self.data.get("checked_in_day") == date.today().isoformat()

    def mark_checked_in(self):
        self.data["checked_in_day"] = date.today().isoformat()
        self.data["last_action_at"] = time.time()
        self.save()


# ---------------------------------------------------------------- 私信
    @property
    def day_letter_replies(self) -> int:
        self._rollover()
        return self.data["day_letter_replies"]

    def already_replied_letter(self, letter_id) -> bool:
        return str(letter_id) in self.data["replied_letters"]

    def mark_replied_letter(self, letter_id):
        lid = str(letter_id)
        if lid not in self.data["replied_letters"]:
            self.data["replied_letters"].append(lid)
        self.data["replied_letters"] = self.data["replied_letters"][-2000:]
        self._rollover()
        self.data["day_letter_replies"] = self.day_letter_replies + 1
        self.data["last_action_at"] = time.time()
        self.save()
