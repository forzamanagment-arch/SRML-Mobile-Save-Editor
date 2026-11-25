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
- `+0x1A`: candidate current slime-key count. The three values are 0, 0, and 2, matching the TXT key counts exactly in all three samples. Treat as a strong candidate pending one controlled key gain/spend test.

The maximum health and maximum energy capacities are not identified by these fields.

## World-progress binary research status

The descriptions establish these ground-truth labels. The following serialized patterns have been found but need controlled before/after samples before they can be treated as universal rules:

- Ranch expansion flags / IDs (Grotto, Overgrowth, Docks, Lab).
- Map area discovery flags / IDs (Dry Reef, Indigo Quarry).
- Individual slime-key gate unlock flags / IDs.
- Current key inventory count.
- Ranch/map/key-gate flags and individual location identities.
- Gordo-to-key reward accounting and mapping random Gordo instance IDs to the species/locations in the TXT.
- The exact binary flag for water-slot unlock; however, the three samples show a useful structural distinction: the locked sample has only four inventory records, while both unlocked samples have a fifth record. The 150/150 sample's fifth record is ID 0/count 0 (unlocked but empty), and the 250/250 sample's fifth record is ID 113/count 29.

### Water-slot structural candidate

The 100/100 save, whose TXT says the water slot is locked, has four inventory records. Both saves whose TXT says the water slot is unlocked have a fifth inventory record: ID 0/count 0 when empty, or ID 113/count 29 for Ancient Water. The editor now recognizes ID 0 as an unlocked empty water slot rather than misclassifying it as locked. This is a strong three-sample structural rule, but it should still be checked against a controlled lock/unlock pair.

### Gordo instance-state candidate

The binary scan found 69 serialized strings beginning with `gordo` in each sample. Six instance records in the 18-day save differ from the two zero-Gordo saves: the uint32 value in the record field immediately after the `03 SRG 01 00 00 00 30` sequence is `0xFFFFFFFF` for exactly six instance names:

- `gordo0806598363`
- `gordo1686430858`
- `gordo0966530436`
- `gordo1440735342`
- `gordo2083992877`
- `gordo1173217994`

This six-record match is a strong candidate for the six burst Gordos, but the random instance names have not yet been mapped to Pink/Tabby/Honey/Phosphor identities or positions. A seventh record, `gordo1831887310`, has value 2 in the progressed save and 0 in the two zero-Gordo saves; its meaning is unknown and it must not be counted as a burst Gordo.

Do not write to guessed offsets. In particular, do not derive key count from Gordo count: the labelled sample explicitly shows 6 Gordos burst, 4 keys earned, 2 spent, and 2 currently held. The `SRPL + 0x1A` key-count candidate is separate from the per-Gordo records.

## Next binary-analysis experiment

Use controlled pairs of saves from the same world, changing only one progress action at a time:
1. Before/after opening one ranch expansion.
2. Before/after opening one map location without using a slime key.
3. Before/after opening one slime-key gate, recording key count before and after.
4. Before/after bursting one known Gordo, recording whether it awards a key.
5. Before/after unlocking the water slot.

This separates individual flags from aggregate counters and avoids attributing unrelated world changes to the wrong field.

## Other serialized sections inspected

The deep scan also found these repeated record families in the three payloads:

- `SRGA`: one section per save. The variable-length string data immediately before it differs between samples. The 100/100 sample includes the string `REEF`, while the 250/250 sample includes `MAP`; this is suggestive of progress/achievement labels but does not yet establish the individual map/ranch unlock encoding.
- `SRHD`: one section per save, followed by an `SRAP` record collection. Its values differ, but no reliable location-name mapping has been established.
- `SRLP`: 41 records in each sample, with some readable `plot...` identifiers. These look like plot-related records, but the current TXT descriptions do not label each plot's state or type.
- `SRRW`: 408–410 repeated records containing vector-like numeric data. They appear to be world/transform records, not directly labelled location unlock flags.
- `SRTP`: 181 records in each sample with `pod...` identifiers. These are likely treasure-pod instance records, but the TXT files do not say which pods are opened.

These are now marked as inspected, not decoded. The presence of a tag or a readable identifier is not enough to safely assign it to a gameplay setting.
