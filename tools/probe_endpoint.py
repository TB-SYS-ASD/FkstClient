#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""靠服务端的「缺参提示」把 fkst 接口的参数试出来。

原理
----
`api.yaerxing.com` 的参数校验非常啰嗦，缺什么就直说：

    POST /STCoin2Note            → {"res":1,"error":"count field missing"}
    POST /STCoin2Note count=1    → {"res":1,"error":"nid field missing"}
    POST /STCoin2Note count=1 nid=1 → {"res":0}

所以只要不停地把「服务端说的那个字段」补上，就能把必需参数一个个问出来，
根本不需要脱壳看代码。

用法
----
    # 交互式地把一个接口的必需参数问出来（最多 8 轮）
    python tools/probe_endpoint.py STCoin2Note

    # 先猜一批参数名（逗号分隔），再自动补缺
    python tools/probe_endpoint.py STCoin2Note --seed nid,count

    # 指定签名变体（default / comment）
    python tools/probe_endpoint.py SendSTLetterMessage --sign comment --seed type,my_mid,other_mid,content

注意：本脚本**只做读探测**。像投币、发私信这种会真的产生副作用的接口，
参数补齐后请自行确认再调用，别拿主号乱试。
"""

import argparse
import re
import sys
import time
import urllib.parse
import urllib.request
import gzip
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from fkst_sdk.client import FkstClient                      # noqa: E402
from fkst_sdk.constants import DEVICE_PARAMS, DEFAULT_DYNAMIC_PARAMS  # noqa: E402
from fkst_sdk.signer import SIGNERS                        # noqa: E402

MISSING_RE = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)\s+field\s+missing")


def call(client: FkstClient, path: str, params: dict, sign: str = "default") -> str:
    base = dict(DEVICE_PARAMS)
    base.update(DEFAULT_DYNAMIC_PARAMS)
    base.update({k: v for k, v in client.dynamic_params.items() if v})
    base.update({k: v for k, v in params.items() if v is not None})
    base["call_id"] = str(int(time.time() * 1000))
    base["api_sig"] = SIGNERS[sign](base)

    body = urllib.parse.urlencode(base).encode()
    req = urllib.request.Request(
        "https://api.yaerxing.com/" + path,
        data=body,
        headers={
            "User-Agent": "okhttp/4.9.0",
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept-Encoding": "gzip",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        raw = r.read()
        if r.headers.get("Content-Encoding") == "gzip":
            raw = gzip.decompress(raw)
    return raw.decode("utf-8", "replace").strip()


def main():
    ap = argparse.ArgumentParser(description="fkst 接口参数探测（缺参提示法）")
    ap.add_argument("endpoint", help="接口方法名，例如 STCoin2Note")
    ap.add_argument("--seed", default="", help="先猜的参数，逗号分隔，值留空示例：nid=1,count=1")
    ap.add_argument("--sign", default="default", choices=list(SIGNERS), help="签名变体")
    ap.add_argument("--session", default=str(ROOT / "user_session.json"))
    ap.add_argument("--rounds", type=int, default=8)
    ap.add_argument("--value", default="1", help="新学到的字段默认填什么值")
    args = ap.parse_args()

    client = FkstClient(session_file=args.session, min_interval=1.4)
    if client.load_session():
        print(f"已加载会话 mid={client.mid}")
    else:
        print("⚠️  没找到会话，将以游客身份（mid=1）探测")

    params: dict = {}
    for item in filter(None, (s.strip() for s in args.seed.split(","))):
        if "=" in item:
            k, v = item.split("=", 1)
            params[k] = v
        else:
            params[item] = args.value

    print(f"\n接口 {args.endpoint}（签名 {args.sign}）")
    for i in range(args.rounds):
        out = call(client, args.endpoint, params, args.sign)
        print(f"  第 {i + 1} 轮  params={params}")
        print(f"        → {out[:300]}")
        m = MISSING_RE.search(out)
        if not m:
            print("\n✅ 参数齐了（或至少服务端不再报缺参）。")
            break
        missing = m.group(1)
        params[missing] = args.value
        print(f"        + 补上 {missing}={args.value}")
        time.sleep(1.4)
    else:
        print("\n⚠️  轮次用完了，可能还有没问出来的字段，或参数名不在缺参提示里。")

    print("\n最终参数：", params)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
