# Feature status and limitations

This page describes the code in this repository's `0.1-dev` Android demo. It distinguishes implemented app paths from operating-system permissions and unimplemented capabilities.

## Implemented paths

| Area | Current behavior | Requirements / limits |
|---|---|---|
| Text chat | Accepts typed text and routes recognized local commands before sending other questions to Gemini. | A Gemini API key is required for general AI answers. Without one, local text commands may still run. |
| Voice | Starts a single Android speech-recognition session from **Hold to talk**, then displays/speaks the result. | Microphone permission, speech-recognition service, and a configured Gemini key are required by the current voice path. This is not an always-listening wake-word detector. |
| Spoken output | Android text-to-speech reads assistant responses when enabled. **Stop** cancels current speech/listening. | Depends on Android TTS voice availability and the app's speak setting. |
| App launch | Opens apps found by the installed-app lookup. | Android may show its own app/permission restrictions. |
| Calls and SMS | Uses Android call/SMS APIs and contact lookup. | Requires the corresponding Android permission and usable contact/phone details. SMS can send directly when permission is granted; confirm recipient and message before use. |
| Weather | Requests current weather through the app's network path. | Network access and a usable location/city setting are needed. |
| Floating orb | A foreground service draws the optional overlay. | Requires overlay authorization; Android foreground-service and notification rules apply. |
| Notification listener | Can receive notification content after the user enables the listener in Android Settings. | Must be enabled by the device owner. Notification data is sensitive; review the listener implementation before relying on it. |
| Recent conversation/settings | Stores app preferences and a short conversation history in app-private storage. | App-private storage is not the same as encrypted backup or end-to-end encrypted memory. |

## Not implemented in this repository

- A trained, locally validated “Hello Akansha” / “Hey Akansha” wake-word model.
- Continuous background conversation, interruption-aware duplex speech, or guaranteed lock-screen replies.
- Semantic/vector memory, a multi-step task planner, Windows desktop companion, or PC tool bridge.
- General screen understanding or arbitrary app tapping/swiping.
- Automatic replies to incoming notifications. The notification listener does not mean messages are automatically interpreted or sent.
- An autonomous coding agent or self-healing build loop.

## Security and privacy notes

- The app requests sensitive Android capabilities such as microphone, calls, SMS, contacts, location, and notification access. Grant only what you need and revoke access in Android Settings when finished.
- The current demo stores the Gemini key in app-private preferences; the key is not protected by the hardware-backed Android Keystore in this version. Avoid using a key with broad privileges and do not publish it.
- Calls and messages have real-world effects. Use the app only after checking the recipient and action. Android's permission prompts are part of the authorization boundary.
- This repository intentionally excludes signing keys and generated build directories. Keep your signing key backed up securely and outside Git.

## Verification status

The APK build script's compile, packaging, alignment, and signature checks are automated in `tools/build-apk.ps1`. Those checks do not prove every feature works on every phone. Microphone, notification access, calls, SMS, overlay behavior, Gemini credentials, and device-specific background restrictions require runtime testing on a device.
