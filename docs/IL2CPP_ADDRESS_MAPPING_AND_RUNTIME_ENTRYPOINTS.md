# SRML Mobile: IL2CPP address mapping and runtime entry points

Analysis date: 2026-11-26  
Target ABI: Android arm64-v8a / AArch64  
Input library: `libil2cpp.so` from the uploaded `FilesSRML.zip` release archive  
SHA-256: `509dd7cc9cf64bc7d2e54093504c20b17d3e1b60cafdb59c2b6fe1d1e1a72084`

## 1. ELF mapping: confirmed

The library is an ELF64 little-endian AArch64 shared object (`ET_DYN`). The executable PT_LOAD segment is:

| Property | Value |
|---|---:|
| File offset | `0x1F780C0` |
| Virtual address | `0x1F7C0C0` |
| File size | `0x4C144D0` |
| Memory size | `0x4C144D0` |
| Flags | `R E` |
| Alignment | `0x4000` |

The segment has a `0x4000` difference between `p_vaddr` and `p_offset`. For an address inside this segment:

- ELF virtual address (the address emitted by the analyzer) = `A`
- corresponding file offset = `0x1F780C0 + (A - 0x1F7C0C0)`
- runtime address = ELF load bias + `A`

The load bias is not known from the APK. It must be obtained from the actual loaded module inside the game process (for example, from the process's ELF program-header mapping). Do not treat an analyzer address as an absolute process pointer.

### Cross-check of all resolved functions

All **172,643 / 172,643** function addresses in `unity_meta.json` fall inside this executable PT_LOAD segment.

- Lowest resolved address: `0x2BDD DD4` (without spacing: `0x2BDDDD4`)
- Highest resolved address: `0x6B8DE70`
- No resolved function address fell outside the executable segment.

This strongly supports interpreting the analyzer's `addr` values as ELF virtual addresses for this exact library build. It does not prove that the same addresses apply to another game version or another `libil2cpp.so`.

## 2. Address examples

| Method | ELF VA | File offset |
|---|---:|---:|
| `PlayerState$$SetHealth` | `0x335DB2C` | `0x3359B2C` |
| `PlayerState$$SetEnergy` | `0x335DC18` | `0x3359C18` |
| `PlayerState$$GetCurrHealth` | `0x3362FE8` | `0x335EFE8` |
| `PlayerState$$GetCurrEnergy` | `0x334ADB4` | `0x3346DB4` |
| `SceneContext$$get_PlayerState` | `0x33DC1E4` | `0x33D81E4` |
| `SceneContext$$get_GameModel` | `0x33DC97C` | `0x33D897C` |
| `GameModel$$GetPlayerModel` | `0x3299C1C` | `0x3295C1C` |
| `PlayerModel$$SetHealth` | `0x32D89B4` | `0x32D49B4` |
| `PlayerModel$$SetEnergy` | `0x32D8248` | `0x32D4248` |
| `SRSingleton`1$$get_Instance` | `0x51D6DE4` | `0x51D2DE4` |

## 3. Player state: what disassembly confirms

### Health

`PlayerState$$SetHealth` at `0x335DB2C`:

- Receives a `PlayerState*` in X0 and an integer value in W1.
- Reads the model reference from `PlayerState + 0x48` (decimal 72).
- Converts the integer to a single-precision float with `SCVTF`.
- Calls `MonomiPark.SlimeRancher.DataModel.PlayerModel$$SetHealth` at `0x32D89B4`.

`PlayerState$$GetCurrHealth` at `0x3362FE8` reads a float from the model at offset `0x64` (decimal 100), then converts it to an integer through a helper call. `GetMaxHealth` reads an integer from model offset `0xA8` (decimal 168).

### Energy

`PlayerState$$SetEnergy` at `0x335DC18` follows the same pattern:

- `PlayerState + 0x48` supplies the model reference.
- The integer argument is converted to float.
- It calls `PlayerModel$$SetEnergy` at `0x32D8248`.

`GetCurrEnergy` reads a float at model offset `0x60` (decimal 96), then converts it to an integer. `GetMaxEnergy` reads an integer at offset `0xAC` (decimal 172).

These are observations about the compiled code. The exact semantic type of every hidden argument to the model setters is not resolved by the metadata parser, so do not construct raw calls from guessed prototypes.

### Other player values

The disassembly also confirms the model pointer at `PlayerState + 0x48` is used by the currency and key getters:

- Currency: model offset `0x6C` (108), read as a 32-bit integer.
- Keys: model offset `0x7C` (124), read as a 32-bit integer.

These offsets are useful reverse-engineering evidence, not a stable public API. They may change with a game update.

## 4. SceneContext and GameModel access path

The following accessors are confirmed by their instructions:

- `SceneContext$$get_PlayerState` reads a reference from `SceneContext + 0x28` (40).
- `SceneContext$$get_GameModel` reads a reference from `SceneContext + 0xB8` (184).
- `GameModel$$GetPlayerModel` is present in the metadata at `0x3299C1C`.

This gives a useful object relationship once a valid `SceneContext*` has already been obtained:

`SceneContext → PlayerState`  
`SceneContext → GameModel → PlayerModel`

The unresolved part is the first pointer: how to safely obtain the currently active `SceneContext*` in the running game. The generic `SRSingleton<T>.get_Instance` method exists, but the current static evidence does not prove that its relevant closed generic instance is `SRSingleton<SceneContext>`, nor that calling it from an external process is possible. `SceneContext` also exposes `Awake`, `Register`, and `Deregister`, which should be traced next to understand lifecycle and registration.

## 5. ModDirector is not proof of plugin loading

`SceneContext$$get_ModDirector` returns the reference stored at `SceneContext + 0x58` (88). This confirms an in-game object named `ModDirector`, but does not establish support for third-party DLL loading. Its API and call sites must be examined before assigning it that role.

## 6. Runtime Bridge implications

The static analysis has now answered two important questions:

1. The native method addresses are consistently mapped to the executable ELF segment.
2. The game has a real object chain connecting SceneContext, PlayerState, GameModel and PlayerModel.

It has **not** answered whether a separate Android app can safely reach those objects. A normal app process cannot dereference another app's pointers or directly invoke its IL2CPP methods. The eventual bridge needs a component executing inside the game process or a supported in-process integration point.

Recommended architecture investigation order:

1. Trace SceneContext registration and lifetime to identify the supported route to the active context.
2. Inspect the game's own bootstrap and native library initialization to determine whether an extension point exists.
3. Verify whether the app package or game runtime exposes a legitimate plugin/module mechanism. Do not assume one exists.
4. Only after a legitimate in-process entry point is established, design a narrow bridge API with version checks, null/lifetime checks, and safe failure behavior.
5. Keep the existing save editor as a separate offline feature; it does not depend on a runtime bridge.

## 7. Evidence limits

- All results refer to the exact `libil2cpp.so` SHA-256 listed above.
- `global-metadata.dat` format version 39 is not the Unity engine version.
- Several generated class field layouts and method argument types remain incomplete. The disassembly evidence above is preferred where it directly shows register use and memory offsets.
- No live process was inspected, no runtime address was measured, and no function was invoked.
- This report does not claim that runtime modification or third-party DLL loading is already working.

## 8. SceneContext lifecycle: additional disassembly pass

A focused pass over `SceneContext$$Awake`, `Start`, `Register`, `Deregister`, and the generic `SRSingleton<T>` methods did not reveal a direct `SceneContext.Instance` accessor.

New observations:

- `SceneContext$$get_PlayerState`, `get_GameModel`, and `get_ModDirector` are ordinary instance property accessors. Each receives a `SceneContext*` in X0 and returns a reference stored in that object.
- `SceneContext$$get_ModDirector` reads the reference at `SceneContext + 0x58` (88).
- `SceneContext$$Register` and `Deregister` operate on a registry-like member at `SceneContext + 0x100` (256) and accept an object argument.
- The call graph shows `WakeUpDestination$$Awake` calling `SceneContext$$Register`, and `WakeUpDestination$$OnDestroy` calling `SceneContext$$Deregister`. This is consistent with scene objects registering/unregistering themselves with a context-owned registry. It is not evidence that SceneContext is a global singleton.
- `SceneContext$$OnApplicationQuit` calls `OnSessionEnded`.
- `SceneContext$$Start` contains an async state-machine setup; the method is not a simple global initialization accessor.
- `SRSingleton<T>.get_Instance` exists as a generic method, but there is no direct proof in the available evidence that its relevant closed generic type is `SceneContext`.

### Consequence

The earlier object chain remains valid **after a real SceneContext instance is obtained**, but the static analysis has not yet found a safe global entry point for obtaining that instance. We should not hardcode a guessed static pointer, use the generic singleton blindly, or infer the context from an unrelated registered component.

The next useful static target is the code that constructs/owns the active scene context and the code that populates its PlayerState/GameModel fields. That requires tracing the relevant constructors, setters, scene bootstrap code, and serialized component relationships. The current analyzer's class/field layout output is not reliable enough to infer these relationships from field names alone.
