# SRML Game Tools: Live Cheat Menu Bridge Plan

## Scope clarification

This project is a **single-game cheat/mod menu for Slime Rancher Mobile**, not a general-purpose mod loader. The first target is one verified live operation: read and set the current Newbucks balance. Later commands can cover health, energy, keys and inventory.

The in-game component described here is only a small runtime connector for SRML Game Tools. It is not intended to discover, install, or execute arbitrary third-party mods.

## What static analysis confirms

Target APK: Slime Rancher Mobile 1.1.1103
Package: `com.playdigious.slimerancher`
ABI: arm64-v8a
IL2CPP metadata: v39

Known game-side chain:
- `SceneContext.get_PlayerState` at ELF VA `0x33DC1E4`, field offset `+0x28`.
- `PlayerState.SetModel` at ELF VA `0x335FED0`, stores PlayerModel at `PlayerState + 0x48`.
- `SceneContext.get_GameModel` at ELF VA `0x33DC97C`, field offset `+0xB8`.
- `GameModel.GetPlayerModel` at ELF VA `0x3299C1C`.
- `SceneContext.get_IsGameFullyLoaded` at ELF VA `0x33DDC58`, field offset `+0x128`.
- `PlayerModel.SetCurrency` at ELF VA `0x32D8930`.
- `PlayerState.GetCurrency` at ELF VA `0x335D8A8`.

The SceneContext accessors are instance methods. Static analysis has not yet established a stable public/static singleton accessor for SceneContext. GameContext does have a duplicate-instance check using a static reference at ELF VA `0x6EDD000`, but that is a GameContext singleton, not proof that it directly exposes the active SceneContext.

All addresses are ELF virtual addresses. They are not absolute RAM addresses and must not be used as if ASLR did not exist.

## Runtime connector vs mod loader

Unity's official Android native-plugin documentation describes shared libraries packaged into a Unity app build. For IL2CPP, managed DLLs are not dynamically loaded as ordinary runtime plugins. This does not provide an aftermarket plugin slot in an already published APK.

Therefore:
- We do not need a generic mod manager/loader for the Newbucks test.
- A one-purpose in-game connector would be sufficient.
- A connector must still execute inside the game's process to access IL2CPP objects.
- The inspected retail APK has no verified supported external connector interface.
- We will not bypass the publisher's signature/tamper validation.

## Proposed minimal test design

### Game-side connector responsibilities
1. Confirm the existing IL2CPP runtime is initialized; never call `il2cpp_init` again.
2. Wait until the game reports `SceneContext.IsGameFullyLoaded == true`.
3. Resolve the active SceneContext using a verified in-process object-discovery route.
4. Validate the `SceneContext -> PlayerState -> PlayerModel` chain.
5. Expose only two first-test operations: `GET_CURRENCY` and `SET_CURRENCY(value)`.
6. Run model access on the Unity/game thread and return explicit success/error plus the read-back balance.

### SRML Game Tools responsibilities
1. Keep the existing overlay UI and controls.
2. Connect to the game-side connector over a local, app-private IPC channel.
3. Display connection state, current balance, pending state, and the verified result.
4. Never show a success state until the connector confirms the write and read-back.
5. Keep save-file editing as a separate offline feature.

### First test acceptance criteria
- Game is running and fully loaded.
- Connector reports a valid PlayerState and PlayerModel.
- Read operation matches the visible in-game balance.
- Set operation changes only the current balance field.
- Read-back and HUD agree.
- A normal save/reload preserves the new balance.
- Lifetime accumulated currency remains unchanged.

## Current blockers

1. **No verified way to add or execute the one-purpose connector inside the retail game process without bypassing publisher validation.**
2. No verified active SceneContext discovery path yet.
3. No IPC implementation between the overlay app and an in-game endpoint.
4. No live write/read-back test has been performed.

## Next research tasks

1. Inspect SceneContext registration and the global registry to determine whether there is an ordinary in-game lookup path for the active SceneContext. Treat registry references as unverified until their types and lifecycle are resolved.
2. Inspect SystemContext/GameContext initialization for an existing, documented callback or extension seam that can host a connector without modifying protection checks.
3. In parallel, implement and test the companion-side IPC client against a local mock endpoint. This validates the UI/protocol independently, but does **not** count as a live game test.
4. Reassess feasibility after the above. If no legitimate in-process hosting path exists for the retail APK, report that as a hard blocker rather than disguising it as a completed bridge.

## Status

- Offline save Newbucks editing: implemented/tested previously.
- IL2CPP Newbucks methods and field semantics: identified.
- SceneContext/PlayerState/PlayerModel relationship: identified.
- Cheat menu overlay UI: prototype exists.
- In-game connector: not implemented.
- Companion IPC: not implemented.
- Live Newbucks test: not yet performed.
