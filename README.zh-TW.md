# HyperOS FCM Fix

[English](README.md) | [简体中文](README.zh-CN.md) | **繁體中文**

HyperOS FCM Fix 用於防止小米 HyperOS 凍結 Google Play 服務，或過度限制需要及時接收 Firebase Cloud Messaging (FCM) 通知的應用程式。它使用 Shizuku 提供的 ADB shell 身分，無需 Root。

本專案是 [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) 的分支，目標是免維護：自動保護每個 FCM 應用程式（包括之後安裝的應用程式），並透過系統事件觸發，而不是輪詢。

[<img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="透過 GitHub 取得" height="80">](https://github.com/dingwen07/hyperos-fcm-fix/releases)

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="透過 Obtainium 取得" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22net.extrawdw.apps.miuisucks.powerkeeper%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdingwen07%2Fhyperos-fcm-fix%22%2C%22author%22%3A%22dingwen07%22%2C%22name%22%3A%22HyperOS%20FCM%20Fix%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D)

## 主要功能

- 將 Google Play 服務 (`com.google.android.gms`) 和本應用程式保留在 HyperOS 的隱藏無限制清單 (`MILLET_NO_RESTRICT_APP`) 中，並在 PowerKeeper 重寫該清單時立即修復這些項目。
- 停用已知的 Greezer GMS 限制器，並將 GMS 恢復至正常的執行階段允許清單。
- 無需要求 `QUERY_ALL_PACKAGES` 權限即可找出所有已安裝的第三方 FCM 應用程式，並預設保護它們，包括之後安裝或更新的應用程式。
- 為每個應用程式分別提供 Aurogon FCM 保護、自動解除停止、HyperOS 自動啟動和 AOSP 電池最佳化控制。你停用的應用程式會一直保持停用。
- 在 Google Play 服務被恢復後要求其重新連線。
- 提供適用於 HyperOS 4 的實驗性夜間 FCM 保護，預設關閉，並保留深度 Doze。
- 不使用任何計時器：沒有輪詢迴圈、鬧鐘或定期工作。它回應系統事件，並保留用於疑難排解的診斷記錄。

## 要求與設定

本應用程式僅在小米 HyperOS 裝置的擁有者使用者中執行。應用程式資訊清單依賴小米的 `com.miui.system` 共用程式庫，因此不受支援的裝置無法安裝。本應用程式不會修改 PowerKeeper、清除應用程式資料或使用 Root。

1. 從 [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) 安裝 Shizuku，然後透過無線偵錯、ADB 或 Sui 啟動。Play 商店版 Shizuku 在 Android 16 QPR2 上可能有相容性問題。
2. 安裝並開啟 HyperOS FCM Fix，然後授予 Shizuku 權限。設定到此結束：保護會立即啟動，應用程式清單會顯示它找到的所有 FCM 應用程式。
3. 確認 Google Play 服務顯示 **保護作用中**。GMS 會受到自動保護，無需在應用程式清單中另行啟用。
4. 可選：檢查應用程式清單，關閉不想管理的應用程式，或變更其 Aurogon、自動解除停止、自動啟動和電池設定。**立即套用**會重新執行全部操作，僅在疑難排解時需要。

裝置重新啟動後，Shizuku 再次執行時保護會自動恢復。除非重新啟動 Shizuku 或使用開機啟動方式，否則 Shizuku 會在重新啟動後停止。

> [!NOTE]
> 如要停止使用 HyperOS FCM Fix，請先關閉已啟用的 **夜間 FCM 保護（HyperOS 4）**，並保持 Shizuku 執行，以移除本應用程式新增的豁免。在應用程式及其自動啟動管理開關仍處於啟用狀態時，將每個受管理應用程式的 **HyperOS 自動啟動**設為希望保留的狀態。然後停用所有受管理的應用程式，重新啟動裝置後再解除安裝。停用應用程式會移除其 Aurogon 和自動解除停止規則，並將受管理的電池最佳化恢復為 **最佳化**；其餘暫時性系統變更會在重新啟動後復原。HyperOS 自動啟動是例外，會保留最後套用的狀態。

> [!TIP]
> AOSP 電池管理預設關閉。建議先使用 **最佳化**，僅當某個應用程式的通知仍然延遲時才使用 **無限制**，因為允許更多背景活動可能會增加耗電量。

## 保護行為

### 無需輪詢的運作方式

本應用程式處理程序中的前景服務（通知名稱為 **背景保護**，最低重要性）持有下列觀察器和接收器。它不放在 Shizuku UserService 中，因為該處理程序以 shell 身分執行，沒有 `Context` 和 `Looper`，不借助隱藏的 Binder API 就無法註冊內容觀察器或動態接收器。`PACKAGE_ADDED` 等套件廣播也不會投遞到資訊清單中宣告的接收器，因此需要有常駐處理程序來接收。在 HyperOS 上，本應用程式會保護自己：加入 device-idle 白名單、取得自動啟動權限、使用作用中待命分組，並將自身套件名稱保留在 `MILLET_NO_RESTRICT_APP` 中。開機和應用程式更新接收器會在重新啟動或更新後重新啟動該服務。

沒有任何內容依計時器重複執行。事件會合併兩秒（最多 20 秒）後統一處理，特權操作透過 Shizuku 執行：

| 事件 | 執行內容 |
| --- | --- |
| `Settings.System` 的 `MILLET_NO_RESTRICT_APP` 發生變化 | 缺少時重新新增 GMS 和本應用程式；修復 GMS 時傳送 GMS 重新連線廣播 |
| Aurogon 設定發生變化 | 恢復本應用程式的 Aurogon 規則 |
| 夜間遊戲白名單發生變化（僅在該選項開啟時） | 恢復 GMS 項目 |
| 套件被安裝或更新 | 保護新的 FCM 應用程式；對已管理的應用程式重新套用政策 |
| 套件被強制停止或重新啟動 | 對啟用自動解除停止的受管理應用程式清除 `FLAG_STOPPED` |
| 開機、本應用程式更新、Shizuku binder 或權限可用 | 完整流程，包括 Greezer 命令（它們會在 `system_server` 重新啟動時重設） |
| 螢幕解鎖 | 檢查上述設定，以防遺漏事件 |

如果某次處理無法完成（例如 Shizuku 尚未執行），它會保持待處理狀態並合併到下一個事件中，因此不會依時鐘重試。Shizuku binder 就緒本身就是這樣的事件。

### Google Play 服務

核心修復會保留 `Settings.System.MILLET_NO_RESTRICT_APP` 中的所有現有項目，僅在缺少時附加 `com.google.android.gms` 和本應用程式的套件名稱。

完整流程還會執行等效於 `dumpsys greezer IM GMS disable` 和 `dumpsys greezer LM add com.google.android.gms` 的操作。任一 Greezer 命令失敗時會回報為無法使用，該流程會保持待處理狀態，等待下一個事件。

**FCM 連線保護**預設開啟。當 GMS 需要被重新加入無限制清單時，它會傳送定向的 `com.google.android.intent.action.GCM_RECONNECT` 廣播，使 CN Google Play 服務版本能及時重新連線。本應用程式沒有定期的 socket 探測。關閉此選項不會停用 `MILLET_NO_RESTRICT_APP` 修復。

**夜間 FCM 保護（HyperOS 4）**是實驗性功能，預設關閉。它針對在中國版 HyperOS 4 上重現的額外夜間凍結；即使關閉小米原有的夜間省電，該凍結仍會發生。本功能將 GMS 保留在小米共用的遊戲預先下載豁免清單中，同時保留深度 Doze。該豁免只要存在就會生效，包括白天，可能增加耗電量。

該選項開啟時，服務會監視此清單，並在小米的下載元件清空它時修復。修復會通知框架更新，並傳送用於擁有者 GMS 的解凍和重新連線廣播，即使關閉 FCM 連線保護也會執行。其他項目會保留。關閉該選項時，僅移除本應用程式新增的 GMS；原先已有的 GMS 會保留。Shizuku 無法使用時，清理會等待其恢復。

不同 Android 使用者中的 GMS 有各自的 UID 和連線狀態。共用的套件豁免可以涵蓋複製版 GMS，但恢復廣播針對擁有者 GMS。已凍結複製版 GMS 的即時解凍、整夜通知投遞、耗電量及重新啟動或 OTA 後的行為尚未驗證。證據和限制見 [HyperOS 4 夜間凍結調查](docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)。

### 受管理的應用程式

每個宣告了 `com.google.android.c2dm.intent.RECEIVE` 接收器的非系統套件都預設啟用，無論是在本應用程式之前還是之後安裝的。每個已啟用的應用程式有四項獨立控制：

- Aurogon FCM 保護：允許 FCM Intent 通過小米的廣播控制並投遞到應用程式，包括投遞過程需要啟動應用程式的情況。
- 自動解除停止（Android 16+）：在應用程式被安裝、更新或強制停止時清除 `FLAG_STOPPED`，使應用程式仍有資格由 FCM 啟動；該操作本身不會啟動應用程式。
- HyperOS 自動啟動：在 FCM 投遞目標沒有執行中處理程序（例如處理程序已被終止）時允許啟動該處理程序。此時它會與 Aurogon 規則配合。本應用程式會管理小米的兩個自動啟動 AppOp。
- AOSP 電池最佳化：主要用於限制應用程式的背景活動，同時不影響 FCM。它會為所選 Android 使用者套用無限制、最佳化或受限制政策，預設關閉。

新找到的應用程式預設開啟 Aurogon、自動解除停止和自動啟動管理，電池管理關閉。

在清單中關閉某個應用程式後，該選擇會被記住：即使應用程式更新，也不會被自動重新啟用。停用應用程式時，本應用程式會將其從受管理的 Aurogon 和自動解除停止清單中移除，同時保留已儲存的選擇。如果此前啟用了電池最佳化管理，則會套用一次 **最佳化**；自動啟動狀態不會改變。

**立即套用**會處理所有已啟用的應用程式。在清單中變更的設定會立即生效。

自動啟動和電池政策以本應用程式中選定的 Android 使用者為目標；擁有者使用者和手機複製使用者預設選中。Android 的無限制 device-idle 允許清單以套件為全域範圍，因此「無限制」和「最佳化」的變更無法依使用者隔離。

## 診斷

開啟主畫面底部的 **診斷**，可檢視、重新整理、複製或清除工作階段記錄。記錄涵蓋 Shizuku 連線、守護事件、特殊權限操作和 FCM 修復，檔案儲存在 `/storage/emulated/0/Android/data/net.extrawdw.apps.miuisucks.powerkeeper/files/logs/`。Logcat 項目使用 `PowerKeeperFix` 前置字串。

## 技術調查

- [小米 HyperOS GMS、FCM 與 Greezer 調查](docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)
- [PowerKeeper 重寫 `MILLET_NO_RESTRICT_APP` 的調查](docs/xiaomi-millet-no-restrict-app-rewrite-investigation.md)
- [HyperOS 4 夜間 GMS 凍結與無需 Root 的解決方案](docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)

## 建置

在 Android Studio 中開啟專案並執行 `app` 設定。
