"""探测：找出机器人关注的账号 + 该账号的笔记（只读）。"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fkst_sdk import FkstClient, FkstAPIError, services as S  # noqa: E402


def load_env():
    for line in Path(".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())


def head(o, n=500):
    s = json.dumps(o, ensure_ascii=False)
    return s[:n] + ("…" if len(s) > n else "")


def main():
    load_env()
    c = FkstClient(min_interval=1.5)
    c.login(os.environ["FKST_PHONE"], os.environ["FKST_PASSWORD"])
    print("bot mid:", c.mid)

    print("\n== 找关注列表 ==")
    for kw in [
        {"updated_at": "0"},
        {"updated_at": "0", "page": "0"},
    ]:
        for name in ("GET_LINKMAN_LIST",):
            try:
                r = c.request(name, **kw)
                print(f"{name} {kw} → {head(r)}")
            except FkstAPIError as e:
                print(f"{name} {kw} → {e}")

    for t in ["1", "2", "3", "0"]:
        try:
            r = c.request("GET_BUDDIES", type=t)
            print(f"GET_BUDDIES type={t} → {head(r, 700)}")
        except FkstAPIError as e:
            print(f"GET_BUDDIES type={t} → {e}")

    print("\n== 关注流里的作者 home_id ==")
    try:
        r = S.get_follow_notes(c)
        notes = r.get("notes") or []
        seen = {}
        for n in notes:
            seen.setdefault(str(n.get("home_id")), 0)
            seen[str(n.get("home_id"))] += 1
        print("作者分布:", seen)
        print("样例笔记:", head(notes[0], 400) if notes else None)
    except FkstAPIError as e:
        print("失败:", e)

    print("\n== 猜测作者 mid 并读取其笔记 ==")
    for home_id in list(seen.keys())[:3]:
        try:
            r = S.get_user_notes(c, home_id, "0", "0")
            nn = r.get("notes") or []
            print(f"home_id={home_id} 笔记数={len(nn)}")
            for n in nn[:6]:
                print("   -", n.get("id"), n.get("title"), "| created:", n.get("created_at"))
        except FkstAPIError as e:
            print(f"home_id={home_id} 失败: {e}")


if __name__ == "__main__":
    main()
