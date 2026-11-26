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
