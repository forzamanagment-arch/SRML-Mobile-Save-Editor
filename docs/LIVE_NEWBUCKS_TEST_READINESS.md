# Live Newbucks Test: Runtime Bridge Readiness

Target game: Slime Rancher Mobile 1.1.1103
Package: `com.playdigious.slimerancher`
Architecture: Android arm64-v8a, IL2CPP metadata v39

## Current result

The game-side data path is identified, but there is not yet a working or verified way for SRML Game Tools to execute code inside the game's process. Therefore live Newbucks editing is **not ready to test**. The companion app's overlay is only a UI until an in-process bridge exists.

## Confirmed game-side path

- `SceneContext.get_PlayerState`: ELF VA `0x33DC1E4`; reads the reference at `SceneContext + 0x28`.
- `SceneContext.get_GameModel`: ELF VA `0x33DC97C`; reads `SceneContext + 0xB8`.
- `SceneContext.get_Player`: ELF VA `0x33DCBB0`; reads `SceneContext + 0xE0`.
- `SceneContext.get_IsGameFullyLoaded`: ELF VA `0x33DDC58`; reads the boolean at `SceneContext + 0x128`.
- `PlayerState.SetModel`: ELF VA `0x335FED0`; stores a `PlayerModel*` at `PlayerState + 0x48`.
- `GameModel.GetPlayerModel`: ELF VA `0x3299C1C`.
- `PlayerModel.SetCurrency`: ELF VA `0x32D8930`.
- `PlayerState.GetCurrency`: ELF VA `0x335D8A8`.

All addresses above are ELF virtual addresses, not absolute process addresses. ASLR/load bias must be accounted for by any legitimate in-process native component.

## New findings from the metadata artifact

- `SceneContext.Register` and `SceneContext.Deregister` interact with a shared registry object. This is registration infrastructure, not a public static accessor that an external app can call.
- `SceneContext.set_Player` stores the player reference at offset `0xE0`, performs registration-related work, and notifies `InputDirector.NoteNewPlayer`.
- `SceneContext.Start` launches asynchronous level initialization. The game-loaded flag is set later through the startup flow.
- The game's `ModDirector` is an internal gameplay modifier controller (for example, slime hunger/count factors and other game rules). It is **not evidence of an SRML-style external plugin loader**. Its inspected `IsModActive` implementation currently returns false.
- Unity's Java `UnityPlayer` layer exposes a `UnitySendMessage` path for sending messages to an existing GameObject/method. This is not a generic way for a separate companion application to execute new game code or obtain IL2CPP object references.

## Newbucks write semantics

- `PlayerModel + 0x6C`: current Newbucks balance.
- `PlayerModel + 0x70`: lifetime accumulated Newbucks.
- `PlayerModel.SetCurrency` writes the current balance directly.
- `PlayerState.AddCurrency` has additional side effects, including achievement/stat updates and a popup. It should not be treated as an equivalent setter.

First test should change only the current balance, then verify the in-game HUD, `PlayerState.GetCurrency`, and persistence after a normal save/reload. Lifetime totals should remain unchanged unless a separate test specifically targets them.

## Blocking issue

Android process isolation prevents the companion app from directly calling exported `il2cpp_*` functions or dereferencing game objects. Shizuku and an overlay permission do not turn a separate process into part of the Unity process.

The inspected APK startup wrapper performs publisher-signature validation. This project will not rely on bypassing that validation or weakening the game's protection. No supported plugin/mod-loader entry point has been verified in the inspected APK.

## Requirements before a live test

1. Identify a supported, authorized way to host a bridge inside the game process (for example, an official plugin/mod interface or a controlled test build supplied for development).
2. In that bridge, attach to the existing IL2CPP domain; do not call `il2cpp_init` a second time.
3. Resolve the live `SceneContext` only after the scene is initialized, then validate the `PlayerState -> PlayerModel` chain and object lifetimes.
4. Dispatch game-object/model operations on the appropriate Unity/game thread.
5. Expose a narrow IPC command interface to SRML Game Tools, with read-back and error reporting.
6. Run the Newbucks test with a backup and verify HUD, getter value, and save/reload.

## Status

- Save-file Newbucks editing: implemented and previously tested.
- Game-side Newbucks methods/fields: identified by static analysis.
- SceneContext field access path: identified.
- In-process bridge: not implemented.
- Companion-to-game IPC: not implemented.
- Live Newbucks test: not yet performed.
