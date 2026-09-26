#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""直接按**接口名**打一次 fkst 接口，用来试参数和看返回结构。

跟 `probe_endpoint.py` 的区别：那个是靠「缺参提示」自动把参数问出来，
这个是**手动**指定路径与参数，适合已经有线索、只想看一眼返回内容的场景
（试卷库那几个接口就是这么试出来的）。

用法：
    python tools/_probe_paper.py GetShuatiPaper5
    python tools/_probe_paper.py GetShuatiPaper5 page=0 f_gradeid=7
    python tools/_probe_paper.py GetSearchPapers7 keyword=数学 page=0 ct=20 --sign comment

登录态：优先读当前目录下的 `user_session.json`（`fkst_sdk` 登录后生成的），
没读到就按游客身份发 —— 部分接口游客也能调。
想指定别的位置就设环境变量 `FKST_SESSION_FILE`。

⚠️ 本脚本**不判断副作用**，收藏 / 发布这类会改数据的接口自己心里有数。
"""
import gzip
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from fkst_sdk.constants import DEVICE_PARAMS, DEFAULT_DYNAMIC_PARAMS  # noqa: E402
from fkst_sdk.signer import SIGNERS                                   # noqa: E402

SESSION = Path(
    os.environ.get("FKST_SESSION_FILE")
    or Path.cwd() / "user_session.json"
)


def call(path: str, params: dict, sign: str = "default", dyn: dict | None = None,
         timeout: int = 20) -> str:
    base = dict(DEVICE_PARAMS)
    base.update(DEFAULT_DYNAMIC_PARAMS)
    if SESSION.exists():
        base.update({k: v for k, v in json.loads(SESSION.read_text(encoding="utf-8")).items() if v})
    if dyn:
        base.update(dyn)
    base.update({k: v for k, v in params.items() if v is not None})
    base["call_id"] = str(int(time.time() * 1000))
    base["api_sig"] = SIGNERS[sign](base)
    body = urllib.parse.urlencode(base).encode()
    req = urllib.request.Request(
        "https://api.yaerxing.com/" + path, data=body,
        headers={"User-Agent": "okhttp/4.9.0",
                 "Content-Type": "application/x-www-form-urlencoded",
                 "Accept-Encoding": "gzip"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        if resp.headers.get("Content-Encoding") == "gzip":
            raw = gzip.decompress(raw)
        return raw.decode("utf-8", "replace").strip()


def main():
    argv = sys.argv[1:]
    if not argv:
        print("用法: python tools/_probe_paper.py <path> [k=v ...] [--sign comment]")
        return
    path = argv[0]
    sign = "default"
    params: dict[str, str] = {}
    for a in argv[1:]:
        if a == "--sign":
            continue
        if "=" in a:
            k, v = a.split("=", 1)
            params[k] = v
    if "--sign" in argv:
        sign = argv[argv.index("--sign") + 1]

    text = call(path, params, sign)
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        print(text[:800])
        return
    s = json.dumps(payload, ensure_ascii=False)
    print(s[:3000] + (" ...(截断)" if len(s) > 3000 else ""))


if __name__ == "__main__":
    main()
