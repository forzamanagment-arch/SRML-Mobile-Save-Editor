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
