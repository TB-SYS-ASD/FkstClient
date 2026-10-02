#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""勋章 / 硬币：只读探测工具（2026-10-02 实测）。

结论速览（详见 API_RESEARCH.md 第二十节）
----------------------------------------
1. `STMedalDetails` 参数是 `page` + `tab`，**`page` 从 0 开始**（`page=1` 恒返回空数组，
   这是此前一直探不到数据的原因）。返回是一本**勋章流水账**，不是可购买的勋章目录：

       {"over":true,"res":0,
        "details":[{"id":"18904185","type":"9","format_date":"2026/09/25 10:01",
                    "number":"1","type_event":"新注册获取"}]}

   - `type`     勋章/事件类型（观测到 9 = 新注册，101 = 兑换硬币）
   - `number`   数量
   - `type_event` 事件文案，直接可展示
   - `format_date` 形如 `2026/09/25 10:01`
   - `tab` 是分类，0~6 都接受（观测到 tab=1 是「获得」、tab=2 是「消耗/兑换」）

2. `ExchangeSTMedal` + `count` = **用 count 枚勋章兑换硬币**（实测报「勋章数量不足」，
   说明输入是勋章）。接口活着。

3. `ExchangeSTCoin` + `count` = 硬币兑换勋章，**服务端已下线**：

       {"res":2,"remind_hint":"硬币兑换勋章功能已全面升级，请APP升级最新版本了解详情"}

   不带任何参数也是这句话（不会先报缺参），说明服务端在校验参数前就短路了。
   实测换过 app_v(2.0.2/2.2.5/2.3.3/2.4.0/2.5.0/3.0.0) 与 app_c(171/188/201/220)
   共 10 种组合，返回完全一致 —— **不是版本号门槛，是真下线**。
   （签名模板只含 app_c：`f0{call_id后四位}com.yaerxing.fkst{app_c}F.K*$t`，
   所以改 app_v 本来也不影响 api_sig。）

用法
----
    # 内存登录（不写任何会话文件），扫 tab 1 的流水
    FKST_PHONE=1xxxxxxxxxx FKST_PASSWORD=xxxxxx python tools/probe_medal.py --tab 1

    # 扫全部 tab
    FKST_PHONE=... FKST_PASSWORD=... python tools/probe_medal.py --all

安全
----
本脚本**只读**。兑换类接口会真实消耗硬币/勋章，参数与比例未验证前不要在真账号上跑。
"""
import argparse
import gzip
import os
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from fkst_sdk.client import FkstClient                          # noqa: E402
from fkst_sdk.constants import DEVICE_PARAMS, DEFAULT_DYNAMIC_PARAMS  # noqa: E402
from fkst_sdk.signer import SIGNERS                             # noqa: E402

_client = FkstClient(session_file=str(ROOT / "__no_session__.json"), min_interval=1.3)


def call(path: str, params: dict, sign: str = "default") -> str:
    base = dict(DEVICE_PARAMS)
    base.update(DEFAULT_DYNAMIC_PARAMS)
    base.update({k: v for k, v in _client.dynamic_params.items() if v})
    base.update({k: v for k, v in params.items() if v is not None})
    base["call_id"] = str(int(time.time() * 1000))
    base["api_sig"] = SIGNERS[sign](base)
    req = urllib.request.Request(
        "https://api.yaerxing.com/" + path,
        data=urllib.parse.urlencode(base).encode(),
        headers={"User-Agent": "okhttp/4.9.0",
                 "Content-Type": "application/x-www-form-urlencoded",
                 "Accept-Encoding": "gzip"},
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        raw = r.read()
        if r.headers.get("Content-Encoding") == "gzip":
            raw = gzip.decompress(raw)
    return raw.decode("utf-8", "replace").strip()


def scan_tab(tab: str, pages: int = 2) -> None:
    print(f"\n--- STMedalDetails  tab={tab}")
    for p in range(pages):
        try:
            out = call("STMedalDetails", {"page": str(p), "tab": tab})
        except Exception as e:                                    # noqa: BLE001
            print(f"    page={p} → EXC {e}")
            return
        print(f"    page={p} → {out[:500]}")
        if '"over":true' in out.replace(" ", ""):
            break
        time.sleep(1.3)


def main() -> int:
    ap = argparse.ArgumentParser(description="勋章 / 硬币只读探测")
    ap.add_argument("--tab", default=None, help="只扫这个 tab（默认 0~6 全扫）")
    ap.add_argument("--all", action="store_true", help="扫 tab 0~6")
    ap.add_argument("--pages", type=int, default=2, help="每个 tab 翻几页")
    args = ap.parse_args()

    phone, pwd = os.getenv("FKST_PHONE"), os.getenv("FKST_PASSWORD")
    if not phone or not pwd:
        print("需要 FKST_PHONE / FKST_PASSWORD 环境变量")
        return 1

    # 内存登录：save=False，不落盘
    _client.login(phone, pwd, save=False)
    print(f"已登录 mid={_client.mid}（会话未写盘）")

    print("\n=== 硬币余额（GetSTMyData5）===")
    try:
        out = call("GetSTMyData5", {"all_black_member": "1"})
        import json
        data = json.loads(out)
        print(f"    coin_count = {data.get('coin_count')}"
              f"   get_coin_day = {data.get('get_coin_day')}"
              f"   get_coin_status = {data.get('get_coin_status')}")
    except Exception as e:                                        # noqa: BLE001
        print(f"    EXC {e}")

    tabs = [args.tab] if args.tab is not None else [str(i) for i in range(7)]
    print(f"\n=== 勋章流水（page 从 0 开始！）tabs={tabs} ===")
    for t in tabs:
        scan_tab(t, args.pages)

    print("\n=== 兑换接口状态 ===")
    for name, p, note in (
        ("ExchangeSTMedal", {"count": "999999"}, "勋章→硬币（输入勋章；填非法值安全）"),
        ("ExchangeSTCoin", {}, "硬币→勋章（已下线）"),
    ):
        try:
            out = call(name, p)
        except Exception as e:                                    # noqa: BLE001
            out = f"EXC {e}"
        print(f"    {name:<18} {note}\n        → {out[:250]}")
        time.sleep(1.3)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
