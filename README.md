# Orbit

Orbit is a nearby sharing app for Android. Discover another Orbit device, choose files or text, and review each incoming transfer before accepting it.

## Features

- Find and connect to nearby Orbit devices.
- Send one or more files, or share text and links.
- Review the sender, file names, sizes, and text preview before accepting an incoming transfer.
- Track transfer progress, speed, and estimated time, with pause, resume, and cancel controls.
- See recent devices for repeat sends and open the received-files location in `Downloads/Orbit`.
- See when your device is visible and ready to receive while nearby discovery and advertising are active.

## Receiving a transfer

Keep Orbit open with nearby access enabled. The home screen shows when your device is visible and ready. When someone sends something, Orbit opens a request with the sender and shared items; tap **Accept & receive** to begin or **Decline** to dismiss it. The incoming notification also opens Orbit so you can review the request.

## Install

Download the latest APK from [GitHub Releases](https://github.com/samielmadani/Orbit/releases), install it, and grant the nearby-device permissions when prompted. Orbit requires Android 8.0 (API 26) or newer.

## Build

Open the project in Android Studio or run:

```powershell
.\gradlew.bat :app:assembleDebug
```