# SRML Mobile Save Editor

Android save editor for Slime Rancher Mobile. The first milestone is a safe Shizuku access check and save discovery flow.

## Current alpha

- Shizuku connection and permission status
- Checks the game's external save directory
- Read-only listing; no original save is modified
- Save-format research notes

Currency and inventory editing are intentionally not enabled until their binary fields are verified with controlled differential save samples.

## Build

GitHub Actions builds the debug APK on pushes to `main` and manual workflow dispatch.
