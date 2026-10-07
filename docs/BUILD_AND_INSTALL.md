# Build and install

## Toolchain

The Windows PowerShell script uses an Android SDK installed at `%LOCALAPPDATA%\Android\Sdk` and a JDK on `PATH`. It chooses the highest installed Android build-tools and platform versions unless `-SdkRoot` or `-Platform` is supplied.

## Build

Run from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\build-apk.ps1 `
  -ProjectDir .\app `
  -Out .\build\akanshaa-v0.1.apk `
  -BuildDir .\build-tools-out
```

The script compiles resources, links the manifest, compiles Java, converts classes to DEX, injects `classes.dex`, aligns and signs the APK, and verifies its contents/signature. It creates/reuses a debug signing key in the current user's profile if needed. Do not move that key into the repository.

The checked-in APK is `release/Akanshaa-v0.1-dev.apk`. The build script does not update that checked-in copy automatically; copy a newly verified build there only when you intend to publish a new APK.

## Install over USB

Enable USB debugging, connect/unlock the Android device, and accept its debugging authorization prompt. Then:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb install -r .\release\Akanshaa-v0.1-dev.apk
```

If Android reports a signing-certificate mismatch, the installed app was signed with a different key. Do not uninstall blindly if local app data matters; back it up first. `adb install -r` preserves app data when the signing key and package identity match.

## First-run setup

1. Install and launch Akanshaa.
2. Add your own Gemini API key, then use **Test key and list models**.
3. Grant Android runtime permissions only for features you plan to use.
4. Enable overlay and notification access separately in Android Settings if using those features.
5. Test each action with a harmless request before using it for real calls or messages.

The app has no custom wake-word model in this demo. Voice entry is initiated with the in-app **Hold to talk** control.
