# SRML Mobile Save Editor

Android prototype for editing Slime Rancher Mobile save archives.

## Prototype features

- Opens ZIP save archives, including extensionless files.
- Includes a read-only **recognition test** that detects the player block, checks the duplicated Newbucks pattern, and enumerates the compact `SRAD` inventory record group.
- Reads the `gamesave-sav` entry.
- Detects and edits both mirrored Newbucks fields.
- Detects serialized inventory records using the `SRAD` marker.
- Edits only the currently verified records: Honey slimes (ID 59), Chickadoos (ID 16), and Water (ID 48).
- Leaves unknown record IDs untouched until their meaning is confirmed.
- Rebuilds the archive into a separate output file.
- Suggests the original save filename for the output.

## Compatibility and safety

This is an early, version-specific prototype, not a universal save editor. The Newbucks signatures and inventory record layout were checked against the paired sample saves supplied for this project. If the expected signatures are missing or ambiguous, the app refuses to write a modified archive.

Only the three verified record IDs are editable in this prototype:
- 59: Honey slimes
- 16: Chickadoos
- 48: Water

Other serialized records are preserved unchanged. They are not exposed as editable inventory until their meaning is verified.

The app does not upload save files. The original archive is not overwritten. After exporting, place the output in the game's save folder using the exact original filename. Keep a backup of the original.

## v0.2: read-only recognition prototype

A first read-only recognizer is now wired into the app. Open the existing editor screen and choose **Тест распознавания нового сохранения**. It currently checks the duplicated Newbucks value at two positions relative to the `SRPL` player marker and scans for the largest compact group of `SRAD` inventory records near that player block. It labels known IDs 3 (Pink Slime), 16 (Hen-Hen), 18 (Chickadoo), and 37 (Tabby Slime); unknown IDs remain numeric.

The recognizer is calibrated against one supplied save from game build `1.1.1103`. It is a test implementation, not a complete structural parser. It does not yet recognize keys, burst Gordos, water-slot unlock state, day/time, or health/energy semantics. It does not modify or export the selected save. The next test is to load a second save from the same game build and compare every displayed value with the game.

The design and parser safety requirements are documented in [SAVE_PATTERN_ENGINE_V02.md](docs/SAVE_PATTERN_ENGINE_V02.md) and the training sample in [TRAINING_FIXTURE_20261125_GAME2.md](docs/TRAINING_FIXTURE_20261125_GAME2.md).

## Build

GitHub Actions workflow **Build SRML Mobile Save Editor** builds a debug APK on push or manual dispatch.
