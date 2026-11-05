# HyperOS FCM Fix

**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md)

HyperOS FCM Fix prevents Xiaomi HyperOS from freezing Google Play services or over-restricting apps that need timely Firebase Cloud Messaging (FCM) notifications. It uses Shizuku's ADB shell identity and does not require root.

This fork of [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix) is built to need no upkeep: it protects every FCM app on its own, including apps installed later, and reacts to system events instead of polling.

[<img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/dingwen07/hyperos-fcm-fix/releases)

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22net.extrawdw.apps.miuisucks.powerkeeper%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdingwen07%2Fhyperos-fcm-fix%22%2C%22author%22%3A%22dingwen07%22%2C%22name%22%3A%22HyperOS%20FCM%20Fix%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%7D%22%7D)

## Features

- Keeps Google Play services (`com.google.android.gms`) and this app in HyperOS's hidden no-restrictions list (`MILLET_NO_RESTRICT_APP`) and repairs the entries the moment PowerKeeper rewrites the list.
- Disables the known Greezer GMS limiter and restores GMS to its normal runtime allowlist.
- Finds every installed third-party FCM app without requesting `QUERY_ALL_PACKAGES` and protects it by default, including apps installed or updated later.
- Gives each app separate Aurogon FCM protection, Auto unstop, HyperOS Autostart, and AOSP battery-optimization controls. You can turn an app off and it stays off.
- Asks Google Play services to reconnect after it had to be restored.
- Offers experimental nighttime FCM protection for HyperOS 4, off by default, while keeping deep Doze enabled.
- Uses no timers: no polling loop, alarm, or periodic job. It reacts to system events and keeps diagnostic records for troubleshooting.

## Requirements and setup

The app runs only in the owner profile on a Xiaomi device with HyperOS. Its manifest requires Xiaomi's `com.miui.system` shared library, so unsupported devices cannot install it. It does not modify PowerKeeper, clear app data, or use root.

1. Install Shizuku from [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases), then start it through wireless debugging, ADB, or Sui. The Play Store build of Shizuku may have compatibility issues on Android 16 QPR2.
2. Install and open HyperOS FCM Fix, then grant its Shizuku permission. That is the whole setup: protection starts at once and the app lists every FCM app it found.
3. Confirm that Google Play services shows **Protection active**. GMS is protected automatically and does not appear as an app you need to enable.
4. Optionally review the apps: turn off any you do not want managed, or change their Aurogon, Auto unstop, Autostart, and battery settings. **Apply now** reruns everything and is only needed while troubleshooting.

After a reboot, protection resumes by itself once Shizuku is running again. Shizuku stops on reboot unless you start it again or use a boot-time start method.

> [!NOTE]
> To stop using HyperOS FCM Fix, first turn off **Nighttime FCM protection (HyperOS 4)** if enabled, with Shizuku running so its app-added exemption can be removed. Set each managed app's **HyperOS Autostart** to the state you want while the app and its Autostart management switch are still enabled. Then disable every managed app and reboot before uninstalling. Disabling an app removes its Aurogon and Auto unstop rules and returns managed battery optimization to **Optimized**; the remaining temporary system changes reset after reboot. HyperOS Autostart is the exception and keeps the last applied state.

> [!TIP]
> AOSP battery management is off by default. Use **Optimized** to start, and **Unrestricted** only for an app whose notifications remain delayed, because allowing more background activity can increase battery use.

## Protection behavior

### How it stays running without polling

A foreground service in the app process (**Background protection** notification, minimum importance) holds the observers and receivers listed below. It is not in the Shizuku UserService because that process runs as shell without a `Context` or `Looper`, so it cannot register content observers or dynamic receivers without hidden binder APIs. Package broadcasts such as `PACKAGE_ADDED` also do not reach manifest receivers, so something has to stay alive to hear them. On HyperOS the app protects itself: it is on the device-idle allowlist, has Autostart permissions, uses an active standby bucket, and keeps its own package in `MILLET_NO_RESTRICT_APP`. Boot and app-update receivers restart the service after a reboot or update.

Nothing repeats on a timer. Events are merged for two seconds (at most 20 seconds) into a single pass, and privileged work runs through Shizuku:

| Event | What runs |
| --- | --- |
| `Settings.System` `MILLET_NO_RESTRICT_APP` changes | Re-add GMS and this app if missing; on a GMS repair, send the GMS reconnect broadcast |
| Aurogon setting changes | Restore this app's Aurogon rules |
| Nighttime game allowlist changes (only while that option is on) | Restore the GMS entry |
| Package added or updated | Protect a new FCM app; reapply the policy of a managed one |
| Package force-stopped or restarted | Clear `FLAG_STOPPED` for managed apps with Auto unstop |
| Boot, this app updated, Shizuku binder or permission available | Full pass, including the Greezer commands, which reset when `system_server` restarts |
| Screen unlocked | Check the settings above in case an event was missed |

If a pass cannot finish, for example because Shizuku is not running yet, it stays pending and is merged into the next event, so nothing is retried on a clock. The Shizuku binder arriving is such an event.

### Google Play services

The core fix preserves all existing entries in `Settings.System.MILLET_NO_RESTRICT_APP` and appends `com.google.android.gms` and this app's package only when missing.

A full pass also issues the equivalent of `dumpsys greezer IM GMS disable` and `dumpsys greezer LM add com.google.android.gms`. A failure of either Greezer command is reported as unavailable and the pass stays pending for the next event.

**FCM Connection Protection** is on by default. When GMS had to be added back to the no-restrict list, it sends a targeted `com.google.android.intent.action.GCM_RECONNECT` broadcast so CN Google Play services builds reconnect promptly. There is no periodic socket probe. Turning the option off does not disable the `MILLET_NO_RESTRICT_APP` repair.

**Nighttime FCM protection (HyperOS 4)** is experimental and off by default. It addresses an additional nighttime freeze reproduced on a China HyperOS 4 build even with Xiaomi's older nighttime battery saving disabled. It maintains GMS in Xiaomi's shared game predownload exemption list, keeping deep Doze enabled. The exemption applies whenever present, including daytime, and may increase battery use.

While the option is on, the service watches that list and repairs it when Xiaomi's download components clear it. A repair signals the framework and sends owner-GMS thaw and reconnect broadcasts, even if FCM Connection Protection is off. Other entries are retained. Turning the option off removes GMS only if this app introduced it; preexisting GMS is preserved. Cleanup waits for Shizuku if unavailable.

GMS has separate UIDs and transport state in different Android profiles. The shared package exemption can cover clone GMS, but the recovery broadcasts target owner GMS. Immediate thaw of already-frozen clone GMS, overnight delivery, battery cost, and reboot/OTA behavior remain unverified. See the [HyperOS 4 nighttime investigation](docs/xiaomi-hyperos4-nighttime-fcm-investigation.md) for evidence and limits.

### Managed apps

Every non-system package that declares a receiver for `com.google.android.c2dm.intent.RECEIVE` is enabled by default, whether it was installed before this app or later. Each enabled app has four independent controls:

- Aurogon FCM protection: permits the FCM Intent through Xiaomi's broadcast control so it can be delivered to the app, including when delivery requires starting the app.
- Auto unstop (Android 16+): clears `FLAG_STOPPED` when the app is installed, updated, or force-stopped so it remains eligible to be started by FCM; it does not launch the app itself.
- HyperOS Autostart: permits the process start when FCM delivery targets an app with no running process, such as after the process has been killed. In that case it works together with the Aurogon rule. It manages both Xiaomi Autostart AppOps.
- AOSP battery optimization: mainly lets users suppress an app's background activity while keeping FCM unaffected. It applies Unrestricted, Optimized, or Restricted to the selected Android profiles. It is off by default.

A newly found app starts with Aurogon, Auto unstop, and Autostart management on, and battery management off.

Turning an app off in the list is remembered: it is never re-enabled automatically, even when the app is updated. Disabling an app removes it from the managed Aurogon and Auto unstop sets while retaining its saved choices. If battery management was enabled, the app is changed to **Optimized** once. Its Autostart state is left unchanged.

**Apply now** processes every enabled app. Settings that you change in the list apply immediately.

Autostart and battery policies target the Android profiles selected in the app; the owner and XSpace profiles are selected by default. Android's unrestricted device-idle allowlist is package-wide, so Unrestricted and Optimized changes are not isolated per profile.

## Diagnostics

Open **Diagnostics** at the bottom of the main screen to inspect, refresh, copy, or clear session logs covering Shizuku connections, guard events, privileged operations, and FCM repairs. The files are stored under `/storage/emulated/0/Android/data/net.extrawdw.apps.miuisucks.powerkeeper/files/logs/`. Logcat entries use the `PowerKeeperFix` prefix.

## Technical investigation

- [Xiaomi HyperOS GMS, FCM, and Greezer investigation](docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md)
- [PowerKeeper `MILLET_NO_RESTRICT_APP` rewrite investigation](docs/xiaomi-millet-no-restrict-app-rewrite-investigation.md)
- [HyperOS 4 nighttime GMS freezing and non-root workaround](docs/xiaomi-hyperos4-nighttime-fcm-investigation.md)

## Build

Open the project in Android Studio and run the `app` configuration.
