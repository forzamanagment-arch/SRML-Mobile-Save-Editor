# Health and Energy Save Research Fixture (2026-11-25)

Source: public release tag `Saves`, asset `Savefiles.zip`.
The GitHub Actions workflow downloads the release asset directly; no manual attachment is required.

## Paired samples and user-provided ground truth

| Save | Max health | Max energy | Newbucks | Day | Time |
|---|---:|---:|---:|---:|---|
| `20261125171726-2-1` | 100 | 100 | 262 | 1 | 11:04-11:10 |
| `20261125175309-3-0` | 150 | 150 | 3,548,344 | 1 | 12:38-12:50 |
| `20261122185933-1-1` | 250 | 250 | 81,649 | 18 | 18:11-18:20 |

TXT files are paired by exact save basename:
- `20261125171726-2-1.txt`
- `20261125175309-3-0.txt`
- `20261122185933-1-1.txt`

## Other ground truth

### 100 / 100 sample: `20261125171726-2-1`
- Inventory: Pink Slime x8; Hen-Hen x7; Tabby Slime x2; Chickadoo x4.
- Water slot locked.
- Ranch locations: 0 additional.
- Map locations: 1 manually opened Dry Reef.
- Keys: 0.
- Gordos burst: 0.

### 150 / 150 sample: `20261125175309-3-0`
- Inventory: Chickadoo x1; Rock Slime x2; empty slot; Gold Plort x999698.
- Water slot unlocked, empty (ID 0, count 0).
- Ranch locations: 0 additional.
- Map locations: 0 additional.
- Keys: 0.
- Gordos burst: 0.

### 250 / 250 sample: `20261122185933-1-1`
- Inventory: Gold Plort x9729; empty slot; Boom Slime x7; Strange Diamond x106; Ancient Water x29.
- Water slot unlocked.
- Day 18.
- Ranch locations: 4 additional (Grotto, Overgrowth, Docks, Lab).
- Map locations: 2 additional (Dry Reef, Indigo Quarry).
- Slime-key opened locations: 2.
- Keys currently held: 2.
- Gordos burst: 6: 2 Pink, 2 Tabby, 1 Honey, 1 Phosphor.
- User explanation: 4 keys earned from gordos, 2 spent; two of the six gordos do not award keys.

## Binary observations

The nested save payload is `gamesave-sav` inside each extensionless save archive.

Player marker `SRPL` was found at:
- 100 sample: absolute offset `0x15725`
- 150 sample: absolute offset `0x15725`
- 250 sample: absolute offset `0x15799`

Values below are little-endian unsigned 32-bit integers relative to the start of the `SRPL` text marker:

| Sample | SRPL + 0x0A | SRPL + 0x0E | SRPL + 0x16 |
|---|---:|---:|---:|
| 100 / 100 | 100 | 87 | 262 |
| 150 / 150 | 120 | 150 | 3,548,344 |
| 250 / 250 | 250 | 250 | 81,649 |

### Confirmed

- `SRPL + 0x16` matches Newbucks in all three samples. This independently corroborates the current Newbucks field rule.
- `SRPL + 0x0A` stores **current health at save time**, not maximum health. The 150-max-health sample had 120 current health in the user's pre-save screenshot, matching the binary value 120; the other two samples had full current health (100 and 250), also matching the binary values.
- `SRPL + 0x0E` is a candidate for **current energy at save time**, but this interpretation is not independently confirmed yet.

### Not confirmed

- `SRPL + 0x0E` as current energy remains unconfirmed because the matching energy readings at the exact save moments have not yet been independently compared.
- The maximum-health and maximum-energy capacity fields have **not** been located. Do not confuse these capacity fields with current health/current energy.
- A scan for exact 8/16/32/64-bit integers and float32 values matching the labelled maxima at a stable relative offset from `SRPL` did not identify a confirmed max-health/max-energy field.
- The saves differ in world progress, inventory, time, and file size, so whole-file byte diffs are not a valid way to attribute every change to health or energy.

## Next experiment needed

Create controlled saves from the same world and record the exact current energy shown immediately before saving. To test the capacity fields separately, create two saves where max health and max energy differ (for example 150/100 and 100/150), while recording current health and current energy at save time.

## Use in the editor

This is a labelled research fixture, not a confirmed editor rule. `SRPL + 0x0A` may be treated as the confirmed current-health field for analysis; any write support still needs bounds/format validation and a backup-safe test. Do not write `SRPL + 0x0E` as energy until confirmed. Maximum health and maximum energy capacity fields remain unknown.
