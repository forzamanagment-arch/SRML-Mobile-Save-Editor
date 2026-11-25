# Save format research

## Confirmed observations

- The save slot file is a ZIP container.
- Its payload includes a binary file whose observed magic begins with `SRGAME`.
- The payload contains structured markers including `SRGSUMM` and repeated `SRV3` markers.
- A single observed save is not enough to identify the exact Newbucks or inventory fields.

## Rules for the parser

1. Preserve unknown bytes and unknown records.
2. Do not write to the original save during analysis.
3. Validate ZIP CRC and entry metadata before and after any rewrite.
4. Do not assign semantic names to offsets until differential tests confirm them.

## Required differential samples

- Baseline: close the game and copy the full `pt_saves` directory.
- Currency sample: change only the amount of Newbucks, close the game, then copy the directory again.
- Inventory sample: change only one item's quantity, close the game, then copy the directory again.

Compare the inner `gamesave-sav` payloads, not only the outer ZIP files.
