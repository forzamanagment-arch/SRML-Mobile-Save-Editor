# SRML Mobile Save Editor

Android prototype for editing Slime Rancher Mobile save archives.

## Prototype features

- Opens ZIP save archives, including extensionless files.
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

## Build

GitHub Actions workflow **Build SRML Mobile Save Editor** builds a debug APK on push or manual dispatch.
