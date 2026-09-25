"""签名算法（三种变体），与官方 APP 一致。"""

import hashlib

from .constants import API_KEY, SIGN_SECRET, GET_NOTE_PARAMS

# 官方模板串： f0{call_id 后四位}com.yaerxing.fkst{app_c}F.K*$t
_TEXT_TPL = "f0{cid}com.yaerxing.fkst{app_c}F.K*$t"
# H5 文章详情模板串：f0{timestamp 后四位}{app_v}F.K*$t
_TEXT_TPL_NOTE = "f0{ts}{app_v}F.K*$t"


def _md5(s: str) -> str:
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def _stamp(params: dict, key: str, tpl: str, **kw) -> str:
    """结果串 = 字典序拼接 KV + SECRET + md5(模板)[5:21]"""
    body = "".join(
        f"{k}{v}" for k, v in sorted(params.items()) if k != "api_sig"
    ) + SIGN_SECRET
    suffix = str(params[key])[-4:]
    return body + _md5(tpl.format(cid=suffix, ts=suffix, **kw))[5:21]


def sign_params(params: dict, app_c: str = "171") -> str:
    """通用签名。"""
    return _md5(_stamp(params, "call_id", _TEXT_TPL, app_c=app_c)).upper()


def sign_note(params: dict) -> str:
    """GET_NOTE 专用：用 timestamp 后四位，不含 call_id。"""
    return _md5(
        _stamp(params, "timestamp", _TEXT_TPL_NOTE, app_v=GET_NOTE_PARAMS["app_v"])
    ).upper()


def sign_comment(params: dict) -> str:
    """发评论专用：只签 api_key + call_id + openid 三个字段。"""
    for k in ("call_id", "api_key", "openid"):
        if k not in params:
            raise ValueError(f"评论签名缺少参数: {k}")
    body = (
        f"api_key{params['api_key']}call_id{params['call_id']}openid{params['openid']}"
        + SIGN_SECRET
    )
    body += _md5(
        _TEXT_TPL.format(cid=str(params["call_id"])[-4:], app_c=params.get("app_c", "171"))
    )[5:21]
    return _md5(body).upper()


def encrypt_password(password: str, salt: str) -> str:
    """登录密码：md5(明文 + 盐)。"""
    return _md5(password + salt)


SIGNERS = {
    "default": sign_params,
    "note": sign_note,
    "comment": sign_comment,
}
