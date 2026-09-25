@echo off
rem fkst comment bot - run one live round (called by Task Scheduler or by hand)
chcp 65001 >nul
rem 切到仓库根目录（本文件在 bot\ 下）
cd /d "%~dp0.."

rem 如需指定解释器，把下面这行改成你的 python.exe 绝对路径
set PY=python

%PY% -m bot.main --once --live --log-dir logs
