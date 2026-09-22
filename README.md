# CardVault

**Offline encrypted card and address vault for Android and Windows.**

当前版本：Android 1.5.5 / Windows 1.5.6

CardVault 是一款完全离线的银行卡与地址资料保险库，提供 Android 与 Windows 两个版本。两端采用一致的深色视觉语言、堆叠卡包、原创卡面、翻转详情、一键复制、增删改和拖动排序；布局会根据手机与桌面屏幕分别适配。

CardVault 不连接服务器，也不在应用中使用网络。Android 和 Windows 之间通过用户主动传送的端到端加密文件同步，可使用微信、QQ、邮件、本地文件或 U 盘作为传输通道。

## 主要功能

- 卡片正面只显示原创卡面、用户设置的名称设计和离线识别到的 Visa、Mastercard 或银联标志。
- 卡号、`MM/YY`、CVV 和备注只显示在卡片背面；例如 2029 年 6 月显示为 `06/29`。
- Android 点击眼睛后通过系统 `BiometricPrompt`（强生物识别或设备凭据）认证，再短时显示完整信息。
- Windows 使用系统 `UserConsentVerifier` / Windows Hello 验证敏感显示、编辑、删除和导入导出操作。
- 完整卡号可以复制；Android 与 Windows 都会在限定时间后尝试清除仍由 CardVault 持有的剪贴板内容。CVV 不提供复制按钮。
- 16 套预设颜色、18 套原创花纹、9 种名称版式、名称颜色和 HSV 自定义调色板均为本地绘制。
- 可选备注只显示在卡片背面右下角。
- 卡片支持单击详情、平滑翻转以及长按拖动排序。
- 地址支持昵称、详细地址、城市、其它、邮编和国家；两端均可添加、编辑、删除、翻转查看并复制完整地址。
- 银行卡与地址都支持原创配色、花纹、名称版式和自定义颜色。
- 应用退到后台或 Windows 窗口失焦/最小化时立即隐藏已显示的敏感内容。
- Android 进入应用只显示掩码内容，不要求启动认证；切换应用后返回会保留未保存的添加或编辑草稿。查看完整卡号、CVV，以及编辑和删除仍要求一次系统身份认证。

## 离线双向同步

交换协议使用两类文件：

- `.cvpair`：首次连接新设备的配对文件，使用一次性 128 位配对码保护。
- `.cvsync`：配对完成后的常规同步文件，使用两端共享的 256 位同步密钥保护。

两类文件都使用版本化二进制格式、HKDF-SHA256 和 AES-256-GCM。每个文件使用新的随机 Salt 和 12 字节 IV，完整 64 字节文件头作为 AAD；CardVault 会拒绝错误密钥、篡改、截断、过期、重复或旧序列文件。同步文件不包含 Android Keystore 密钥、Android 本地 DEK 或 Windows 本地 DEK。

### 手机首次连接 Windows

1. Android 打开“设置 → 设备间同步”。
2. 点击“首次连接其他设备”或“连接新设备”，完成系统认证。
3. 点击“打开系统分享”，通过微信等工具发送 `.cvpair` 文件。
4. Windows 打开“同步”，选择下载好的 `.cvpair` 文件，并输入手机上显示的一次性配对码。
5. 后续任一端生成 `.cvsync` 文件，另一端导入即可。

配对码不要和 `.cvpair` 文件放在同一条转发消息中。配对文件默认 30 分钟过期；丢失配对码时可在任一已配对设备重新生成新的配对文件。

### Windows 导出

Windows 端先选择并记住导出文件夹，再点击导出。CardVault 会把加密文件原子写入该目录；用户随后可用微信发送到手机。Android 通过系统文件选择器导入，应用无需存储权限。

### 合并规则

- 每条银行卡、地址以及两个列表的排序都带独立版本向量。
- 删除操作使用独立墓碑，避免旧设备把已删除记录重新带回。
- 并发修改不会静默覆盖：CardVault 会保留确定性的冲突副本并在导入结果中提示。
- 最近包 ID 和每个来源的永久序列水位共同防止重放。
- 单个交换文件最大 4 MiB；银行卡和地址各最多 256 条活动记录、1024 条记录加墓碑，最多 16 台来源设备。
- 协议 v2 同时携带银行卡和地址；旧 v1 银行卡文件仍可导入，且不会清空本机地址。

## 本地加密

### Android

- Android Keystore 保存不可导出的 AES-256 KEK。
- 随机 256 位本地 DEK 由 KEK 使用 AES-GCM 包装。
- 每条银行卡或地址 payload 使用本地 DEK、独立随机 IV 和记录 UUID/schemaVersion AAD 加密后写入 Room。
- 共享同步密钥再次使用本地 DEK 加密，AAD 绑定 vaultId 和 keyEpoch。
- Room 只保存银行卡/地址密文、IV、版本向量、排序、时间、墓碑和必要同步元数据。

### Windows

- 随机 256 位本地 DEK 由当前 Windows 用户的 DPAPI 保护。
- 本地保险库使用 AES-256-GCM 加密并以临时文件、强制落盘和原子替换保存到 `%LOCALAPPDATA%\CardVault`。
- 当前 Windows 用户之外无法直接解开本地 DEK；窗口失焦会撤销界面中的敏感显示。

Android 本地 DEK、Windows 本地 DEK 和跨设备同步密钥彼此独立。交换文件泄露时仍需要相应配对码或同步密钥才能解密。

## 权限与隐私

Android 源 Manifest 不申请 `INTERNET`、`ACCESS_NETWORK_STATE`、位置、相机、麦克风、联系人、短信、电话、通知、存储或媒体权限。应用不包含 Firebase、广告、Analytics、Crash SDK、WebView 或网络库。

Android 使用不可导出的 `FileProvider`，只为系统分享临时授予单个加密文件的读取 URI；导入使用 Storage Access Framework。`android:allowBackup="false"`，系统备份规则排除数据库、设置和文件。

Windows 端不执行 HTTP 请求。微信、QQ 或邮件只承担用户选择的密文传输，不属于 CardVault 的网络同步。

## 构建工具链

| 项目 | 版本 |
| --- | --- |
| Android Gradle Plugin | 9.2.1 |
| Gradle Wrapper | 9.6.1 |
| Kotlin | 2.4.10 |
| Android Compose BOM | 2026.06.01 |
| Compose Desktop | 1.11.0 |
| Room | 2.8.4 |
| JNA / JNA Platform | 5.18.1 |
| Android compileSdk | 36.1 |
| Android targetSdk / minSdk | 36 / 30 |
| Windows 打包 JDK | Temurin 21 |

Android 命令行构建可只为当前 PowerShell 会话设置 Android Studio JBR：

```powershell
$env:JAVA_HOME = "<Android Studio>\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleRelease
```

Windows 开发验证：

```powershell
.\gradlew.bat :desktopApp:test :desktopApp:compileKotlin
```

Windows 安装器需要带 `jpackage` 的完整 JDK 21；生成 EXE/MSI 还需要 WiX 3.x。工具可以使用便携 ZIP，无需安装或修改系统环境变量。

```powershell
.\gradlew.bat :desktopApp:createDistributable
.\gradlew.bat :desktopApp:packageExe
.\gradlew.bat :desktopApp:packageMsi
```

## 构建产物

为避免源码仓库混入不透明的二进制文件，APK、AAB、EXE、MSI 和本地 `dist/` 目录不进入 Git 历史。正式对外提供的构建产物应作为 GitHub Release 附件发布，并同时公布 SHA-256 校验值。

当前 Android 交付包为不可调试、已压缩的 Release APK，但使用本机标准 Android 调试证书签名，仅适合个人安装验证；应用商店发布应在仓库外使用长期保管的正式发布密钥重新签名。仓库不包含 Android 正式发布密钥或 Windows 代码签名证书，因此 Windows 安装器可能显示“未知发布者”。

## 数据恢复与注意事项

- 同步是用户主动触发的全量加密快照交换，不是实时云同步。
- 卸载应用、清除数据、Windows 用户配置损坏或丢失所有已配对设备可能导致数据不可恢复；应定期保留最新加密同步文件。
- 用户主动复制卡号后，系统剪贴板会短暂包含明文。恶意输入法、已失陷系统或在清除前读取剪贴板的程序仍可能获取该内容。
- Android 在一次系统认证成功后的当前前台会话允许截图；进入后台会恢复 `FLAG_SECURE`，但已生成的截图无法撤回。
- Kotlin/Java `String` 不能可靠清零。应用会限制敏感状态的范围和存活时间，但不能保证托管内存中不存在残留。
- Root/越狱设备、恶意 ROM、进程注入、已失陷 Windows 用户会话或已知设备凭据超出应用层保护能力。

## 开源、贡献与商标

CardVault 的源代码依据 [Apache License 2.0](LICENSE) 开源。欢迎通过 Issue 报告可复现的问题，或通过 Pull Request 提交范围清晰、已验证的改进。

请勿在 Issue、截图、日志或测试数据中提交真实卡号、CVV、地址、配对码、同步文件或其他私密数据。如发现安全问题，请使用 GitHub 的私密安全报告功能，不要在公开 Issue 中公布利用细节或用户数据。

Visa、Mastercard、银联及其他可能显示的名称或标识属各自权利人所有。本项目与这些机构无隶属、赞助或背书关系；这些标识仅用于离线识别用户自行录入的卡类型。
