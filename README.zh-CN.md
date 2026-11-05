# HyperOS FCM Fix

[English](README.md) | **简体中文** | [繁體中文](README.zh-TW.md)

HyperOS FCM Fix 用于防止小米 HyperOS 冻结 Google Play 服务，或过度限制需要及时接收 Firebase Cloud Messaging (FCM) 通知的应用。它使用 Shizuku 提供的 ADB shell 身份，无需 Root。

本项目是 [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) 的分支，目标是免维护：自动保护每个 FCM 应用（包括之后安装的应用），并通过系统事件触发，而不是轮询。

[<img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="通过 GitHub 获取" height="80">](https://github.com/dingwen07/hyperos-fcm-fix/releases)

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="通过 Obtainium 获取" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22net.extrawdw.apps.miuisucks.powerkeeper%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdingwen07%2Fhyperos-fcm-fix%22%2C%22author%22%3A%22dingwen07%22%2C%22name%22%3A%22HyperOS%20FCM%20Fix%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D)

## 主要功能

- 将 Google Play 服务 (`com.google.android.gms`) 和本应用保留在 HyperOS 的隐藏无限制列表 (`MILLET_NO_RESTRICT_APP`) 中，并在 PowerKeeper 重写该列表时立即修复这些条目。
- 停用已知的 Greezer GMS 限制器，并将 GMS 恢复到正常的运行时允许列表。
- 无需请求 `QUERY_ALL_PACKAGES` 权限即可查找所有已安装的第三方 FCM 应用，并默认保护它们，包括之后安装或更新的应用。
- 为每个应用分别提供 Aurogon FCM 保护、自动解除停止、HyperOS 自启动和 AOSP 电池优化控制。你停用的应用会一直保持停用。
- 在 Google Play 服务被恢复后请求其重新连接。
- 提供适用于 HyperOS 4 的实验性夜间 FCM 保护，默认关闭，并保留深度 Doze。
- 不使用任何计时器：没有轮询循环、闹钟或定期任务。它响应系统事件，并保留用于故障排查的诊断记录。

## 要求与设置

本应用仅在小米 HyperOS 设备的机主用户中运行。应用清单依赖小米的 `com.miui.system` 共享库，因此不受支持的设备无法安装。本应用不会修改 PowerKeeper、清除应用数据或使用 Root。

1. 从 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 安装 Shizuku，然后通过无线调试、ADB 或 Sui 启动。Play 商店版 Shizuku 在 Android 16 QPR2 上可能存在兼容性问题。
2. 安装并打开 HyperOS FCM Fix，然后授予 Shizuku 权限。设置到此结束：保护会立即启动，应用列表会显示它找到的所有 FCM 应用。
3. 确认 Google Play 服务显示 **保护已启用**。GMS 会受到自动保护，无需在应用列表中另行启用。
4. 可选：检查应用列表，关闭不想管理的应用，或更改其 Aurogon、自动解除停止、自启动和电池设置。**立即应用**会重新执行全部操作，仅在故障排查时需要。

设备重启后，Shizuku 再次运行时保护会自动恢复。除非重新启动 Shizuku 或使用开机启动方式，否则 Shizuku 会在重启后停止。

> [!NOTE]
> 如要停止使用 HyperOS FCM Fix，请先关闭已启用的 **夜间 FCM 保护（HyperOS 4）**，并保持 Shizuku 运行，以移除本应用添加的豁免。在应用及其自启动管理开关仍处于启用状态时，将每个受管理应用的 **HyperOS 自启动**设为希望保留的状态。然后停用所有受管理应用，重启设备后再卸载。停用应用会移除其 Aurogon 和自动解除停止规则，并将受管理的电池优化恢复为 **优化**；其余临时系统更改会在重启后复原。HyperOS 自启动是例外，会保留最后应用的状态。

> [!TIP]
> AOSP 电池管理默认关闭。建议先使用 **优化**，仅当某个应用的通知仍然延迟时才使用 **无限制**，因为允许更多后台活动可能会增加耗电量。

## 保护行为

### 无需轮询的运行方式

本应用进程中的前台服务（通知名为 **后台保护**，最低重要性）持有下列观察器和接收器。它不放在 Shizuku UserService 中，因为该进程以 shell 身份运行，没有 `Context` 和 `Looper`，不借助隐藏的 Binder API 就无法注册内容观察器或动态接收器。`PACKAGE_ADDED` 等应用包广播也不会投递到清单中声明的接收器，因此需要有常驻进程来接收。在 HyperOS 上，本应用会保护自己：加入 device-idle 白名单、获得自启动权限、使用活跃待机分组，并将自身包名保留在 `MILLET_NO_RESTRICT_APP` 中。开机和应用更新接收器会在重启或更新后重新启动该服务。

没有任何内容按计时器重复执行。事件会合并两秒（最多 20 秒）后统一处理，特权操作通过 Shizuku 执行：

| 事件 | 执行内容 |
| --- | --- |
| `Settings.System` 的 `MILLET_NO_RESTRICT_APP` 发生变化 | 缺少时重新添加 GMS 和本应用；修复 GMS 时发送 GMS 重连广播 |
| Aurogon 设置发生变化 | 恢复本应用的 Aurogon 规则 |
| 夜间游戏白名单发生变化（仅在该选项开启时） | 恢复 GMS 条目 |
| 应用包被安装或更新 | 保护新的 FCM 应用；对已管理的应用重新应用策略 |
| 应用包被强行停止或重启 | 对启用自动解除停止的受管理应用清除 `FLAG_STOPPED` |
| 开机、本应用更新、Shizuku binder 或权限可用 | 完整流程，包括 Greezer 命令（它们会在 `system_server` 重启时重置） |
| 屏幕解锁 | 检查上述设置，以防遗漏事件 |

如果某次处理无法完成（例如 Shizuku 尚未运行），它会保持待处理状态并合并到下一个事件中，因此不会按时钟重试。Shizuku binder 就绪本身就是这样的事件。

### Google Play 服务

核心修复会保留 `Settings.System.MILLET_NO_RESTRICT_APP` 中的所有现有条目，仅在缺少时追加 `com.google.android.gms` 和本应用的包名。

完整流程还会执行等效于 `dumpsys greezer IM GMS disable` 和 `dumpsys greezer LM add com.google.android.gms` 的操作。任一 Greezer 命令失败时会报告为不可用，该流程会保持待处理状态，等待下一个事件。

**FCM 连接保护**默认开启。当 GMS 需要被重新加入无限制列表时，它会发送定向的 `com.google.android.intent.action.GCM_RECONNECT` 广播，使 CN Google Play 服务构建能及时重新连接。本应用没有定期的套接字探测。关闭此选项不会停用 `MILLET_NO_RESTRICT_APP` 修复。

**夜间 FCM 保护（HyperOS 4）**是实验性功能，默认关闭。它针对在中国版 HyperOS 4 上复现的额外夜间冻结；即使关闭小米原有的夜间省电，该冻结仍会发生。本功能将 GMS 保留在小米共用的游戏预下载豁免列表中，同时保留深度 Doze。该豁免只要存在就会生效，包括白天，可能增加耗电量。

该选项开启时，服务会监视此列表，并在小米的下载组件清空它时修复。修复会通知框架更新，并发送用于机主 GMS 的解冻和重连广播，即使关闭 FCM 连接保护也会执行。其他条目会保留。关闭该选项时，仅移除本应用添加的 GMS；原先已有的 GMS 会保留。Shizuku 不可用时，清理会等待其恢复。

不同 Android 用户中的 GMS 有各自的 UID 和连接状态。共用的应用包豁免可以覆盖分身 GMS，但恢复广播针对机主 GMS。已冻结分身 GMS 的即时解冻、整夜通知投递、耗电量及重启或 OTA 后的行为尚未验证。证据和限制见 [HyperOS 4 夜间冻结调查](docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)。

### 受管理的应用

每个声明了 `com.google.android.c2dm.intent.RECEIVE` 接收器的非系统应用包都默认启用，无论是在本应用之前还是之后安装的。每个已启用应用有四项独立控制：

- Aurogon FCM 保护：允许 FCM Intent 通过小米的广播控制并投递到应用，包括投递过程需要启动应用的情况。
- 自动解除停止（Android 16+）：在应用被安装、更新或强行停止时清除 `FLAG_STOPPED`，使应用仍有资格由 FCM 启动；该操作本身不会启动应用。
- HyperOS 自启动：在 FCM 投递目标没有运行中进程（例如进程已被杀死）时允许启动该进程。此时它会与 Aurogon 规则配合。本应用会管理小米的两个自启动 AppOp。
- AOSP 电池优化：主要用于限制应用的后台活动，同时不影响 FCM。它会为所选 Android 用户应用无限制、优化或受限制策略，默认关闭。

新发现的应用默认开启 Aurogon、自动解除停止和自启动管理，电池管理关闭。

在列表中关闭某个应用后，该选择会被记住：即使应用更新，也不会被自动重新启用。停用应用时，本应用会将其从受管理的 Aurogon 和自动解除停止列表中移除，同时保留已保存的选择。如果此前启用了电池优化管理，则会应用一次 **优化**；自启动状态不会改变。

**立即应用**会处理所有已启用应用。在列表中更改的设置会立即生效。

自启动和电池策略以本应用中选定的 Android 用户为目标；机主用户和手机分身用户默认选中。Android 的无限制 device-idle 允许列表以应用包为全局范围，因此“无限制”和“优化”的更改无法按用户隔离。

## 诊断

打开主屏幕底部的 **诊断信息**，可查看、刷新、复制或清除会话日志。日志涵盖 Shizuku 连接、守护事件、特权操作和 FCM 修复，文件保存在 `/storage/emulated/0/Android/data/net.extrawdw.apps.miuisucks.powerkeeper/files/logs/`。Logcat 条目使用 `PowerKeeperFix` 前缀。

## 技术调查

- [小米 HyperOS GMS、FCM 与 Greezer 调查](docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)
- [PowerKeeper 重写 `MILLET_NO_RESTRICT_APP` 的调查](docs/xiaomi-millet-no-restrict-app-rewrite-investigation.md)
- [HyperOS 4 夜间 GMS 冻结与无需 Root 的解决方案](docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)

## 构建

在 Android Studio 中打开项目并运行 `app` 配置。
