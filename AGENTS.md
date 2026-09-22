# SimpleFactions — agent guide

Instructions for AI agents and contributors adding or changing code in **SimpleFactions** (`simplefactions/`).

Product docs: [docs/README.md](docs/README.md) · shipped vs planned: [docs/roadmap.md](docs/roadmap.md)

---

## How SF is organized (author style)

SimpleFactions favors **domain packages** and **orchestrator managers**, not one-class-per-tiny-helper sprawl.

| Area | Pattern | Examples |
|------|---------|----------|
| Domain state | Few fat types | `Objects/Faction.java`, `War/core/War.java` |
| Player-facing flows | `Managers/*Manager` + `Managers/Inventory/*` | `FactionManager`, `WarView`, `CampaignView` |
| Persistence | `Database/*Data` POJOs + mappers | `Database.java`, `WarMapper` |
| Config / flags | `Cache` static fields | Loaded from `config.yml` |
| Feature domains | Lowercase subfolders | `government/session`, `Guild`, `Map/Provinces` |

**War** uses domain subpackages under `War/` (see layout below). Follow [docs/wars.md](docs/wars.md) for gameplay rules when editing war code.

---

## Package layout

```text
me.Plugins.SimpleFactions/
├── Objects/              # Core domain POJOs (Faction, Bank, …)
├── Managers/             # Orchestration, commands, GUIs
├── Database/             # Gson/load-save DTOs
├── Map/                  # Provinces, grid
│   └── fertility/        # FertilityProvinceResolver (0-100 score for Cooking)
├── government/           # Laws, elections, movement
├── War/
│   ├── core/             # War, Side, WarMapper, declare helpers
│   ├── enums/
│   ├── declare/          # Validation at war declare; staff declare-code gate
│   ├── pathfinder/
│   ├── campaign/
│   │   ├── schedule/     # Campaign battle list build (FB legs, placer)
│   │   ├── zoc/          # Fort/port ZOC indexes
│   │   ├── runtime/      # Hourly battle window, tick, autoresolve; pick/ for installation picks
│   │   ├── vote/         # Battle hour voting, quorum
│   │   ├── admin/        # Staff warschedule tools
│   │   ├── ui/           # Schedule copy, route lore, push projection
│   │   ├── progression/  # Cursor, occupation, peace; postbattle/ for choice/retreat
│   │   └── raid/         # Campaign raids; fight/ + intruder/
│   └── battle/
│       ├── engine/core|capture|win|raid|rules/
│       ├── campaign/     # Launch scheduled campaign battles; warband/ signup/retreat
│       ├── warband/, military/, template/, persistence/, ui/
│       └── …
├── prestige/             # Playtime prestige curve and RPCharacters probe seam
├── installation/         # Installations, bounds, handler
├── vehicles/             # Root listeners/commands; registry/, berth/, maintenance/, battle/
└── SimpleFactions.java   # Bootstrap, listener registration
```

**Rule:** no more than ~**12 sibling `.java` files** in one directory. If you exceed that, add a subpackage named after the subdomain.

---

## Where new code goes

Use this table before creating a file.

| You are adding… | Put it in… | Avoid… |
|-----------------|------------|--------|
| Campaign slot insertion, trim, validator | `War/campaign/schedule` | `War/schedule`, new `*Helper` at `War/` root |
| Fort/port ZOC lookup | `War/campaign/zoc` | Schedule builder file |
| Hourly battle open/close, autoresolve | `War/campaign/runtime` | Mixing with schedule build |
| Vote tally, quorum, postpone | `War/campaign/vote` | New 5-line `*Result.java` (nest in `VoteResults`) |
| Fort/port ZOC operational DTOs | nested in `FortZocIndex` / `PortSeaZocIndex` | Standalone `OperationalFort.java` files |
| Staff `/warschedule` output | `War/campaign/admin` | Chat formatters in engine |
| `/war` command (list, admin subcommands) | `War/core/WarCommandManager` | `Managers/CommandManager` for war logic |
| `/movement admin` list/join/leave/demands/target | `government/movement/admin` | `Managers/CommandManager` |
| GUI lore / schedule debug lines | `War/campaign/ui` | `Managers` unless it is pure inventory layout |
| Path B, axis, dijkstra | `War/pathfinder` | Schedule package |
| Occupation, white peace, push/hold | `War/campaign/progression` | Flat `War/` root |
| Post-battle choice / retreat / walkover | `War/campaign/progression/postbattle` | Mixing into live `engine/core` |
| Live battle instance, sides, join | `War/battle/engine/core` | Flat `engine/` |
| Item durability / province block protection | `War/battle/engine/rules` | Installation protection or raid logic |
| Capture points, markers | `War/battle/engine/capture` | Template package |
| Field/siege/raid win checks | `War/battle/engine/win` or `raid` | One new `*Service` per if-branch |
| Campaign battle display names | `War/battle/campaign` | New `battle/naming` package |
| Warband signup for campaign battle | `War/battle/campaign/warband` | `engine/core` |
| War domain types (War, Side, WarMapper) | `War/core` | `War/` root |
| Lives, pool, casualties | `War/battle/military` | Battle engine |
| Post-battle rewards for fighters | `War/battle/loot` | Handing items out from `BattleEndSupport` or a win check |
| YAML battle templates | `War/battle/template` | Hard-coded in engine |
| Faction ledger / war declare GUI | `Managers/Inventory` | War package UI for non-war commands |
| Staff declare-code gate and chat prompt | `War/declare/WarDeclareCodeService`, `DeclareCodePrompt` | `GatewayClient` calls straight from a GUI class |
| General file logging | `Managers/LogManager` (`logs/log.txt`, `relations.log`, `movement.log`, `civilwar.log`, `war.log`; all wiped by `wipe-log`) | Mixing domain events into chat |
| REST/export shape | Keep stable; change mappers + PS docs together | Renaming JSON fields casually |
| Installation berth / transfer / consent | `vehicles/berth` | `Managers` or `installation/handler` |
| Personal slot / construction limits | `vehicles/berth/VehicleSlotGuard.java`, `vehicles/berth/VehicleConstructionMessages.java` | Inline checks in listeners |
| Vehicle config keys (`per-person`, `ignore-limit`) | `Loaders/VehiclesConfigLoader.java`, `vehicles/VehicleTypeConfig.java` | Hard-coded limits in services |
| Berthable category helper (battle prep) | `vehicles/berth/VehicleCategoryRules.java` | Battle engine importing installation loaders |
| VF construction listener | `vehicles/VehicleIntegrationListener.java` | `installation/handler` |
| Installation radius / bounds helpers | `installation/InstallationBounds.java` + `Loaders/InstallationConfigLoader` | Hard-coded radius in services |
| VF event listeners (berth only) | `vehicles/berth/VehicleTransferListener`, `vehicles/VehicleSpawnListener` | VehicleFramework imports in `installation/` |
| Installation pick persistence / toggle | `War/campaign/runtime/pick/BattleInstallationPickService` | Mixing with vote tally |
| Pick eligibility (kind + control) | `War/campaign/runtime/pick/BattleInstallationPickEligibility` | GUI-only checks |
| In-play union (picks + siege fort) | `War/campaign/runtime/pick/BattleInstallationInPlayService` | Duplicating OR in vehicle service |
| Siege fort from active schedule slot | `War/campaign/runtime/pick/BattleSiegeFortService` | Schedule builder |
| Campaign raid launch GUI | `Managers/Inventory/CampaignRaidLaunchView` | Eligibility or muster logic in view |
| Campaign raid join / muster | `War/campaign/raid/CampaignRaidJoinService`, `raid/fight/CampaignRaidMusterScheduler` | `/raid` command or warband logic in join service |
| Campaign raid warbands | `War/campaign/raid/CampaignRaidWarbandService` (listener nested) | Battle runtime in warband service (71.07) |
| Campaign raid `/raid` command | `War/campaign/raid/RaidCommandManager` | Join validation in command class |
| Campaign raid fight start | `War/campaign/raid/fight/CampaignRaidLaunchService`, `CampaignRaidBattleService`, `CampaignRaidFightScheduler` | Battle runtime in launch service |
| Campaign raid battle end | `War/campaign/raid/fight/CampaignRaidBattleEndService` | Campaign battle outcome side effects |
| Campaign warband signup lock | `War/battle/campaign/warband/CampaignWarbandSignupService.isSignupOpen` | Raid launch or `/raid join` logic in signup service |
| Installation vulnerability gating | `installation/InstallationVulnerabilityService`, `InstallationProtectionListener` | Raid state or repair embargo logic in vulnerability service |
| Installation repair embargo | `installation/InstallationRepairEmbargoService` | Fight-start lock writes (already in `CampaignRaidLaunchService`) |
| Vehicle berth embargo | `vehicles/berth/VehicleInstallationLockService` | Place/break embargo or VF `RepairManager` internals |
| Campaign raid intruder province penalty | `War/campaign/raid/intruder/CampaignRaidIntruderService` | Raid eligibility or warband signup logic |
| Campaign raid source/target eligibility | `War/campaign/raid/CampaignRaidEligibilityService` | Raid launch GUI or `CampaignRaidService` |
| Raid target listing (legacy; prefer eligibility) | `War/campaign/raid/RaidTargetService` | New campaign raid flows |
| Campaign raid state / quota / mutex | `War/campaign/raid/CampaignRaidService` | GUI or battle launch in same class |
| Campaign battle vehicle eligibility | `vehicles/battle/BattleVehicleEligibilityService` | Battle engine core |
| Battle province block protection | `War/battle/engine/rules/BattleProvinceBlockProtectionService` | Installation protection or raid logic |
| Campaign installation pick GUI | `Managers/Inventory/CampaignInstallationPickView` | Pick logic in view |
| Prestige term math or a new prestige input | `prestige/` (curve + probe seam), assembled by `Objects/PrestigeBreakdown` | Any I/O behind a prestige read; `updatePrestige()` runs for every faction on every bank mutation. See [docs/prestige.md](docs/prestige.md) |
| Fertility province lookup | `Map/fertility/FertilityProvinceResolver` | Crop growth cancel (Cooking) |

---

## Naming conventions

| Suffix | Use when |
|--------|----------|
| `*Manager` | Owns lifecycle, maps, listeners (`WarManager`, `BattleManager`, `WarbandManager`) |
| `*Service` | Stateless orchestration over a domain (`WarCampaignService`, `BattleVoteService`) |
| `*Helper` | Pure functions for one workflow (`WarDeclareHelper`) - prefer merging into Manager if &lt;100 lines |
| `*Validator` | Declarative rules, returns result type |
| `*Mapper` | Domain ↔ `Database/*Data` |
| `*View` / `*Creator` | Inventory GUI (`Managers/Inventory`) |
| `*Data` | Gson DTO in `Database/` only |

Do **not** create:

- A new top-level package for one class (`battle/naming`, `battle/util`)
- A separate file for a record used in only one service (use nested record or keep in same file)
- `WarCampaignLogManager`-style duplicates of `LogManager`

---

## War development rules

1. **Schedule changes:** only `CampaignBattlePlacer.placeBattle` (or documented builder phase) mutates leg lists. Read [docs/wars.md](docs/wars.md) campaign schedule sections before editing insertion.
2. **Tests:** any war change should run `mvn test -Dtest="me.Plugins.SimpleFactions.War.**"`.
3. **Brume acceptance:** invasion `713 SIEGE → 705 required` when Greenfort ZOC covers border `709` (no separate `709` field). Off-axis fort ZOC on a non-objective border still replaces that field (`713 SIEGE` chrono `704` → `705 required`, no airfield field).
4. **Persistence:** if you add a field to `ScheduledCampaignBattle` or `War`, update `Database/*Data`, `WarMapper`, and tests in the same change.
5. **Player-facing text:** no em dash (`—`); use `-` or `:`. See workspace rule `no-em-dash.mdc`.
6. **Config:** faction/map toggles go in `config.yml` + `Cache`. Campaign/war-goal tunables go in `war.yml` (`ConfigLoader.loadWar`). Do not hard-code them in services.

---

## Non-war SimpleFactions rules

1. **Managers** own Bukkit listeners when the feature is cohesive; do not add listener classes for one event unless splitting an existing giant manager.
2. **Objects** hold domain state; avoid putting Bukkit API in `Objects` when possible.
3. **Database** stores POJOs; domain logic stays in Managers or feature packages.
4. **Loaders** (`Loaders/`) are for static YAML registries, one loader per file.
5. Match existing indentation and naming in the file you edit.
6. **Movements:** new movements get id `{lowercase founder}_movement` (`_2`, `_3` on collision). Saved ids (including old UUIDs) are kept on load. Staff staging: `/movement admin list|join|leave|demands|target` (`simplefactions.admin`); skip the player join-request flow; do not bypass `canJoin`. `target` sets a CHANGE_LEADER cause wanted leader without online consent.

---

## Refactor policy for agents

| Task type | Approach |
|-----------|----------|
| New feature | Add to correct subpackage per table above; extend existing service if &lt;50 lines |
| Bug fix | Minimal diff; no drive-by repackage |
| Repackage | One batch per PR; tests green; follow layout rules in this doc |
| Merge small types | Nest in parent type (e.g. `VoteResults`) unless a standalone file is clearly warranted |

When moving classes: IDE refactor/move, update main + test + Database imports, grep old package string.

---

## Verify before finishing

```bash
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.War.**"   # war changes
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.vehicles.**"  # vehicle berth changes
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.Map.fertility.FertilityProvinceResolverTest"  # fertility lookup
cd simplefactions && mvn test                                              # broad changes
```

For schedule/pathfinder work, confirm tests include:

- `CampaignScheduleBuilderTest`
- `ProvincePathfinderTest`
- `CampaignScheduleValidator` / Brume-shaped cases

**GUI tests:** test the service and the text builders behind a button, never `ItemStack` construction. `ItemStack.getItemMeta()` needs a live `Bukkit.getItemFactory()`, so a `*Creator` must expose its player-facing text as plain `List<String>` or `String` methods and the tests call those. See `CompanyCreator.buildUpgradeLore` and `PlayerLedgerCreator.buildLore`. Click routing is covered by testing the service the click delegates to; the icons themselves are checked in game.

---

## Related docs

- [docs/wars.md](docs/wars.md) - gameplay spec
- [docs/prestige.md](docs/prestige.md) - prestige terms, the two halves of Members, playtime curve, hot-path rules
- [docs/fertility.md](docs/fertility.md) - province fertility 0-100 lookup
- [docs/fertility-verify.md](docs/fertility-verify.md) - in-game QA matrix for fertility crop growth
- [docs/mercenaries.md](docs/mercenaries.md) - companies for hire, contracts, wages, reputation, config keys
- [docs/installations.md](docs/installations.md) - installations, berth flow, personal limits
- [docs/vehicles.md](docs/vehicles.md) - berths, slots, VF integration
- [docs/campaign-raids.md](docs/campaign-raids.md) - inter-battle raids
- [docs/map-export.md](docs/map-export.md) - map upload and `wars[]` export
- [docs/dev-config.md](docs/dev-config.md) - dev-only config and bypasses
- [docs/planning/campaign-time-dev/](docs/planning/campaign-time-dev/00-index.md) - campaign clock dev batches
- [docs/planning/campaign-retreat/](docs/planning/campaign-retreat/00-index.md) - strategic retreat batches
- [docs/planning/battle-retreat/](docs/planning/battle-retreat/00-index.md) - mid-fight battle retreat batches
- [docs/planning/war-companies/](docs/planning/war-companies/00-index.md) - mercenary gameplay lock; [08-verify.md](docs/planning/war-companies/08-verify.md) - in-game matrix
- [docs/planning/inter-vassal-wars/](docs/planning/inter-vassal-wars/00-index.md) - Phase 8 internal wars lock
- [docs/planning/chronicle-export/](docs/planning/chronicle-export/00-index.md) - chronicle snapshot batches
- [docs/roadmap.md](docs/roadmap.md) - shipped vs planned features
