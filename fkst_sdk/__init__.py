"""
fkst_sdk —— 疯狂刷题（com.yaerxing.fkst）API 轻量 SDK

只依赖标准库（urllib），可直接在无 requests 的环境运行。
协议细节见项目根目录 API_RESEARCH.md。
"""

from .constants import (
    API_BASE, WEB_BASE, API_KEY, SIGN_SECRET, PWD_SALT,
    DEVICE_PARAMS, DEFAULT_DYNAMIC_PARAMS, GET_NOTE_PARAMS, ErrorCode,
)
from .client import FkstClient, FkstAPIError, RateLimiter
from .services import (
    login, whoami, get_user_data, get_my_notes, get_user_notes,
    get_discover_notes, get_follow_notes, get_note_detail,
    get_comments, get_replies, post_comment, delete_comment,
    like_note_comment, get_notices, search_notes,
    like_note, get_liked_notes,
    get_buddies, get_linkman_list, get_letters, send_letter, delete_letters,
    can_dm, get_coin_state, check_in,
)

__all__ = [
    "API_BASE", "WEB_BASE", "API_KEY", "SIGN_SECRET", "PWD_SALT",
    "DEVICE_PARAMS", "DEFAULT_DYNAMIC_PARAMS", "GET_NOTE_PARAMS", "ErrorCode",
    "FkstClient", "FkstAPIError", "RateLimiter",
    "login", "whoami", "get_user_data", "get_my_notes", "get_user_notes",
    "get_discover_notes", "get_follow_notes", "get_note_detail",
    "get_comments", "get_replies", "post_comment", "delete_comment",
    "like_note_comment", "get_notices", "search_notes",
    "like_note", "get_liked_notes",
    "get_buddies", "get_linkman_list", "get_letters", "send_letter", "delete_letters",
    "can_dm", "get_coin_state", "check_in",
]
