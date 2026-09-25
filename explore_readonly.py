"""只读探查脚本：登录后把机器人账号能看到的关键数据摸一遍（不写任何内容）。"""
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


def head(obj, n=400):
    s = json.dumps(obj, ensure_ascii=False)
    return s[:n] + ("…" if len(s) > n else "")


def main():
    load_env()
    c = FkstClient(debug=False, min_interval=1.5)

    print("== 1. 登录 ==")
    c.login(os.environ["FKST_PHONE"], os.environ["FKST_PASSWORD"])
    print("mid:", c.mid, "unionid:", c.dynamic_params["unionid"])

    print("\n== 2. 我的资料 (GetSTMyData5) ==")
    try:
        me = S.whoami(c)
        keys = list(me.keys())
        print("字段:", keys[:25])
        print("member:", head(me.get("member"), 300))
    except FkstAPIError as e:
        print("失败:", e)

    print("\n== 3. 我的主页 (GetSTUserData) ==")
    try:
        ud = S.get_user_data(c, c.mid)
        print(head(ud, 500))
    except FkstAPIError as e:
        print("失败:", e)

    print("\n== 4. 我关注的用户 (GetSTLinkmanList / GetSTBuddies) ==")
    for name in ("GET_LINKMAN_LIST", "GET_BUDDIES"):
        try:
            r = c.request(name)
            who = [x for x in r.keys() if x not in ("res",)]
            print(f"{name}: keys={who} {head(r, 300)}")
        except FkstAPIError as e:
            print(f"{name} 失败: {e}")

    print("\n== 5. 发现流（日常）==")
    notes = []
    try:
        r = S.get_discover_notes(c, type_="10", page="0")
        notes = r.get("notes") or []
        print("条数:", len(notes))
        for n in notes[:5]:
            print("   -", n.get("id"), n.get("title"), "| mid:", n.get("mid"), "| 评论数:", n.get("comment_count") or n.get("comments"))
        print("字段样例:", list(notes[0].keys()) if notes else None)
    except FkstAPIError as e:
        print("失败:", e)

    print("\n== 6. 我的笔记 (GetSTUserNotes2) ==")
    my_notes = []
    try:
        r = S.get_my_notes(c)
        my_notes = r.get("notes") or []
        print("条数:", len(my_notes))
        for n in my_notes[:5]:
            print("   -", n.get("id"), n.get("title"))
    except FkstAPIError as e:
        print("失败:", e)

    print("\n== 7. 关注流 (GetFollowUserNotes2) ==")
    try:
        r = S.get_follow_notes(c)
        nn = r.get("notes") or []
        print("条数:", len(nn))
        for n in nn[:5]:
            print("   -", n.get("id"), n.get("title"), "| 作者 mid:", n.get("mid"))
    except FkstAPIError as e:
        print("失败:", e)

    print("\n== 8. 文章详情 + 正文解密 ==")
    target = my_notes[0] if my_notes else (notes[0] if notes else None)
    if target:
        d = S.get_note_detail(c, target["id"])
        print("html 长度:", len(d["html"]), "密文长度:", len(d["cipher"]))
        print("正文预览:", d["text"][:200].replace("\n", " "))
    else:
        print("没有可用的文章 ID")

    print("\n== 9. 评论列表 ==")
    if target:
        try:
            cs = S.get_comments(c, target["id"], ct="10")
            print("顶层评论:", len(cs))
            for cm in cs[:5]:
                print("   -", cm.get("id"), cm.get("fid"), (cm.get("content") or "")[:40],
                      "| 作者:", cm.get("nick_name") or cm.get("mid"))
            if cs:
                print("评论字段:", list(cs[0].keys()))
                try:
                    rp = S.get_replies(c, cs[0].get("id"))
                    print("首条评论的回复数:", len(rp))
                except FkstAPIError as e:
                    print("回复失败:", e)
        except FkstAPIError as e:
            print("失败:", e)

    print("\n== 10. 通知 (GetSTNotices2) ==")
    try:
        r = S.get_notices(c, type_="2")
        nn = r.get("notices") or []
        print("条数:", len(nn), "| 字段:", list(nn[0].keys()) if nn else None)
        for n in nn[:5]:
            print("   -", head(n, 200))
    except FkstAPIError as e:
        print("失败:", e)


if __name__ == "__main__":
    main()
