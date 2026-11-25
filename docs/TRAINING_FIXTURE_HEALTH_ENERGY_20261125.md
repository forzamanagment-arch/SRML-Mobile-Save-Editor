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
- `SRPL + 0x0A` and `SRPL + 0x0E` are changing player-record values and are worth investigating further.

### Not confirmed

- Maximum health and maximum energy are both set to the same value in all three samples. These fixtures cannot distinguish the health field from the energy field.
- Neither `SRPL + 0x0A` nor `SRPL + 0x0E` maps consistently to the declared maximum values across all three samples. They may represent current health/energy, or other related state. Do not label either as max health or max energy yet.
- A scan for exact 8/16/32/64-bit integers and float32 values matching the labelled maxima at a stable relative offset from `SRPL` did not identify a confirmed max-health/max-energy field.
- The saves differ in world progress, inventory, time, and file size, so whole-file byte diffs are not a valid way to attribute every change to health or energy.

## Next experiment needed

Create two additional saves from the same world while keeping all other values unchanged:
1. Max health changes, max energy stays fixed.
2. Max energy changes, max health stays fixed.

Keep current health and current energy at known values too, and record those separately if the game UI exposes them. This will let the recognizer distinguish capacity fields from current-value fields.

## Use in the editor

This is a labelled research fixture, not a confirmed editor rule. The editor must not write to `SRPL + 0x0A` or `SRPL + 0x0E` until independent samples confirm their meaning and safe write behavior.
