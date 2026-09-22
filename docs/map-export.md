# Map export

SimpleFactions exports political map data to **ProvinceSystem** via the **TFMCWeb** gateway. The website reads uploaded JSON from the map data store; incremental regen redraws only changed provinces.

**ProvinceSystem side:** [integrations/simplefactions.md](../../ProvinceSystem/docs/integrations/simplefactions.md) · **Wars overlay:** [map/wars-on-map.md](../../ProvinceSystem/docs/map/wars-on-map.md) · **Schema:** [map-export-schema.json](../../ProvinceSystem/docs/assets/map-export-schema.json)

---

## Config

| Key | Role |
|-----|------|
| `enable-map` | Master switch; when false, uploads and regen are skipped |
| `enable-provinces` | In-game land grid; default true. When false, Input grid is not loaded, land/war commands are blocked, and `enable-map` is forced off |
| `enable-chronicle` | Chronicle snapshot upload; default true. Independent of `enable-map` so a broken chronicle never blocks a map regen |
| `map-reference` | Map id in upload/regen URLs (e.g. `main`, `dev`) |
| `api.base-url` | TFMCWeb gateway (softdepend config, not in `config.yml`) |

`Cache.mapRef` mirrors `map-reference` at load time. All upload paths use `/{mapRef}/data/upload/{mode}`.

---

## Upload modes

`RestServer.upload(mode, file)` POSTs JSON to TFMCWeb. `MapSystem` exports files under `plugins/SimpleFactions/MapAPI/` (and title JSON under `Input/`), then uploads:

| Mode | File | Payload |
|------|------|---------|
| `nation` | `MapAPI/nation.json` | Faction colours / borders |
| `province_data` | `MapAPI/province_data.json` | Per-province trade/prosperity; wartime `occupied_by` (occupier faction id) |
| `guilds` | `MapAPI/guilds.json` | Guild markers |
| `map_markers` | `MapAPI/map_markers.json` | Settlements, installations, forts, wars |
| `chronicle` | `MapAPI/chronicle.json` | Wealth / prestige / territory snapshot for season graphs |
| `infestation_data` | Infestations plugin `MapAPI/infestation_data.json` | Per-province infestation severity/group (uploaded by Infestations via `RestServer.upload`) |
| `county` / `duchy` / `kingdom` / `empire` | `Input/*.json` | De jure title trees |
| `queue` | `MapAPI/queue.json` | Incremental province/border change list |

Validation runs in `RestServer.validate(mode, payload)` before the POST.

`map_markers`:

- Root must be a JSON object with a `settlements` array.
- If present, `installations` and `forts` must be arrays.

`chronicle`:

- Root must be a JSON object with `captured_at` and a `factions` array.

---

## Regen

| Trigger | Mechanism |
|---------|-----------|
| Incremental | `MapSystem.updateMap()` uploads `queue` + full payloads, then `RestServer.commenceRegen("queued")` |
| Live only | `MapSystem.updateLiveData()` uploads the live payloads, then `commenceRegen("trade")` |
| Full | `/faction fullregen <map>` or `MapSystem.fullRegen()` → `commenceRegen("full")` |
| Nation-only queue | `/faction regen` enqueues all nations |

Regen URL: `GET /{mapRef}/{REGEN_HASH}/api/regenerate/{queued|full|trade}`. `REGEN_HASH` is currently hardcoded in `RestServer` (move to config before production; see [dev-config.md](./dev-config.md)).

`trade` redraws trade and prosperity overlays without touching nation borders or title geometry. Until ProvinceSystem implements it the call fails, gets logged and swallowed, so the uploads still land.

---

## Tick cadence

`MapSystem.tick()` splits the payloads by cost. Trade and chronicle change continuously with no map queue involvement, so they ship on every cycle; nation geometry and markers only when there is queued work.

| Every | Condition | Path | Payloads |
|-------|-----------|------|----------|
| 3600 s | always | `queueAllNations()` → `updateMap()` | all |
| 300 s | queue non-empty | `updateMap()` | `queue`, live, `nation`, `map_markers`, titles |
| 300 s | queue empty | `updateLiveData()` | live only |

Live payloads are `province_data`, `guilds` and `chronicle` (`prepareLiveFiles` / `uploadLiveFiles`). Map payloads are `map_markers` and `nation` (`prepareMapFiles`).

The 3600 s branch is checked first and unconditionally. As an else-branch it could be starved indefinitely by a queue that happened to be non-empty on the crossing tick.

Faction state is saved before each queued upload/regen. The live path skips the per-faction save loop because nothing on it reads `Data/*.json`.

---

## `map_markers.json` shape

Built by `Markers.export()`:

| Top-level key | Source |
|---------------|--------|
| `map_id` | `Cache.mapRef` |
| `exported_at` | ISO-8601 instant |
| `settlement_large_population_threshold` | Config |
| `settlements[]` | Faction capitals + named settlements |
| `installations[]` | Operational forts, ports, airports |
| `forts[]` | Fort ZOC province lists (`zoc_provinces`, war-aware via `ZocRealm`) |
| `wars[]` | Active campaign wars only (`WarMapExporter`) |

Under-construction installations are **not** exported.

---

## `chronicle.json` shape

Built by `ChronicleExport.export()` / `ChronicleSnapshot.build()`. A point-in-time record of every stock and flow the website needs to graph a season, uploaded every 300 s.

Stocks are absolute; ProvinceSystem differences consecutive snapshots for deltas. Flows are shipped explicitly because they cannot be recovered from stock differences (a flat treasury may mean no activity, or trade income exactly cancelling upkeep). Flows are always the ledger **projections**, never the daily accumulators, which are cleared at settlement and would sawtooth across a 5 minute cadence.

| Top-level key | Meaning |
|---------------|---------|
| `schema_version` | Snapshot schema version (currently 1) |
| `map_id` | `Cache.mapRef` |
| `captured_at` | ISO-8601 instant (real time) |
| `server_day` | Completed day rollovers (`FactionManager.day`, persisted in `Cache/data.json`) |
| `day_progress_seconds` | Seconds into the current in-game day (`FactionManager.timer`) |
| `complete` | Always true from a successful export. Absence of a faction only means deletion when set |
| `global` | Server-wide aggregates |
| `factions[]` | Per-faction rows |
| `guilds[]` | Per-guild rows |
| `events[]` | Reserved, always empty. The event stream is still owned outside SF |

`server_day` counts server uptime, not calendar days, so it drifts against `captured_at` across downtime. Both ship: the in-game pair is the honest axis for economic continuity, `captured_at` is for display.

### `global`

`faction_wealth`, `pouch_wealth`, `player_bank_wealth`, `liquid_wealth`, `guild_liquid_wealth`, `node_wealth`, `expansion_wealth`, `guild_income`, plus `faction_count`, `guild_count`, `claimed_provinces`, `population`, `active_wars`, `max_wealth_prestige`.

The three wealth figures are **not** pre-summed. `FactionManager.getGlobalWealth()` excludes all personal money, so total money supply is a website decision.

### `factions[]`

| Group | Fields |
|-------|--------|
| Identity | `id`, `founded_at`, `name`, `rgb`, `overlord`, `subjects[]` |
| Wealth | `wealth`, `wealth_breakdown{}`, `bank`, `vassal_wealth` |
| Flows | `net_income`, `inflation_delta`, `trade_power` |
| Prestige | `prestige`, `prestige_breakdown{}`, `rank`, `rank_level`, `rank_up_at`, `rank_down_at` |
| Standing | `prestige_position`, `wealth_position` |
| Territory | `provinces`, `realm_size`, `tier`, `tier_index`, `highest_title` |
| People | `members`, `members_with_vassals`, `settlements`, `population` |
| Assets | `installations`, `forts` |
| Conflict | `wars[]` |

`founded_at` (epoch seconds) exists because faction ids come from `Formatter.formatId(name)` rather than a UUID, and `deleteFaction` frees the name. ProvinceSystem keys identity on `(id, founded_at)` so a recycled name does not splice two unrelated nations into one line.

`prestige_breakdown` is load-bearing, not decoration. The `Wealth` component is a share of global wealth, so a faction's prestige falls when rivals get richer even with its own finances flat. Without the components those dips are unexplainable on a chart.

`rank_up_at` / `rank_down_at` come from `FactionManager.getRankUpAmount`, which is competitive rather than fixed. The website draws threshold lines from these, never from `ranks.yml` minimums.

### `guilds[]`

`id`, `faction_id`, `name`, `type`, `wealth`, `bank`, `expansions`, `trade_power`, `credit_score`, `size`. The only view of the merchant economy separate from state finances.

---

## `wars[]` route slice (shipped)

`WarMapExporter.exportWars()` includes active wars that have a non-empty `campaign_provinces` axis. `WarType.RAID` (legacy/staff raid template wars) and wars with no axis are excluded. **Pillage** goals with a campaign axis **are** exported.

Per-war object (snake_case):

| Field | Meaning |
|-------|---------|
| `id`, `name`, `war_type`, `goal`, `status` | War identity |
| `attacker_leader_id`, `defender_leader_id` | Leader faction ids |
| `belligerents[]` | All participating faction ids |
| `campaign_provinces[]` | Route polyline (province ids) |
| `cursor_index` | Campaign cursor on the line |
| `objective_province_id` | War goal province (optional) |
| `push_target` | Push/hold/counter state |
| `campaign_schedule_index`, `campaign_counter_schedule_index` | Active slot indices |
| `campaign_battle_schedule[]` | Invasion leg slots |
| `campaign_counter_schedule[]` | Counter-push leg (optional) |
| `attacker_capital`, `defender_capital` | `{ province_id, center_x?, center_z? }` |
| `occupied_by_attacker[]` | Province ids in the attacker occupation bulge |
| `occupied_by_defender[]` | Province ids in the defender occupation bulge |

Each schedule slot:

| Field | Meaning |
|-------|---------|
| `schedule_index`, `leg` | `invasion` or `counter` |
| `province_id`, `kind`, `kind_label`, `battle_type` | Battle placement |
| `required`, `status` | `fought` / `next` / `upcoming` |
| `display_name` | UI label for map pin hover |
| `fort_installation_id`, `port_installation_id` | Siege/naval anchor (nullable) |

### `province_data[].occupied_by` (shipped)

`Compiler.exportProvincesToJson` sets `occupied_by` to the occupying **war leader** faction id via `OccupationMapExport.occupierByProvince`. ProvinceSystem remaps those tiles to the occupier nation colour and fills `occupied_held` for labels. This is not inferred from territory diffs.

ProvinceSystem enriches coordinates and renders the campaign line, battle pins, occupier fill, and campaign-line front. Details: [wars.md](./wars.md#web-map-campaign-visualization).

---

## Admin commands

| Command | Action |
|---------|--------|
| `/faction regen` | Queue all nations + upload |
| `/faction fullregen <map>` | Full map regen for map id |

After war schedule or installation changes, wait for the next `map_markers` upload or trigger regen manually so the website picks up new pins.

---

## Related docs

- [wars.md](./wars.md) - campaign system and map visualization rules
- [installations.md](./installations.md) - installation and fort ZOC export
- [province-grid.md](./province-grid.md) - local grid (not uploaded)
- [dev-config.md](./dev-config.md) - `map-reference: dev`, regen hash
