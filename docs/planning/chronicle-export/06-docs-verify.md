# Batch 06: Docs and verify

**Status:** done

## Deliverable

Updated SF export docs, a handoff brief for the ProvinceSystem chronicle dev, and an end-to-end checklist.

## Docs to update

| Doc | Change |
|-----|--------|
| [map-export.md](../../map-export.md) | New `chronicle` upload mode, the live vs map payload split, the `trade` regen type |
| [roadmap.md](../../roadmap.md) | Chronicle snapshot moves from planned to shipped; `events[]` stays planned |
| [dev-config.md](../../dev-config.md) | `enable-chronicle` toggle |
| [ProvinceSystem/docs/integrations/simplefactions.md](../../../../ProvinceSystem/docs/integrations/simplefactions.md) | Chronicle contract and cadence |
| [ProvinceSystem/docs/assets/map-export-schema.json](../../../../ProvinceSystem/docs/assets/map-export-schema.json) | Chronicle snapshot schema beside the existing `chronicle_event` definition |

## E2E checklist

1. Boot a test server with several factions, one palatinate overlord and one subject.
2. Call `updatePrestige()` repeatedly (any bank deposit triggers it) and confirm the total holds steady instead of creeping.
3. Restart. Confirm ranks are restored on the first converged pass, not climbed back over the following minutes.
4. Idle with no map activity for 10 minutes. Confirm two `chronicle.json` uploads and no `nation` upload.
5. Claim a province. Confirm the next tick takes the queued path with the full payload set.
6. Confirm `chronicle.json` carries a wealth breakdown, a prestige breakdown, rank thresholds and population per faction.
7. Delete a faction. Confirm it disappears from the next snapshot while `complete` stays true.
8. Set `enable-chronicle: false`. Confirm map uploads continue and chronicle stops.

---

## Handoff brief: ProvinceSystem chronicle

Everything below is the other dev's scope. SF produces the payload and posts it; ProvinceSystem owns storage, retention and charting.

### Ingest

Accept a `chronicle` upload mode at `POST /{mapRef}/data/upload/chronicle`. It arrives roughly every 5 minutes per map, alongside the existing map payloads.

Unlike every other mode, this one must **not** overwrite `input/{map}/chronicle.json`. `data_routes.py` currently writes each upload to a single path per mode, which would keep exactly one snapshot and discard the season. Partition on the `captured_at` field carried in the payload, for example `chronicle/{map}/{YYYY-MM-DD}/{HH-MM}.json`, with an index for range queries.

### Downsampling

288 snapshots a day is far more than any chart needs. Pick a daily canonical snapshot and keep the intra-day ones only for a short retention window. Graphs should read the daily series.

### Faction registry

Key identity on `(id, founded_at)`, not `id` alone. Faction ids are derived from the faction name rather than being UUIDs, and a deleted faction's name can be reused, which would otherwise splice two unrelated nations into one continuous line. Renames do not happen, so the id is stable within a lifetime.

Keep a persistent registry recording first seen, last seen, and the last known `name` and `rgb`. Factions vanish from the payload when deleted, and the registry is what keeps them on historical charts. Only treat an absence as a deletion when the snapshot carries `complete: true`.

### Two time axes

Store both `captured_at` (real ISO instant) and `server_day` plus `day_progress_seconds` (in-game). The in-game clock counts server uptime rather than calendar days, so it drifts against wall clock across downtime. Use the in-game pair for economic continuity and `captured_at` for display.

### What the wealth numbers mean

Faction wealth excludes all personal player money. Chart `faction_wealth`, `pouch_wealth` and `player_bank_wealth` as separate series and only sum them when you explicitly want total money supply.

Wealth is a stock made of a liquid bank balance plus sunk capital in mining nodes and guild branch expansions, so a faction can be wealthy with an empty treasury. Chart `wealth_breakdown`, not just the total.

### What the prestige numbers mean

The `Wealth` component of prestige is a share of global wealth, so a faction's prestige can fall while its own finances are flat, purely because rivals got richer. Any prestige chart needs `prestige_breakdown` and `global.faction_wealth` alongside the total or the dips look like bugs.

Rank thresholds are competitive rather than fixed, which is why each faction ships `rank_up_at` and `rank_down_at`. Draw threshold lines from those, never from `ranks.yml` minimums.

### Deltas

Difference consecutive daily snapshots yourself. SF does not send deltas. The flow figures it does send (`net_income`, `inflation_delta`, `guild_income`) are projections of a full day and are not the same quantity as the observed stock change, so present them as separate series.

### Events

`events` is present and always empty. The event stream is still unowned on the SF side; the existing `chronicle_event` schema in `map-export-schema.json` remains the target shape when it lands.
