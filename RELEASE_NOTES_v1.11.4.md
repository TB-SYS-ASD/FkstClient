# v1.11.4（测试版）

修复练习页能进、题干全空的问题。

## 改动

练习页能进但题干全空：`paperExercises` 页面内拉题干的 ajax 需要 `yex_session` 会话 cookie，
直载答题页只种 `XSRF-TOKEN`（桌面端 2026-08-11 实测同款坑）。
现照搬桌面端两段式 cookie bootstrap：先静默访问 H5 首页种会话 cookie，再加载答题页。

## 校验

- 包名：`com.tb.fkst`
- versionCode 16 / versionName 1.11.4
- 体积：10,976,674 字节
- SHA256：`1ff07da7cf544a9e734bf6205fa42277a70d204f31000d1a550f04f92b5fec19`
