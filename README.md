# SRML Mobile Save Editor

Android save editor for Slime Rancher Mobile.

## Current diagnostic alpha

- Shizuku Binder and permission checks
- Read-only check of the game's save directory
- Persistent internal diagnostic log with timestamps and stack traces
- Log rotation (current + previous log, 512 KB threshold)
- In-app log viewer and Android share-sheet export
- No original save is modified

## Diagnostics captured

- App version, Android API level and device model
- Shizuku Binder connected/disconnected events
- Permission request and grant/denial result
- Save directory diagnostic result and shell exit code
- Shizuku process exceptions and Java stack traces

The app does not log save-file contents or modify save files.

## Build

GitHub Actions builds the debug APK on pushes to `main` and manual workflow dispatch.
