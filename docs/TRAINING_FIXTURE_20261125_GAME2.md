# Training fixture: game2 (2026-11-25)

This document records the user-labelled values for one Slime Rancher Mobile save and the byte-level observations made from the supplied archive. The save itself is intentionally not stored in this public repository.

## Source file

- Original uploaded filename: `20261125171726-2-2` (extensionless ZIP archive)
- ZIP entry: `gamesave-sav`
- Uncompressed entry size: 137,863 bytes
- Save header: `06 SRGAME`
- Game build string found in the save: `1.1.1103`
- World name supplied by user: `игра2`
- Day/time supplied by user: Day 1, 10:39

## User-provided ground truth

- Keys: 0
- Gordos burst for keys: 0
- Newbucks: 262
- Maximum energy: 100
- Maximum health: 100
- Slot 1: Pink Slime x8
- Slot 2: Hen-Hen (кур-курица) x7
- Slot 3: Tabby Slime x2
- Slot 4: Chickadoo (цыпчик) x4
- Slot 5: water slot locked / not unlocked

The locked water slot is not equivalent to an unlocked water slot with quantity zero. Do not synthesize a water inventory entry for this fixture.

## Binary observations

The player object begins with the `SRPL` tag at offset 87,845.

Four consecutive simple inventory records appear in the player inventory area. Each `SRAD` record contains a numeric item identifier followed by a quantity:

| Slot | Ground-truth item | Quantity | Numeric ID observed |
|---|---|---:|---:|
| 1 | Pink Slime | 8 | 3 |
| 2 | Hen-Hen | 7 | 16 |
| 3 | Tabby Slime | 2 | 37 |
| 4 | Chickadoo | 4 | 18 |

This is a strong mapping for this build because all four observed IDs match the PC Slime Rancher item-ID list published in the Cheat Happens trainer discussion: `PINK_SLIME=3`, `HEN=16`, `TABBY_SLIME=37`, and `CHICK=18`. The PC list also gives `WATER_LIQUID=48`, but water is not present as an unlocked slot in this fixture, so this save does not independently verify mobile water ID 48.

External references:
- PC Slime Rancher trainer item-ID list: https://www.cheathappens.com/show_board2.asp?headID=129001&titleID=21693
- SRML terminology explains that PC Slime Rancher distinguishes an actor's type identifier from its per-instance actor ID: https://github-wiki-see.page/m/slimeranchermodding/srml/wiki/Concepts

## Confidence and limitations

- **Verified against this fixture's supplied ground truth:** IDs 3, 16, 37, and 18 map to the four listed inventory items in this save.
- **Cross-version match:** these four numeric values match the cited PC list. This is promising evidence that the mobile build retained the same item-ID values for these base items, but it does not prove that every item, Largo variant, modded item, or later game version uses the same IDs.
- **Not verified by this fixture:** water slot data, key count encoding, max-stat storage, every other item ID, Largo IDs, and the complete save grammar.
- Do not treat the user-supplied inventory labels as proof that arbitrary numeric IDs have been decoded. Keep unknown IDs as unknown and preserve their records unchanged.
