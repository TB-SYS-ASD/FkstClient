"""机器人入口。

用法：
    python -m bot.main --dry-run --once      # 演练一次：只打印将要发送的内容，不发送
    python -m bot.main --once                # 真跑一次（受 config.json 中 dry_run 控制）
    python -m bot.main --live                # 覆盖为真实发送
    python -m bot.main                       # 常驻循环
"""

import argparse
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from fkst_sdk import FkstClient                      # noqa: E402
from bot.config import BotConfig, STATE_FILE, load_env  # noqa: E402
from bot.engine import BotEngine                     # noqa: E402
from bot.state import BotState                       # noqa: E402


def log(msg):
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    if _LOGFILE is not None:
        try:
            _LOGFILE.write(line + "\n")
            _LOGFILE.flush()
        except Exception:
            pass


_LOGFILE = None


def open_log(log_dir: str):
    """同时把运行日志写入 logs/bot_YYYYMMDD.log。"""
    global _LOGFILE
    d = Path(log_dir)
    d.mkdir(parents=True, exist_ok=True)
    _LOGFILE = open(d / f"bot_{time.strftime('%Y%m%d')}.log", "a", encoding="utf-8")
    log("=" * 46)


def build(args):
    load_env()
    cfg = BotConfig.load()
    if args.dry_run:
        cfg.safety.dry_run = True
    if args.live:
        cfg.safety.dry_run = False

    client = FkstClient(session_file=str(ROOT / "user_session.json"),
                        min_interval=cfg.safety.request_interval, debug=args.debug)
    if not (client.load_session() and True):
        pass
    if client.dynamic_params.get("unionid") == "guest":
        import os
        phone = os.environ.get("FKST_PHONE") or os.environ.get("LOGIN_PHONE")
        pwd = os.environ.get("FKST_PASSWORD") or os.environ.get("LOGIN_PASSWORD")
        if not phone or not pwd:
            log("!! .env 中缺少 FKST_PHONE / FKST_PASSWORD")
            sys.exit(2)
        log(f"登录中… ({phone[:3]}****{phone[-4:]})")
        client.login(phone, pwd)
    log(f"身份: mid={client.mid} nickname={BotConfig.load().owner_names or ''}")

    engine = BotEngine(client, cfg, BotState(STATE_FILE), log=log)
    log(f"机器人昵称: {engine.bind_nick() or '(未知)'}")
    log(f"白名单作者: {cfg.owner_home_ids} | dry_run={cfg.safety.dry_run} | "
        f"今日已回复 {engine.state.day_replies}/{cfg.reply.max_replies_per_day}")
    return engine


def run_once(engine):
    actions = engine.scan_once()
    engine.execute(actions)
    return actions


def main():
    ap = argparse.ArgumentParser(description="fkst 评论机器人")
    ap.add_argument("--once", action="store_true", help="只跑一轮")
    ap.add_argument("--dry-run", action="store_true", help="演练，不真正发送")
    ap.add_argument("--live", action="store_true", help="强制真实发送")
    ap.add_argument("--debug", action="store_true", help="打印请求明细")
    ap.add_argument("--log-dir", default=None, help="把日志追加写入该目录（按天分文件）")
    args = ap.parse_args()

    if args.log_dir:
        open_log(args.log_dir)

    engine = build(args)

    if args.once:
        run_once(engine)
        return

    log(f"进入循环模式，间隔 {engine.cfg.scan.interval_seconds}s（Ctrl+C 退出）")
    while True:
        try:
            run_once(engine)
        except KeyboardInterrupt:
            log("手动停止")
            return
        except Exception as e:  # 网络抖动等，不退出
            log(f"本轮异常: {type(e).__name__}: {e}")
        time.sleep(engine.cfg.scan.interval_seconds)


if __name__ == "__main__":
    main()
