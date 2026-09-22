# Batch 03: Chronicle payload

**Status:** done

## Deliverable

`Map/export/ChronicleExport.java` and `Map/export/ChronicleSnapshot.java`, writing `plugins/SimpleFactions/MapAPI/chronicle.json`, plus `chronicle` validation in [`RestServer.upload`](../../../src/main/java/me/Plugins/SimpleFactions/REST/RestServer.java).

Placed beside `Markers`, `WarMapExporter` and `OccupationMapExport` rather than in a new top-level package, matching the existing `*MapExport` naming and keeping `Map/export/` at six files.

## Principle

Export **absolute stocks** and let ProvinceSystem difference consecutive snapshots for deltas. Export **flows** explicitly, because they cannot be recovered from stock differences: a treasury that did not move might mean no activity, or trade income exactly cancelling military upkeep, and those are different stories on a graph.

Use the ledger **projections** (`getNetIncome()`, `getInflationDelta()`), never the raw daily accumulator maps. Projections are computed from current ledger state so they read the same whenever the 5-minute tick samples them. The accumulators are cleared at settlement (`FactionManager.java:485`) and would sawtooth 288 times a day.

## Schema

```json
{
  "schema_version": 1,
  "map_id": "main",
  "captured_at": "2026-09-01T10:35:00Z",
  "server_day": 143,
  "day_progress_seconds": 43200,
  "complete": true,
  "global": { },
  "factions": [ ],
  "guilds": [ ],
  "events": []
}
```

`server_day` is the count of completed `FactionManager` day rollovers and `day_progress_seconds` is the live `timer` field. Both are server uptime rather than calendar time, so they drift against `captured_at` across downtime. Ship both: the in-game pair is the honest x-axis for economic continuity, `captured_at` is for display.

**No day counter existed.** `FactionManager.timer` counts seconds and resets to 0 at each rollover, so it only ever gave progress-into-day. This batch adds `FactionManager.day`, incremented alongside the reset in `time()` and persisted next to the timer via `TimerData.day` (`Database.getDay` / `saveTimer(time, day)`).

`complete` is always true from a successful export. It exists so the backend can treat a faction's absence as a deletion rather than as a half-written upload.

`events` is reserved and always empty here. The stream stays owned elsewhere per [roadmap.md](../../roadmap.md).

### `global`

| Key | Source |
|-----|--------|
| `faction_wealth` | `FactionManager.getGlobalWealth()` |
| `pouch_wealth` | `getPouchWealth()` |
| `player_bank_wealth` | `getBankWealth()` |
| `liquid_wealth` | `getGlobalLiquidWealth()` |
| `guild_liquid_wealth` | `getGuildLiquidWealth()` |
| `node_wealth` | `getGlobalNodeWealth()` |
| `expansion_wealth` | `getGlobalGuildExpansions()` |
| `guild_income` | `getTotalGuildIncome()` |
| `faction_count`, `guild_count` | collection sizes |
| `claimed_provinces` | sum of province counts |
| `population` | distinct members across all factions |
| `active_wars` | `WarManager.getActive().size()` |
| `max_wealth_prestige` | `Cache.maxWealthPrestige` |

Do not pre-sum the three wealth figures. `getGlobalWealth()` excludes all personal money, so total money supply is a website-side decision, not ours.

### `factions[]`

| Group | Keys |
|-------|------|
| Identity | `id`, `founded_at`, `name`, `rgb`, `overlord`, `subjects[]` |
| Wealth | `wealth`, `wealth_breakdown{}`, `bank`, `vassal_wealth` |
| Flows | `net_income`, `inflation_delta`, `trade_power` |
| Prestige | `prestige`, `prestige_breakdown{}`, `rank`, `rank_level`, `rank_up_at`, `rank_down_at` |
| Standing | `prestige_position`, `wealth_position` |
| Territory | `provinces`, `realm_size`, `tier`, `tier_index`, `highest_title` |
| People | `members`, `members_with_vassals`, `settlements`, `population` |
| Assets | `installations`, `forts` |
| Conflict | `wars[]` |

`wealth_breakdown` and `prestige_breakdown` are `{type: amount}` maps straight from `getWealthModifiers()` and `getPrestigeModifiers()`. The prestige breakdown is the whole point of the export: the `Wealth` component is a share of global wealth, so a faction's prestige falls when rivals get richer even with its own finances flat. Without the components those dips are unexplainable on a chart.

`rank_up_at` is `FactionManager.getRankUpAmount(nextLevel)` and `rank_down_at` is `getRankUpAmount(currentLevel) * 0.95`. Both are competitive and move as the ladder shifts, so the website must draw threshold lines from these rather than from `ranks.yml`.

### `guilds[]`

`id`, `faction_id`, `name`, `type`, `wealth`, `bank`, `expansions` (`getTotalExpansionSpent()`), `trade_power`, `credit_score`, `size`. This is the only view of the merchant economy separate from state finances.

## Exporter

```java
public void export(File out) {
    FactionManager.updateAllPrestigeConverged();   // batch 02
    // build ChronicleSnapshot from live objects
    // write compact JSON (no setPrettyPrinting)
}
```

Runs on the main thread as part of `prepareLiveFiles()`; upload happens async (batch 04).

Do **not** loop `f.updateWealth()` to refresh before snapshotting. That method ends with `FactionManager.updateAllPrestige()`, so an n-faction loop is O(n squared) prestige recomputes. Wealth is already maintained eagerly on every bank mutation; prestige is the only stale value and the converged call covers it.

Skip `setPrettyPrinting()`. Every other exporter uses it, but this one ships 288 times a day.

## Upload validation

In `RestServer.upload`, alongside the existing `nation` and `map_markers` cases:

```java
if (mode.equals("chronicle")) {
    if (!payload.isJsonObject()) throw new IllegalStateException("chronicle upload must be JSON object");
    var obj = payload.getAsJsonObject();
    if (!obj.has("captured_at")) throw new IllegalStateException("chronicle upload must include captured_at");
    if (!obj.has("factions") || !obj.get("factions").isJsonArray())
        throw new IllegalStateException("chronicle upload must include factions array");
}
```

## Config

`enable-chronicle: true` in `config.yml`, loaded into `Cache.chronicleEnabled` by `ConfigLoader`. `RestServer.upload` already short-circuits on `Cache.mapEnabled`; the chronicle needs its own switch so it can be turned off without taking the map down, and so a broken chronicle never blocks a map regen.

## Next

Batch 04: make the 300s tick unconditional and split live from map payloads.
