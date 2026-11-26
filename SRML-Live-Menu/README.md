# SRML Live Menu

Standalone Android prototype for testing the floating overlay foundation of SRML Game Tools.

## Current prototype
- Overlay permission flow
- Foreground service with persistent notification
- Floating launcher bubble
- Draggable bubble and panel header
- Player / Inventory / World sections
- Cheat rows explicitly marked **NOT CONNECTED**

This build does **not** modify Slime Rancher Mobile values. It validates the overlay lifecycle and UI only. Runtime integration is a separate future milestone.

## Build
Run from this directory:

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
