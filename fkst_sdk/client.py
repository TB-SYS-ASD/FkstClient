"""HTTP 客户端：参数组装、签名、限频、重试、会话持久化。"""

import json
import random
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

from .constants import (
    API_BASE, DEVICE_PARAMS, DEFAULT_DYNAMIC_PARAMS, API_KEY, PWD_SALT,
    GET_NOTE_PARAMS, ENDPOINTS,
)
from .signer import SIGNERS, encrypt_password


class FkstAPIError(Exception):
    """服务端返回 res != 0。"""

    def __init__(self, endpoint: str, payload: dict):
        self.endpoint = endpoint
        self.payload = payload or {}
        self.res = self.payload.get("res")
        msg = self.payload.get("error") or self.payload.get("remind_hint") or self.payload
        super().__init__(f"[{endpoint}] res={self.res} {msg}")


class RateLimiter:
    """最小请求间隔 + 抖动，避免触发风控。"""

    def __init__(self, min_interval: float = 1.2, jitter: float = 0.6):
        self.min_interval = min_interval
        self.jitter = jitter
        self._last = 0.0
        self.count = 0

    def wait(self):
        gap = time.time() - self._last
        need = self.min_interval + random.uniform(0, self.jitter)
        if gap < need:
            time.sleep(need - gap)
        self._last = time.time()
        self.count += 1


def _multipart_body(fields: dict, file_field: str, filename: str,
                    mime: str, data: bytes, boundary: str) -> bytes:
    """拼 multipart/form-data 请求体（普通字段 + 一个文件字段）。"""
    out = bytearray()
    for k, v in fields.items():
        out += (
            f'--{boundary}\r\n'
            f'Content-Disposition: form-data; name="{k}"\r\n\r\n'
            f'{v}\r\n'
        ).encode("utf-8")
    out += (
        f'--{boundary}\r\n'
        f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'
        f'Content-Type: {mime}\r\n\r\n'
    ).encode("utf-8")
    out += data
    out += f"\r\n--{boundary}--\r\n".encode("utf-8")
    return bytes(out)


class FkstClient:
    def __init__(
        self,
        session_file: str = "user_session.json",
        min_interval: float = 1.2,
        timeout: int = 20,
        debug: bool = False,
        device_params: dict | None = None,
    ):
        self.base_url = API_BASE
        self.timeout = timeout
        self.debug = debug
        self.device_params = dict(DEVICE_PARAMS)
        if device_params:
            self.device_params.update(device_params)
        self.dynamic_params = dict(DEFAULT_DYNAMIC_PARAMS)
        self.session_file = Path(session_file)
        self.limiter = RateLimiter(min_interval)
        self.mid = None

    # ------------------------------------------------------------------ 参数
    def build_params(self, endpoint_name: str, **kwargs) -> dict:
        ep = ENDPOINTS.get(endpoint_name)
        if ep is None:
            raise ValueError(f"未定义的端点: {endpoint_name}")

        params = dict(ep.get("default_params", {}))
        missing = [
            p for p in ep.get("required_params", [])
            if p not in kwargs and p not in params
        ]
        if missing:
            raise ValueError(f"{endpoint_name} 缺少必需参数: {missing}")

        if not ep.get("skip_device"):
            params.update(self.device_params)
        dyn = dict(self.dynamic_params)
        for k in ep.get("exclude_dynamic", []):
            dyn.pop(k, None)
        params.update(dyn)

        if endpoint_name != "GET_NOTE":
            params["call_id"] = str(int(time.time() * 1000))
        params.update(kwargs)
        return params

    def _sign(self, endpoint_name: str, params: dict) -> dict:
        ep = ENDPOINTS[endpoint_name]
        kind = ep.get("sign", "default")
        params = dict(params)
        params["api_sig"] = SIGNERS[kind](params)
        return params

    # ------------------------------------------------------------------ 请求
    def request(self, endpoint_name: str, expect_res: bool = True, **kwargs) -> dict:
        ep = ENDPOINTS[endpoint_name]
        params = self.build_params(endpoint_name, **kwargs)
        params = self._sign(endpoint_name, params)
        url = ep.get("base_url", self.base_url) + ep["path"]
        method = ep.get("method", "POST").upper()

        last_err = None
        for attempt in range(3):
            self.limiter.wait()
            try:
                text = self._send(method, url, params)
                break
            except urllib.error.URLError as e:      # 网络类错误才重试
                last_err = e
                time.sleep(2 * (attempt + 1))
        else:
            raise FkstAPIError(endpoint_name, {"error": f"网络失败: {last_err}"})

        if self.debug:
            print(f"[{endpoint_name}] {method} {url}\n  → {text[:300]}")

        if endpoint_name == "GET_NOTE":
            return {"res": 0, "data": {"html": text, "content": self._extract_cipher(text)}}

        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            raise FkstAPIError(endpoint_name, {"error": f"非 JSON 响应: {text[:200]}"})

        if expect_res and payload.get("res") != 0:
            raise FkstAPIError(endpoint_name, payload)
        return payload

    def _send(self, method: str, url: str, params: dict) -> str:
        headers = {
            "User-Agent": "okhttp/4.9.0",
            "Accept-Encoding": "gzip",
        }
        if method == "GET":
            req = urllib.request.Request(url + "?" + urllib.parse.urlencode(params), headers=headers)
        else:
            body = urllib.parse.urlencode(params).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            req = urllib.request.Request(url, data=body, headers=headers)
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Encoding") == "gzip":
                import gzip
                raw = gzip.decompress(raw)
            return raw.decode("utf-8", "replace").strip()

    @staticmethod
    def _extract_cipher(html: str) -> str:
        import re
        m = re.search(r'<div[^>]*class="note-content[^>]*>([^<]+)</div>', html)
        return m.group(1) if m else ""

    # ------------------------------------------------------------------ 图片上传

    def upload_image(
        self,
        data: bytes,
        filename: str = "a.jpg",
        mime: str = "image/jpeg",
        dir_name: str = "stupnote",
    ) -> dict:
        """上传一张图片（multipart/form-data）。

        实测要点（2026-09-25）：
          - 路径参数叫 **dir_name**，不是 path/dir/folder（这些都会被当成空）
          - 文件字段名固定 **file**
          - 签名用的是通用签名，参数必须参与签名，所以要在签名后才拼 body
          - 成功 → {"res":0,"illegal":false,"url":"…/upimage/<dir>/…"}
            illegal=true 表示内容审核没过；res=2+「请开通会员」表示该目录要会员
        """
        ep = ENDPOINTS["UPLOAD_IMAGE"]
        params = self.build_params("UPLOAD_IMAGE", dir_name=dir_name)
        params["api_sig"] = SIGNERS[ep.get("sign", "default")](params)

        boundary = "----FkstBoundary" + uuid.uuid4().hex
        body = _multipart_body(params, "file", filename, mime, data, boundary)
        url = ep.get("base_url", self.base_url) + ep["path"]

        self.limiter.wait()
        req = urllib.request.Request(
            url,
            data=body,
            headers={
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "User-Agent": "okhttp/4.9.0",
            },
        )
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Encoding") == "gzip":
                import gzip
                raw = gzip.decompress(raw)
        return json.loads(raw.decode("utf-8", "replace").strip())

    def upload_file(
        self,
        data: bytes,
        filename: str = "a.pdf",
        mime: str = "application/octet-stream",
        dir_name: str = "stupnotefile",
    ) -> dict:
        """上传一个普通文件（multipart/form-data）。

        实测要点（2026-09-25，`OSSUploadFile2.php`）：
          - 参数与图片上传完全一致：`dir_name` + 文件字段名 `file`，通用签名
          - 成功 → {"res":0,"url":"http://imgcdn.yaerxing.com/upfile/<dir>/…","md5":"…"}
          - {"res":1,"error":"不允许的文件类型!"} → 扩展名被挡（zip 不行）
          - {"res":1,"error":"非法路径"} → 该目录不收文件（stupletter 只收图）
        """
        ep = ENDPOINTS["UPLOAD_FILE"]
        params = self.build_params("UPLOAD_FILE", dir_name=dir_name)
        params["api_sig"] = SIGNERS[ep.get("sign", "default")](params)

        boundary = "----FkstBoundary" + uuid.uuid4().hex
        body = _multipart_body(params, "file", filename, mime, data, boundary)
        url = ep.get("base_url", self.base_url) + ep["path"]

        self.limiter.wait()
        req = urllib.request.Request(
            url,
            data=body,
            headers={
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "User-Agent": "okhttp/4.9.0",
            },
        )
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Encoding") == "gzip":
                import gzip
                raw = gzip.decompress(raw)
        return json.loads(raw.decode("utf-8", "replace").strip())

    def upload_audio(
        self,
        data: bytes,
        filename: str = "voice.mp3",
        mime: str = "audio/mpeg",
    ) -> dict:
        """上传一段音频（multipart/form-data）。

        实测要点（2026-09-25，`OSSUploadAudio2.php`）：
          - 通用签名 + 文件字段名 `file`，**不需要 `dir_name`**（传了会被忽略）
          - **只收 `.mp3`**，而且服务端按**扩展名**判断（内容是 wav、名字写成 .mp3 照样收）
          - 成功 → {"res":0,"url":"http://imgcdn.yaerxing.com/audio/2026/09/25/<随机>.mp3"}
          - 其它扩展名 → {"res":1,"error":"upload audio failed"}

        注意：笔记正文**没有音频字段**（扫过 363 篇社区笔记，零音频痕迹），
        所以音频只能在正文里以 `[音频] <url>` 的形式表达。
        """
        ep = ENDPOINTS["UPLOAD_AUDIO"]
        params = self.build_params("UPLOAD_AUDIO")
        params["api_sig"] = SIGNERS[ep.get("sign", "default")](params)

        boundary = "----FkstBoundary" + uuid.uuid4().hex
        body = _multipart_body(params, "file", filename, mime, data, boundary)
        url = ep.get("base_url", self.base_url) + ep["path"]

        self.limiter.wait()
        req = urllib.request.Request(
            url,
            data=body,
            headers={
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "User-Agent": "okhttp/4.9.0",
            },
        )
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Encoding") == "gzip":
                import gzip
                raw = gzip.decompress(raw)
        return json.loads(raw.decode("utf-8", "replace").strip())
    # ------------------------------------------------------------------ 会话

    def update_dynamic(self, **kwargs):
        for k, v in kwargs.items():
            if v:
                self.dynamic_params[k] = str(v)
        if self.dynamic_params.get("mid"):
            self.mid = self.dynamic_params["mid"]

    def save_session(self) -> bool:
        data = {k: self.dynamic_params.get(k, "")
                for k in ("unionid", "openid", "mid", "device_token")}
        self.session_file.write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return True

    def load_session(self) -> bool:
        if not self.session_file.exists():
            return False
        try:
            data = json.loads(self.session_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return False
        if not all(data.get(k) for k in ("unionid", "openid", "mid")):
            return False
        self.update_dynamic(**data)
        return True

    def clear_session(self):
        self.dynamic_params = dict(DEFAULT_DYNAMIC_PARAMS)
        self.mid = None
        if self.session_file.exists():
            self.session_file.unlink()

    # ------------------------------------------------------------------ 登录
    def login(self, phone: str, password: str, save: bool = True) -> dict:
        payload = self.request(
            "LOGIN",
            phone_number=phone,
            password=encrypt_password(password, PWD_SALT),
            verify_type="1",
        )
        self.update_dynamic(
            unionid=payload.get("unionid"),
            openid=payload.get("openid"),
            mid=payload.get("mid"),
            device_token=payload.get("device_token"),
        )
        if save:
            self.save_session()
        return payload

    def login_with_env(self, save: bool = True) -> dict:
        """从环境变量 / .env 读取账号登录。"""
        import os
        phone = os.getenv("FKST_PHONE") or os.getenv("LOGIN_PHONE")
        pwd = os.getenv("FKST_PASSWORD") or os.getenv("LOGIN_PASSWORD")
        if not phone or not pwd:
            raise RuntimeError("未设置 FKST_PHONE / FKST_PASSWORD")
        return self.login(phone, pwd, save=save)

    def ensure_login(self, verify: bool = True) -> bool:
        """优先复用会话，失败则用环境变量登录。"""
        if self.load_session() and verify:
            from .services import get_user_data
            try:
                get_user_data(self, self.mid)
                return True
            except FkstAPIError:
                self.clear_session()
        if self.session_file.exists() or self.dynamic_params.get("unionid") != "guest":
            self.clear_session()
        try:
            self.login_with_env()
            return True
        except Exception:
            return False
