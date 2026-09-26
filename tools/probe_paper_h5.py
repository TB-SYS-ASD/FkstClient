# -*- coding: utf-8 -*-
"""诊断 paperExercises H5 页面题干空白：对比直载 vs 首页 cookie bootstrap。

用法：python tools/probe_paper_h5.py
用桌面端 fkst_client 真实登录拿 loginToken/tokenSeed，再复现 Android 侧请求。
"""
import os
import re
import sys
import json
import urllib.parse

import requests

DESKTOP = r"C:\Users\TB-SYSTEM\Desktop\fkst-desktop-main"
sys.path.insert(0, DESKTOP)

from fkst_client.client import FkstClient  # noqa: E402
from fkst_client import letter  # noqa: E402

ENV_PATH = r"C:\Users\TB-SYSTEM\Desktop\fkst-client-main\.env"
OUT_DIR = r"C:\Users\TB-SYSTEM\Desktop\FkstClient\tools\probe_out"
UA_H5 = ("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
         "(KHTML, like Gecko) Chrome/120.0 Mobile wv")


def load_env(path):
    env = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    return env


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    env = load_env(ENV_PATH)
    phone = env.get("FKST_PHONE") or env.get("LOGIN_PHONE")
    pwd = env.get("FKST_PASSWORD") or env.get("LOGIN_PASSWORD")
    if not phone or not pwd:
        print("缺少账号密码")
        return 1

    c = FkstClient()
    c.login_by_password(phone, pwd)
    print("[login] mid=%s unionid=%s loginToken=%s... tokenSeed=%s" % (
        c._mid, (c._unionid or "")[:10],
        (c._login_token or "")[:16], (c._token_seed or "")[:8]))

    account = {
        "mid": str(c._mid),
        "unionid": c._unionid,
        "loginToken": c._login_token,
        "tokenSeed": c._token_seed,
    }

    # 拿一份试卷（八年级·英语 f_gradeid=19 → 版本列表 → 列表第一条）
    versions = []
    try:
        v = c._request("GetSTFilterData",
                       {"filter": 1, "subject": 4, "f_gradeid": 19})
        versions = v.get("version") or v.get("list") or []
        print("[versions] %s" % [str(x)[:60] for x in versions[:3]])
    except Exception as exc:
        print("[versions] 取失败: %s" % exc)
    params = {"page": 0, "f_gradeid": 19}
    if versions:
        first = versions[0]
        vid = first.get("id") if isinstance(first, dict) else first
        if vid:
            params["version_id"] = vid
    r = c._request("GetShuatiPaper5", params)
    papers = r.get("papers") or []
    if not papers:
        print("[papers] 空")
        return 1
    target = None
    for p in papers:
        if "Unit 8" in str(p.get("title", "")) or "Unit8" in str(p.get("title", "")):
            target = p
            break
    target = target or papers[0]
    print("[paper] id=%s type=%s title=%s" % (
        target.get("id"), target.get("type"), target.get("title")))

    url = letter.build_paper_exercise_url(
        account, target.get("id"), target.get("type"))
    print("[url] %s" % url[:160])

    # A：直载（无 cookie bootstrap）
    s1 = requests.Session()
    s1.headers.update({"User-Agent": UA_H5})
    a = s1.get(url, timeout=30)
    print("\n[A 直载] status=%s bytes=%d cookies=%s" % (
        a.status_code, len(a.content), list(s1.cookies.keys())))

    # B：先访首页种会话 cookie，再载目标
    s2 = requests.Session()
    s2.headers.update({"User-Agent": UA_H5})
    home = s2.get("https://www.yaerxing.com/", timeout=30)
    print("[B 首页] status=%s cookies=%s" % (
        home.status_code, list(s2.cookies.keys())))
    b = s2.get(url, timeout=30)
    print("[B 目标] status=%s bytes=%d cookies=%s" % (
        b.status_code, len(b.content), list(s2.cookies.keys())))

    open(os.path.join(OUT_DIR, "A_direct.html"), "wb").write(a.content)
    open(os.path.join(OUT_DIR, "B_bootstrap.html"), "wb").write(b.content)

    html = b.text
    print("\n--- B 页面特征 ---")
    for kw in ["localDecrypt", "initData", "ShuatiMathod", "onLocalReady",
               "androidMessage", "获取题目", "加载中", "登录", "encrypt",
               "_question", "ajax", "XMLHttpRequest", "fetch("]:
        print("  %-16s x%d" % (kw, html.count(kw)))

    # 找出页面里的 ajax 端点
    eps = set(re.findall(r'["\'](/[A-Za-z0-9_\-/]*(?:api|json|php|do|action)[A-Za-z0-9_\-/]*)["\']', html))
    if eps:
        print("\n  可能的接口路径:", sorted(eps)[:20])

    # 抽 script src
    srcs = re.findall(r'<script[^>]+src=["\']([^"\']+)["\']', html)
    print("\n  script:", srcs[:15])
    return 0


if __name__ == "__main__":
    sys.exit(main())
