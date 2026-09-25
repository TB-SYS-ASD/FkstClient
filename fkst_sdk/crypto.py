"""文章正文解密（H5 密文）。"""

import base64
import hashlib
import urllib.parse

from .constants import API_KEY


def make_key(secret_key) -> str:
    return hashlib.md5((str(secret_key) + API_KEY).encode("utf-8")).hexdigest()[8:16].lower()


def decrypt_content(cipher: str, secret_key) -> str:
    """base64 → XOR → base64 → urldecode"""
    if not cipher:
        return ""
    key = make_key(secret_key).encode("utf-8")
    step1 = base64.b64decode(cipher)
    xor = bytes(b ^ key[i % len(key)] for i, b in enumerate(step1))
    step3 = base64.b64decode(xor)
    return urllib.parse.unquote(step3.decode("utf-8", "replace"))


def strip_tags(html: str) -> str:
    """粗略去标签，便于文本匹配 / 通知。"""
    import re
    text = re.sub(r"<(script|style)[^>]*>.*?</\1>", "", html or "", flags=re.S | re.I)
    text = re.sub(r"<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>", r" [图片] ", text, flags=re.I)
    text = re.sub(r"<br\s*/?>|</p>", "\n", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = urllib.parse.unquote(text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()
