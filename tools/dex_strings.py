#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从（加固）APK 里挖接口名。

背景
----
`com.yaerxing.fkst` 官方 APK 用**支付宝 ashield 加固**：
`classes.dex` 只是一层壳（`com.ashield.Stub`，`class_defs_size = 1`），
真正的代码被加密后塞在同一个文件后面。所以 jadx / apktool 反编译出来
只有一个 Stub 类，看不到方法体。

但 dex 的**字符串池是明文的**——接口名、资源名、文案全都在。
所以「静态脱壳」虽然做不到，**挖接口名完全可行**。

用法
----
    python tools/dex_strings.py <apk 或 dex 路径> [--grep 关键词] [--dump all]

    python tools/dex_strings.py fkst_2.3.3.apk            # 列全部候选接口
    python tools/dex_strings.py fkst_2.3.3.apk --grep Coin  # 只看含 Coin 的串

拿到接口名之后，用 `tools/probe_endpoint.py` 靠服务端的「缺参提示」把参数试出来。
"""

import argparse
import io
import re
import sys
import zipfile
from pathlib import Path

# ---------------------------------------------------------------- dex 解析

def _read_uleb128(buf, pos):
    result = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
    return result, pos


def _u32(buf, off):
    return int.from_bytes(buf[off:off + 4], "little")


def dex_strings(raw: bytes):
    """读取一个标准 dex 的 string_ids 表，返回全部字符串。

    加固壳的「真」dex 读不到，但壳文件本身也是合法 dex（只有 1 个类），
    它的 string_ids 表通常只覆盖壳自身；真正有用的是**文件里散落的明文串**，
    所以这里同时提供 `raw_strings()`。
    """
    if raw[:4] != b"dex\n":
        return []
    size = _u32(raw, 56)            # string_ids_size
    off = _u32(raw, 60)             # string_ids_off
    out = []
    for i in range(size):
        data_off = _u32(raw, off + i * 4)
        try:
            ln, p = _read_uleb128(raw, data_off)
            s = raw[p:p + ln].decode("utf-8", "ignore")
            out.append(s)
        except Exception:
            continue
    return out


def raw_strings(raw: bytes, minlen: int = 5):
    """扫全文件的可打印 ASCII 串（不依赖 dex 结构，壳也能用）。"""
    out = []
    cur = bytearray()
    for c in raw:
        if 32 <= c < 127:
            cur.append(c)
        else:
            if len(cur) >= minlen:
                out.append(cur.decode("ascii"))
            cur = bytearray()
    if len(cur) >= minlen:
        out.append(cur.decode("ascii"))
    return out


# ---------------------------------------------------------------- 提取

ENDPOINT_RE = re.compile(r"^(?:[A-Z][A-Za-z0-9_]{2,45})$")


def looks_like_endpoint(s: str) -> bool:
    """fkst 的接口名特征：大驼峰 + 常见的 Get/Set/Add/Update/Del + ST 前缀段。"""
    if not ENDPOINT_RE.match(s):
        return False
    if "ST" in s:
        return True
    return s.startswith(
        ("Get", "Set", "Add", "Update", "Del", "Delete", "Upload", "Post", "Push",
         "Create", "Use", "Exchange", "Realize", "See", "Quit", "Pay")
    )


def iter_dex_blobs(path: Path):
    """yield (name, bytes)。支持 apk/zip（取里面的 .dex）或裸 dex 文件。"""
    if path.suffix.lower() in (".apk", ".zip", ".aab", ".xapk"):
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.endswith(".dex"):
                    yield n, z.read(n)
    else:
        yield path.name, path.read_bytes()


def main():
    ap = argparse.ArgumentParser(description="从加固 APK/dex 中提取接口名与字符串")
    ap.add_argument("target", help="apk / zip / dex 路径")
    ap.add_argument("--grep", help="只看包含该子串的字符串")
    ap.add_argument("--dump", choices=["endpoints", "all"], default="endpoints")
    ap.add_argument("--minlen", type=int, default=5)
    args = ap.parse_args()

    p = Path(args.target)
    if not p.exists():
        print(f"找不到文件: {p}", file=sys.stderr)
        return 1

    for name, raw in iter_dex_blobs(p):
        print(f"==== {name}  {len(raw):,} bytes ====")
        try:
            hdr = dex_strings(raw)
            print(f"  dex 头部 string_ids: {len(hdr)} 条"
                  f"（1~121 条基本可以确定是加固壳）")
        except Exception:
            pass

        ss = raw_strings(raw, args.minlen)
        if args.grep:
            hits = sorted({s for s in ss if args.grep.lower() in s.lower()})
            for s in hits:
                print("  ", s)
            print(f"  共 {len(hits)} 条命中")
            continue

        if args.dump == "all":
            for s in ss:
                print("  ", s)
            continue

        eps = sorted({s for s in ss if looks_like_endpoint(s)})
        for s in eps:
            print("  ", s)
        print(f"  候选接口 {len(eps)} 个（全量 {len(ss)} 条串）")

        # OSS 上传脚本名单独特判（它们在 API 域下，但不是大驼峰）
        oss = sorted({s for s in ss if re.match(r"^OSS[A-Za-z0-9_]+\.php$", s)})
        if oss:
            print("  OSS 上传脚本:")
            for s in oss:
                print("    ", s)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
