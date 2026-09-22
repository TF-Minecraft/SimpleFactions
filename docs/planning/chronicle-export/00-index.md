# Chronicle export - Batch index

**Repo:** `simplefactions`
**Goal:** Ship a `chronicle.json` snapshot every 5 minutes so ProvinceSystem can build faithful season timelapses and graphs of wealth, prestige, rank, territory and population per nation.

**Scope:** SF-side snapshot production and upload, plus the prestige correctness fixes the snapshot depends on. **Out of scope:** chronicle storage, downsampling, retention and charting on ProvinceSystem (owned by another dev, see [06-docs-verify](./06-docs-verify.md)); the `events[]` stream (reserved key, still owned elsewhere per [roadmap.md](../../roadmap.md)).

**Related:** [map-export.md](../../map-export.md) · [ProvinceSystem map overview](../../../../ProvinceSystem/docs/map/overview.md)

---

## Problem

Nothing usable for history is exported today. `nation.json` is a raw concatenation of `plugins/SimpleFactions/Data/*.json`, so it only carries what `Database.saveFaction` persists: guild `balance` and persistent wealth modifier strings. Faction total wealth, every prestige component, `PrestigeRank`, income flows and population are all computed in memory and never leave the server.

Three blockers sit in front of the export:

1. **`updatePrestige()` is not idempotent.** The `% Bonus` line sums the whole modifier list, which already holds the previous bonus, so repeated calls converge upward to ~11.1% of base instead of 10%. The type string embeds the percentage, so a changed or removed bonus leaves a stale line summing forever.
2. **`PrestigeRank` is not persisted and only moves one level per call.** After a restart every faction sits at level 1 and needs one pass per level to climb back, against thresholds that have collapsed to the static `ranks.yml` minimums because no faction holds the higher ranks yet.
3. **The 300s upload is conditional on a non-empty map queue.** A quiet server produces no upload at all, leaving chronicle gaps of up to an hour, which is exactly when the economy curve should be densest.

---

## Design lock

| Piece | Choice |
|-------|--------|
| Snapshot owner | `Map/export/ChronicleExport` + `ChronicleSnapshot` DTOs |
| Upload mode | `chronicle` - `POST /{mapRef}/data/upload/chronicle` |
| Local file | `plugins/SimpleFactions/MapAPI/chronicle.json` |
| Cadence | Every 300s, unconditional, on both the queued and the quiet path |
| Dates | SF stamps `captured_at` + `server_day`; PS owns partitioning and retention |
| Dead factions | SF sends `complete: true` + `founded_at`; PS keys identity on `(id, founded_at)` |
| Prestige assembly | Pure `PrestigeBreakdown.build(...)`, rebuilt from scratch each call |
| Bonus base | Includes `Subjects` this season (gameplay change, see batch 01) |
| Rank persistence | `FactionData.rank` holds the rank id; omitted means lowest |
| Load safety | `FactionManager.loading` suppresses prestige recompute until all factions are in |
| Convergence | `updateAllPrestige()` loops until no rank changes, capped at rank count |
| Toggle | `enable-chronicle` in `config.yml` + `Cache.chronicleEnabled`, default true |
| Trade regen | New `commenceRegen("trade")` on the quiet path (our own PS task, not the chronicle dev's) |
| Events | `events: []` reserved in the payload, not populated by this plan |

---

## Batches (implement in order)

| # | Doc | Deliverable |
|---|-----|-------------|
| 1 | [01-prestige-fix](./01-prestige-fix.md) | `PrestigeBreakdown`, idempotent `updatePrestige()`, bonus covers subjects |
| 2 | [02-persist-rank-founded](./02-persist-rank-founded.md) | `rank` + `founded_at` persistence, load suppression, convergence loop |
| 3 | [03-chronicle-payload](./03-chronicle-payload.md) | `ChronicleExport`, snapshot schema, `chronicle` upload validation |
| 4 | [04-upload-cadence](./04-upload-cadence.md) | Unconditional 300s tick, live vs map payload split, `trade` regen |
| 5 | [05-tests](./05-tests.md) | Idempotency, rank round-trip, snapshot builder tests |
| 6 | [06-docs-verify](./06-docs-verify.md) | `map-export.md`, PS handoff brief, E2E checklist |

---

## Checkpoint

```text
updatePrestige() called 10x in a row returns the same number
Restart restores Legendary factions to Legendary on the first converged pass
chronicle.json lands every 300s on a server with zero map activity
province_data + guilds + chronicle upload on the quiet path, queue path adds nation/markers/titles
Snapshot carries wealth breakdown, prestige breakdown, rank thresholds and population per faction
mvn test green
```

**Done when:** batch 6 verify matrix passes and the ProvinceSystem dev has the handoff brief.

---

## Status

| Batch | Status |
|-------|--------|
| 01 Prestige fix | done |
| 02 Rank + founded persistence | done |
| 03 Chronicle payload | done |
| 04 Upload cadence | done |
| 05 Tests | done |
| 06 Docs + verify | done (docs); E2E pending a test server |
