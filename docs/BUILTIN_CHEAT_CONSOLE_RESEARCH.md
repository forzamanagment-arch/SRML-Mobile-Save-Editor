# Built-in Cheat Console Research

Static IL2CPP metadata contains game classes named `CheatConsole`, `CheatConsoleUI`, `CheatMenuItem`, and `CheatMenuItemSelector`. This is a more promising lead for a single-game cheat menu than treating the project as a general mod loader.

Relevant discovered methods:
- `CheatConsole.ToggleCheatConsole`
- `CheatConsole.Close`
- `CheatConsoleUI.SelectCommand`
- `CheatMenuItemSelector.AddCheat`
- `CheatMenuItemSelector.AddMenuManagementButtons`
- `CheatMenuItemSelector.GetItem`
- `CheatMenuItemSelector.RenderItem`
- `CheatMenuItemSelector.Dump`
- `CheatMenuItemSelector.DumpList`
- `CheatMenuItemSelector.GenerateFavourites`
- `CheatMenuItemSelector.AddToFavorite`
- `CheatMenuItem.get_IsActive`

This discovery is not yet proof that the console is enabled in the mobile retail build, reachable from normal UI, or contains a Newbucks command. It may be a retained developer/debug subsystem. The next step is to inspect the full disassembly and references of these classes, determine how the console is activated, enumerate command names and their handlers, and search specifically for currency/health/energy commands.

If the existing console can safely invoke the game's own commands, it may provide a much shorter route to the first Newbucks test: the companion overlay could become a remote control for a built-in game feature, without building a general-purpose mod loader. Any such route must still respect the game's integrity checks and must be verified on the target version.


## Deeper result: the framework appears disabled in this retail build

Additional methods inspected:
- `OSG.BaseCheatManager.get_CanCheat` at ELF VA `0x33F7C80` returns the constant boolean `false`.
- `OSG.BaseCheatManager.OnEnable` calls `get_CanCheat`; when false, it disables the component and does not enable its input listener.
- `OSG.BaseCheatManager.set_Show` also checks `get_CanCheat` and refuses to show the menu when false.
- `OSG.CheatSettings.Setup` registers an assembly scanner and scans for cheat methods, so the framework's code and discovery machinery are present in the binary.
- `OSG.BaseCheatManager.CheckOpenMobileShortcuts` contains mobile shortcut handling, but that does not make the manager usable while `CanCheat` is false.
- `CheatConsole.ToggleCheatConsole` constructs the console UI and uses the OSG cheat manager. Its presence is not proof that the retail build allows it to open.

This means the built-in OSG cheat system is a real, compiled developer/debug framework, but its activation gate is closed in the inspected APK. We will not patch or bypass that gate. It is useful evidence about the game's internal cheat architecture, not a ready-made route for the live test.

## Effect on the SRML Game Tools plan

The project remains a single-game cheat menu, not a mod loader. The built-in cheat framework is not a supported bridge for the retail build because its own runtime gate disables it. The next feasible work remains:
1. continue examining ordinary, non-protection-related game lifecycle/extension points for a supported connector;
2. implement the companion app's IPC client and a mock endpoint so the UI protocol can be tested independently;
3. keep the live Newbucks test marked blocked until a legitimate in-process endpoint is available.


## Confirmed built-in command groups from the IL2CPP function index

The full function index from artifact `15121237036` confirms that the debug assembly contains actual command-provider classes, not only the console UI:

### `OSG.TimeScaleCheats`
Methods include:
- `SpeedUpTime`, `SlowDownTime`
- `SetTimeScale`, `Set`
- `Pause`, `Play`
- `CanPause`, `CanPlay`
- `ResetTime`
- presets `SetTimeTenth`, `SetTimeHalf`, `SetTimeOne`, `SetTimeTwo`, `SetTimeFive`, `SetTimeTen`, `SetTimeTwenty`
- `ShowTimeScale`, `ShowTimeHistory`, `ShowPauseCount`

### `OSG.QualitySettingsCheats`
Methods include `Select`, `LogCurrent`, `ShowSelectedQualityLevel`, and quality-parameter/name helpers.

### `OSG.DEBUG.CaptureScreenCheat`
Methods include screenshot capture, PNG output, texture capture, and screenshot cleanup/readback helpers.

### `OSG.Logger.LoggerCheats`
Methods include log submission and log preparation helpers.

The metadata also defines `OSG.CheatFunctionAttribute`, `OSG.CheatParameterProviderAttribute`, and `OSG.CheatMethodName`. `OSG.CheatSettings.Setup` registers `OSG.Core.AssemblyScanner`; `SetupAssemblies` calls `AppDomain.CurrentDomain.GetAssemblies()` and passes loaded assemblies through that scanner. `ProcessType` calls `OSG.CheatInfo.GetCheatInfos`. This strongly indicates an attribute/reflection-driven command catalogue, with typed parameter-provider support, rather than a hard-coded list of console strings.

The command framework is therefore genuinely extensible in design: commands are grouped by declaring type, can have parameter providers, and the menu supports favourites, repeatable commands, and custom GUI hooks. This is strong evidence for broad developer/debug customization.

## What is and is not confirmed for Slime Rancher Mobile

Confirmed:
- The game contains real developer cheat command implementations.
- Time control, pause/play, quality settings, screenshots, and logging commands exist in the compiled debug assembly.
- The framework includes a command attribute and parameter-provider attribute.
- The scanner discovers loaded assemblies and processes types into CheatInfo entries.
- The console UI and menu system are real, substantial implementations.

Not yet confirmed:
- Which of these commands are included in the final runtime catalogue on the retail mobile build.
- Whether any player-economy, health, energy, inventory, slime-spawn, unlock, or world-manipulation commands exist.
- Whether the framework's command scan completes in the retail build when its manager is disabled.
- Whether there is a supported way for SRML Game Tools to invoke these commands from outside the game.

Important: the existence of `OSG.CheatFunctionAttribute` and the scanner does not itself mean that arbitrary commands can be added from the companion app. The scanner scans assemblies already loaded in the game's AppDomain. The retail build still reports `CanCheat == false`, and we will not bypass that gate.

## Updated research priority

The next high-value task is to enumerate all types and methods marked with `OSG.CheatFunctionAttribute` and `OSG.CheatParameterProviderAttribute` in the compiled metadata, then map their command names, parameter types, categories, and handlers. This should answer whether there are ready-made player/gameplay cheats, rather than guessing from class names.


## Full command-framework inventory from the IL2CPP function/assembly index

The broader dump confirms the debug system is more capable than the four command-provider groups alone suggest.

### Parameter editor / call builder

The compiled `OSG.CallBuilder` includes dedicated input/selector paths for:
- `IntInput`, `UIntInput`
- `LongInput`, `ULongInput`
- `FloatInput`, `DoubleInput`
- `TextInput`
- `Vector2Input`, `Vector3Input`, `Vector4Input`
- `QuaternionInput`
- `CallBuilderBoolSelector`
- `EnumCallBuilder`
- `CallBuilderGameObjectSelector`
- generic type selectors and a `FromAttribute` provider mechanism

This is concrete evidence of a typed parameter UI, not just fixed buttons. The game-specific `CheatManager` class lives in `Assembly-CSharp.dll`, inherits the OSG manager interface (its vtable overrides the OSG manager hooks), and creates a `CustomCallBuilder`. That custom builder delegates type selection to the OSG call-builder system. This is a game-specific customization seam for the developer tool, but it does not prove that every arbitrary game method is callable.

### Menu customization features

The OSG manager exposes:
- custom GUI event hooks;
- category and cheat visibility filtering;
- pinned cheats and favourites;
- repeat-last-cheat behavior;
- menu scaling and saved scale;
- transparency controls;
- mobile shortcut handling;
- built-in console UI and command selector;
- logging and debug-message UI.

### Current command providers found by owner/type

The function index identifies these concrete provider classes inside `com.osg.debug.dll`:
1. `OSG.TimeScaleCheats`
2. `OSG.QualitySettingsCheats`
3. `OSG.Logger.LoggerCheats`
4. `OSG.DEBUG.CaptureScreenCheat`

The game assembly also has `CheatManager` and `CustomCallBuilder`, but the indexed methods for `CheatManager` are only `get_Instance`, `GetBuilder`, and its constructor. No indexed game-specific provider class for currency, health, energy, inventory, slime spawning, or unlocks has been confirmed so far.

### Important completeness limitation

The current `functions.jsonl` is a method/function index. It does not preserve the complete IL2CPP custom-attribute association table for every method. Therefore, a method-owner search is a strong inventory of obvious provider types, but it is **not yet a formal exhaustive list of every method carrying `OSG.CheatFunctionAttribute`**. The next exactness step is to parse the metadata custom-attribute tables (or use a metadata dump that preserves method attributes) and join each attribute record to its declaring method.

### Assessment

The user's intuition is partly confirmed:
- **Yes:** this is a substantial developer cheat/debug framework with typed arguments, object/enum selectors, favourites, categories, custom GUI hooks, console and mobile input handling.
- **No evidence yet:** the retail mobile build offers player-economy/gameplay commands such as adding Newbucks, setting health/energy, granting items, spawning slimes, or unlocking areas.
- **Hard gate:** `OSG.BaseCheatManager.get_CanCheat` returns constant false in the inspected build, and the normal manager lifecycle disables itself. The framework is compiled in, but not currently available through its normal retail UI.
- We will use this discovery to inform SRML Game Tools' own cheat-menu feature set. We will not turn the project into a general mod loader or bypass the retail protection gate.
