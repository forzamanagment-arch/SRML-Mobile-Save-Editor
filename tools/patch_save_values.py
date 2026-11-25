#!/usr/bin/env python3
"""Patch the known Slime Rancher Mobile save fields in a ZIP save archive."""
from pathlib import Path
import struct
import zipfile

SOURCE = Path("input/save.zip")
OUTPUT = Path("output/modified_save.zip")
ENTRY = "gamesave-sav"

# Offsets were identified by comparing the supplied before/after saves.
# The currency is duplicated in the save header and the serialized game data.
EXPECTED = {
    71: 2058,
    87983: 2058,
    88149: 9,   # Honey slime inventory count
    88212: 13,  # Chickadoo inventory count
    88329: 20,  # Water inventory count
}
REPLACEMENTS = {
    71: 676767,
    87983: 676767,
    88149: 20,
    88212: 3,
    88329: 30,
}

if not SOURCE.is_file():
    raise SystemExit("Missing input/save.zip. Upload the original 'до' save there first.")

with zipfile.ZipFile(SOURCE, "r") as src:
    if ENTRY not in src.namelist():
        raise SystemExit(f"Archive does not contain {ENTRY!r}.")
    payload = bytearray(src.read(ENTRY))
    for offset, expected in EXPECTED.items():
        actual = struct.unpack_from("<I", payload, offset)[0]
        if actual != expected:
            raise SystemExit(
                f"Unexpected value at offset {offset}: expected {expected}, got {actual}. "
                "No output was written."
            )

    for offset, value in REPLACEMENTS.items():
        struct.pack_into("<I", payload, offset, value)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, "w") as dst:
        info = src.getinfo(ENTRY)
        dst.writestr(info, payload)

with zipfile.ZipFile(OUTPUT, "r") as check:
    assert check.testzip() is None
    patched = check.read(ENTRY)
    for offset, expected in REPLACEMENTS.items():
        actual = struct.unpack_from("<I", patched, offset)[0]
        assert actual == expected, (offset, expected, actual)

print(f"Created and verified {OUTPUT}")
print("Newbucks: 676767 | Honey slimes: 20 | Chickadoo: 3 | Water: 30")
