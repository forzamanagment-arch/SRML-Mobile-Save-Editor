# FusionCore compatibility audit for Slime Rancher Mobile

Date: 2026-11-26
Target game: Slime Rancher Mobile 1.1.1103 (versionCode 609)
Target package: com.playdigious.slimerancher
SRML repository baseline: 6df033ee2e434136baa11903943e8d41d6e84c72
FusionCore upstream baseline: bf4c69d9d4724e4eff058664a5be0a1d9cfd4e61

## Decision

Use FusionCore as the candidate foundation for the runtime layer. Do not write a second Android IL2CPP bootstrap from scratch. This is an architectural decision, not a compatibility confirmation. The target game has not yet been successfully launched through FusionCore.

## Confirmed target facts

- Android package: com.playdigious.slimerancher.
- Version: 1.1.1103, versionCode 609.
- Minimum Android SDK: 29; target SDK: 36.
- ABI: arm64-v8a.
- Unity scripting backend: IL2CPP.
- global-metadata.dat is approximately 10.66 MB; previously analyzed metadata version is 39.
- APK contains assets/bin/Data/data.unity3d, datapack.unity3d, sharedassets*.resource, and Managed/Metadata/global-metadata.dat.
- APK launcher Activity is obfuscated: com.m8bjh1b7.w1cn8q.F587w173. JADX shows that it extends Android Activity, performs the game's native signature check, and then launches an original launcher Activity resolved by the game's native library. It is not a plain UnityPlayerActivity entry point.
- The game includes libmain.so, libunity.so, libil2cpp.so, and an app-specific native library libzqc63dn9mh.so.

## Confirmed FusionCore behavior

- FusionCore is a standalone Android application/framework, not a small library dependency that can simply be added to the current app.
- It scans installed launcher apps for IL2CPP Unity games and launches a selected target through its own BootstrapActivity and StubActivity flow.
- It creates a target package context and target class loader, then uses a wrapped Activity/application context for the target game.
- It has a native runtime component and a custom BepInEx Fusion + .NET 10 runtime. It is not standard desktop BepInEx/MelonLoader compatibility.
- It detects Unity version from Unity data files. Its current lookup checks globalgamemanagers, data.unity3d (offset 0x12), and mainData.
- It requires Android's MANAGE_EXTERNAL_STORAGE / all-files-access setting for its normal workflow. The app's Shizuku permission is not a substitute for this declared FusionCore requirement.
- It copies APK assets/ entries into /sdcard/FusionCore/<package>/Assets.
- Its context wrapper redirects the target game's external-files directory to /sdcard/FusionCore/<package>/PersistentData; several internal app-data paths are also routed through the FusionCore host context.
- Upstream license is GPL-3.0. If we fork, modify, and distribute a combined derivative, source-code and license obligations must be respected. The exact boundary between a combined work and separate aggregated apps needs review before release.

## Compatibility matrix

| Area | Status | Notes |
|---|---|---|
| Android API | Likely compatible | FusionCore minimum API 24; upstream README says testing was on API 30+. Target device is Android 16. |
| ABI | Compatible on paper | Both target and FusionCore build target arm64-v8a. |
| Unity version | Identified | The APK header probe returns Unity 6000.3.0f1 from data.unity3d at offset 0x12, matching FusionCore's detection code. The BepInEx Unity library index lists the corresponding 6000.3.0.zip; runtime compatibility still needs testing. |
| IL2CPP metadata | Needs runtime validation | Metadata v39 is known, but that alone does not prove the current BepInEx Fusion/interop stack supports this game build. |
| Launcher Activity | Unverified | The game uses an obfuscated Java Activity and a native-resolved second launcher. FusionCore's dynamic Activity loader may handle it, but only a real launch test can confirm. |
| Signature check | Must remain intact | The game's Java/native launcher checks its installed package signature. We will not bypass this check. Test whether the official installed package context is preserved by FusionCore. |
| Native libraries | Unverified | FusionCore routes Unity/IL2CPP library loading. The target also has an app-specific native library that must continue to load normally. |
| Save paths | Unverified | FusionCore redirects external-files storage to its own PersistentData folder. Do not assume the old Android/data/<package>/files/pt_saves path remains the active path. Confirm by observing a test save created by the game. |
| Overlay / IPC | Not implemented | SRML Game Tools still needs a supported communication channel to the runtime. |
| DLL mods | Not validated | Must first prove the runtime starts and loads a harmless diagnostic plugin before implementing game modifications. |

## Integration strategy

1. Establish exact Unity version and check whether FusionCore's Unity base-library/runtime dependencies cover it.
2. Build an unmodified upstream FusionCore baseline separately. Do not yet merge it into the SRML app.
3. Test the target game through the baseline using a disposable test profile and a backed-up save. Keep the official game installation untouched.
4. Verify startup, first scene, save creation/reload, and clean exit. Capture logs for each stage.
5. Only after a successful launch decide whether to embed FusionCore as a Gradle library/module or keep a separate runtime companion with an explicit IPC contract. The current FusionCore project uses an Android application module, Kotlin DSL, native CMake targets, bundled runtime assets and its own Activities/resources, so embedding requires a deliberate port rather than a one-line dependency.
6. Add SRML's managed API and plugin manager after the runtime baseline passes. Add resource overrides through a separate SRML resource provider so future boot backgrounds can be replaced without hard-coding them into the menu.

## Immediate blockers / next tests

- Unity version probe completed: 6000.3.0f1. The corresponding 6000.3.0 Unity base-library package is listed by the BepInEx Unity library repository; verify runtime loading during the launch test.
- Verify upstream FusionCore builds with its current Gradle/AGP/NDK requirements.
- Confirm target launch Activity, package identity/signature behavior, and game-specific native library loading.
- Determine the actual save path while running through FusionCore before any save migration is implemented.
- No APK patcher, injection bypass, signature bypass, or live game-value bridge is implemented by this audit.

## Build-system integration note

The existing SRML app currently uses Android Gradle Plugin 8.7.3, Kotlin Android plugin 2.0.21, compileSdk 35, and a Gradle 8.9 CI build. The pinned FusionCore source uses Android Gradle Plugin 9.3.2, Kotlin Android plugin 2.4.10, compileSdk 37, and Gradle wrapper 9.7.1. These projects cannot be combined safely by copying the module folder alone. Before embedding, the build toolchain must be aligned and the FusionCore application module converted or ported into the SRML app architecture. A separate baseline build is being used to distinguish upstream build issues from integration issues.

