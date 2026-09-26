# v1.11.2（测试版）

修复「实时练习」拿不到登录凭据的根本原因。

## 根因（比 v1.11.1 认知的更深）
实时练习需要 `loginToken` / `tokenSeed` 给官方 H5 答题页签名。
- v1.11.0 曾以为登录时「漏抓」了这两个字段，补上即可 —— **实测不成立**：
  `STAccountLogin3` 的明文响应里根本就不含 `loginToken` / `tokenSeed`
  （2026-09-26 用测试账号实测，响应只有 `unionid/openid/mid/device_token/res`）。
- 所以无论怎么重新登录，走 ST 通道永远拿不到凭据。

## 本版修法
移植官方 APP 真正的登录通道（YEX 加密登录，与桌面版同源）：
- 业务参数（手机号+密码）走「RSA 包裹的 AES-256-GCM」信封加密；
- 客户端现生成一对设备 RSA 密钥，服务端把账号数据（含 `loginToken`/`tokenSeed`）
  加密成 `encryptedAccount` 返回，客户端解密落库。
- YEX 登录失败不阻塞主登录：ST 会话照常可用，只是实时练习不可用。

## 升级用户
安装本版后，请**退出登录 → 重新登录一次**（旧会话里的凭据没法补，
必须重新走一次登录）。之后「实时练习」即可正常进入官方答题页。

## 校验
- 包名：`com.tb.fkst`
- versionCode 14 / versionName 1.11.2
- 体积：10,983,764 字节
- SHA256：`f3c9f6ec5f7de20bf650f31126c11500de7f78f0be8429a20a859e1f721a1e3b`
