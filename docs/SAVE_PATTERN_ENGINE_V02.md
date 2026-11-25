# Save Pattern Engine v0.2

## Goal

Support any world created by the pinned Slime Rancher Mobile game version without requiring a before/after save pair from the end user.

The user workflow is: select one save archive -> inspect and recognize the payload -> edit supported fields -> export under the exact original save filename.

## Important distinction

A byte difference is evidence that data changed; it is not proof of what that data means. The engine must identify records by structure and relationships, not by absolute byte offsets or by searching for a coincidental integer value.

## Pipeline

1. **Archive reader**
   - Accept extensionless ZIP save files and ordinary .zip copies.
   - Find the `*-sav` entry and validate the `06 53 52 47 41 4D 45` payload signature.
   - Preserve the original archive entry name and outer filename.
2. **Version gate**
   - Recognize only the supported Slime Rancher Mobile build.
   - If the version cannot be established confidently, inspect read-only and refuse edits.
3. **Structural parser**
   - Parse the serialization framing, lengths, types, and nested records.
   - Keep a tree of record paths and byte ranges. Never assume that a record lives at a fixed offset.
4. **Semantic resolver**
   - Resolve Newbucks, water, and inventory entries from their record paths and validated field types.
   - Inventory entries are a collection: enumerate every entry, including entries not present in the training sample.
   - Preserve unknown records byte-for-byte.
5. **Validation**
   - Require exactly one unambiguous target for scalar values.
   - Validate collection counts, bounds, duplicate IDs, and related fields before writing.
   - Reparse the output and verify the requested values.
6. **Export**
   - Write a new archive, never overwrite the source.
   - Preserve the original save filename because the game expects the slot's original name.

## Confidence states

- `VERIFIED`: parser path and semantic meaning confirmed for the pinned game build; editable.
- `CANDIDATE`: plausible structural match, shown for diagnostics only; not editable.
- `UNKNOWN`: no safe interpretation; preserve unchanged.

## What makes it universal across worlds

The parser must recognize the schema, not sample values. A world with 0, 3, or 50 inventory records must be handled by iterating the serialized collection. The engine must not depend on a particular quantity, inventory ordering, or a byte offset observed in one save.

## Evidence needed before enabling writes

For each field, maintain a versioned descriptor containing:
- semantic name and serialized type;
- structural path / record signature;
- encoding and numeric bounds;
- cardinality (single field or collection);
- validation rules and cross-field invariants;
- test fixtures and expected parsed values.

The existing confirmed Newbucks and inventory edits remain regression tests. Water and each inventory record type must be checked against the actual parser before being marked verified.

## Current implementation status

This document defines the v0.2 design. It is not a claim that the universal parser is already implemented. The existing prototype must not be replaced by guessed offsets. Implementation requires the current source files and representative save fixtures from the supported game build.
