# Unlock FPS

[Türkçe](README.md) · English

Unlock FPS requests a target display refresh rate for games and apps on MIUI and HyperOS through Xiaomi’s PowerKeeper. It runs without root. Actual frame rates depend on the game’s frame cap and device support.

## Installation

Requires Android 8.0 or later and Xiaomi’s PowerKeeper component. [Download UnlockFPS.apk from the latest release](https://github.com/JrSpatt/UnlockFPS/releases/latest).

## Usage

1. Choose a target refresh rate. The picker lists the rates reported by the display.
2. Select the apps to include in the profile. System apps appear in the **System** tab. Long-press an app to set an individual rate.
3. Tap **Apply**. The home-screen widget can resend the last saved profile.

With automatic reapply enabled, the profile is sent every 15 seconds while the screen is on. **Beast Mode** requests the highest supported refresh rate for every launchable app, including system apps. In this mode, the reapply interval is 60 seconds.

## Build

GitHub Actions uses JDK 17, Android SDK 35 and Build Tools 35.0.0. Signed releases use the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Pushing a `v*` tag runs the **Build APK** workflow and attaches the APK to the GitHub release.

Local test builds use the Android SDK at `ANDROID_HOME`.

```sh
ALLOW_DEBUG_KEY=1 bash tools/build.sh
```

The APK is written to `build/UnlockFPS.apk`.

[Changelog](CHANGELOG.md)
