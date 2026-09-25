# FkstClient v1.8.0

发布日期：2026-09-25
`versionCode` 9 / `versionName` 1.8.0

这一版的所有改动都来自同一件事：**拿到了官方 App 的脱壳 dex**（2.3.3，5 个文件、
34,105 个类）。以前只能扫加固壳的字符串池猜接口，这次等于把源码摊开，
逐条核对后发现之前的几个结论是错的 —— 下面是修正结果。

---

## 修了一个真 bug：收藏其实一直没生效

之前收藏用的是 `UpdateSTCollection`，它返回 `{"res":0}`，界面也提示成功，
但**服务端根本没记**。对照实验：

| | `UpdateSTCollection`（旧） | `CollectShuatiNote`（新） |
| --- | --- | --- |
| 调用返回 | `{"res":0}` | `{"res":0}` |
| 再去查「我的收藏」列表 | **查不到这条笔记** | **这条笔记在列表里** |
| 取消收藏后再查 | —— | 从列表消失 |

两个接口返回一模一样，区别只有落不落库。现在换成 `CollectShuatiNote`
（`nid` + `status`），收藏 / 取消都做了可逆验证：收藏后列表里出现，取消后消失。

**判据方法**（以后都用这个）：拿一个肯定不存在的 id 去打。
`CollectShuatiNote` 对不存在的 nid 回「文章已被删除」，说明它真去查了；
`UpdateSTCollection` 那种怎么都回 `res:0` 的，一律当假成功处理。

## 新增：编辑 / 删除自己的笔记

详情页右上角多了个「更多」菜单，**只对自己的笔记出现**：

- **编辑配图** —— 可以删掉某张图、再加新图，最多 9 张。
- **删除笔记** —— 二次确认后删除。

⚠️ 两点要说清楚：

1. **只能改图，标题和正文改不了**。这不是偷懒，是服务端没开放：
   dex 里笔记编辑相关的接口只有三个，`UpdateNoteTag` 要管理员权限
   （实测回「非本区管理员」）、`UpdateUploadNoteStatus` 对不存在的 id 也返回成功
   （无脑 `res:0`，`score`/`status` 的取值语义也没有任何线索），
   只有 `UpdateUploadNoteUrls` 是真有校验的（不存在的 id 会回 `res:2`）。
   `UpdateUploadNoteFilter` 已经 404 下线。
2. **保存是全量覆盖**。提交什么就是什么 —— 界面里显示的那几张就是最终存上去的，
   删掉的旧图不会保留。

## 验证：签名盐没有换

dex 里有个很显眼的串 `api.yaerxing.com-f11cb6c45e3317e3d624038a657d5ad1-`，
32 位十六进制，看着就是新版盐。实测：

```
现盐 9bldwb2d5d02e81h             → {"res":0,"notes":[…]}   正常
f11cb6c45e3317e3d624038a657d5ad1  → {"res":1,"error":"url illegal!"}
```

**不是签名盐**，现在这套签名一切正常。另外 dex 里压根找不到 `F.K*$t` 模板串
和 api_key —— 签名实现在 native（`com.yaerxing.fkst.security.NativeHelper`）。
那个 32 位串用途不明，大概率是推送 / IM / 统计用的，不影响当前协议。

## 仍然没做：刷题模块

原本指望 `verifyShareQuestionExercise-v2`（分享验证页）可能不带登录门禁，
实测**一样被挡**。`questionExercise-v17` 恒回「请求不合法-1」，
分享页恒回「非法访问」，换 `id` / `qid` / `ids` / `paper_id` / `kid` / `xd+subject`
等 8 种参数组合，提示一个字都没变 —— 说明卡的是登录态 / 签名这一层，
不是参数名猜错了。

所以「试卷 → 题目」那条链依旧是断的，刷题模块维持删除状态。
App 第一次启动时那页说明也还是这么写的。

---

## 变更清单

**新增**
- `ui/screens/EditNoteScreen.kt` —— 编辑已发笔记的配图
- `Api.updateNoteImages()` / `AppViewModel` 的编辑态与 `isMyNote()`
- 详情页「更多」菜单：编辑配图 + 删除笔记（仅本人笔记）
- 接口常量 `SET_NOTE_COLLECTION` / `UPDATE_NOTE_URLS`
- Python SDK：`collect_note()` / `update_note_images()`

**修改**
- 收藏换接口：`UpdateSTCollection` → `CollectShuatiNote`
- `Api.setCollection()` 参数：`object_id` → `nid`
- 版本 1.8.0（versionCode 9）
- 文档：`API_RESEARCH.md` 新增第 17 章（官方端 dex 带来的修正）、
  `android/README.md` 功能表与已知限制

**未采用**（有理由，不是漏做）
- `UpdateUploadNoteStatus` —— 无脑 `res:0`，语义不明
- `UpdateNoteTag` —— 需要管理员权限
- `UpdateUploadNoteFilter` —— 已 404 下线
- 6 个刷题 H5 页面 —— 全部被门禁挡住

## 升级注意

- 覆盖安装不会清数据，之前收藏过的笔记不受影响（旧接口本来就没落库，
  不存在要迁移的数据）。
- 首次启动引导只在「政策未同意」时出现，老用户覆盖安装不会重复弹。
- 更新检查读的是 GitHub `releases/latest`，tag 必须是 `v?x.y.z` 这种规范格式，
  且**不能勾 pre-release**，否则客户端取不到。
```
