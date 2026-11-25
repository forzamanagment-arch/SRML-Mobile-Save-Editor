# World-progress training fixture (2026-11-25)

Source: release `Saves`, asset `Savefiles.zip`, three extensionless save archives paired with TXT descriptions by exact basename. The TXT descriptions are user-provided ground truth; they are not themselves proof of binary field offsets.

## Complete labelled values from the three TXT files

| Parameter | `20261125171726-2-1` | `20261125175309-3-0` | `20261122185933-1-1` |
|---|---:|---:|---:|
| Day | 1 | 1 | 18 |
| Time at save | 11:04–11:10 | 12:38–12:50 | 18:11–18:20 |
| Maximum health | 100 | 150 | 250 |
| Maximum energy | 100 | 150 | 250 |
| Current health | 100 | 120 | 250 |
| Candidate current energy (`SRPL + 0x0E`) | 87 | 150 | 250 |
| Newbucks (`SRPL + 0x16`) | 262 | 3,548,344 | 81,649 |
| Water slot | Locked | Unlocked, empty | Unlocked, Ancient Water x29 |
| Extra ranch locations opened | 0 | 0 | 4 |
| Extra map locations opened | 1 | 0 | 2 |
| Locations opened with slime keys | 0 | 0 | 2 |
| Keys currently held | 0 | 0 | 2 |
| Gordos burst | 0 | 0 | 6 |

Current health for the 150-max-health sample is taken from the user's pre-save screenshot (120), not from the TXT. The binary value at `SRPL + 0x0A` is also 120.

## Per-save world progress

### `20261125171726-2-1` (100/100)

- Ranch: only the starting ranch area is open; no additional ranch expansion.
- Map: Dry Reef was manually opened (one additional map location). The ranch's default map marker is not counted.
- Slime-key gates: none recorded as opened.
- Keys held: 0.
- Gordos burst: 0.
- Water slot: locked, not merely an unlocked slot with quantity zero.
- Inventory: Pink Slime x8; Hen-Hen x7; Tabby Slime x2; Chickadoo x4.

### `20261125175309-3-0` (150/150)

- Ranch: only the starting ranch area is open; no additional ranch expansion.
- Map: only the ranch's default map marker is open; no additional map area.
- Slime-key gates: none recorded as opened.
- Keys held: 0.
- Gordos burst: 0.
- Water slot: unlocked, empty (ID 0, count 0).
- Inventory: Chickadoo x1; Rock Slime x2; empty slot; Gold Plort x999698.

### `20261122185933-1-1` (250/250)

- Ranch: four additional areas are open: Grotto, Overgrowth, Docks, Lab. The starting area is not included in the count.
- Map: two additional areas are open: Dry Reef and Indigo Quarry. The default ranch marker is not included.
- Two slime-key gates are recorded as opened:
  1. Transition to Indigo Quarry.
  2. Entrance to the Ancient Ruins from the Moss Blanket.
- Keys currently held: 2.
- Gordos burst: 6:
  1. Pink Gordo #1, ledge / central island.
  2. Pink Gordo #2, Ring Island (teleporter from the beach).
  3. Tabby Gordo #1, Dry Reef near the bridge to Moss Blanket.
  4. Tabby Gordo #2, Dry Reef beach.
  5. Honey Gordo, Moss Blanket, rock near the large tree in the second area.
  6. Phosphor Gordo, Dry Reef cave near the edge of the area.
- Key accounting supplied by the user: four of these Gordos awarded keys, two keys were spent, leaving two held. Two of the six Gordos do not award keys.
- Water slot: unlocked, Ancient Water x29.
- Inventory: Gold Plort x9729; empty slot; Boom Slime x7; Strange Diamond x106; Ancient Water x29.

## Confirmed binary fields

All offsets below are relative to the `SRPL` text tag (the first `S`), little-endian uint32:

- `+0x0A`: current health at save time. Confirmed against the user's screenshot for the 120-health sample and full-health screenshots for the 100 and 250 samples.
- `+0x0E`: candidate current energy at save time. The binary values are 87, 150, and 250; only the latter two currently match the labelled maximum energy, so exact in-game current-energy readings are still needed to confirm.
- `+0x16`: Newbucks, confirmed across all three saves.

The maximum health and maximum energy capacities are not identified by these fields.

## World-progress binary research status

The descriptions establish these ground-truth labels, but the corresponding serialized fields have **not yet been located and independently validated**:

- Ranch expansion flags / IDs (Grotto, Overgrowth, Docks, Lab).
- Map area discovery flags / IDs (Dry Reef, Indigo Quarry).
- Individual slime-key gate unlock flags / IDs.
- Current key inventory count.
- Individual Gordo burst states and their identities / positions.
- Gordo-to-key reward accounting.
- Water-slot unlock state as distinct from an unlocked-but-empty slot.

Do not write to guessed offsets. In particular, do not derive key count from Gordo count: the labelled sample explicitly shows 6 Gordos burst, 4 keys earned, 2 spent, and 2 currently held.

## Next binary-analysis experiment

Use controlled pairs of saves from the same world, changing only one progress action at a time:
1. Before/after opening one ranch expansion.
2. Before/after opening one map location without using a slime key.
3. Before/after opening one slime-key gate, recording key count before and after.
4. Before/after bursting one known Gordo, recording whether it awards a key.
5. Before/after unlocking the water slot.

This separates individual flags from aggregate counters and avoids attributing unrelated world changes to the wrong field.
