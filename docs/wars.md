# Wars - automated campaign system

> **Status:** See [roadmap.md](./roadmap.md) for shipped vs planned features.
>
> **Website:** [ProvinceSystem map wars overlay](../../ProvinceSystem/docs/map/wars-on-map.md) · [map-export-schema.json](../../ProvinceSystem/docs/assets/map-export-schema.json)
>
> **Campaign raids:** [campaign-raids.md](./campaign-raids.md)

## Why this exists

Previous-season wars were informal: players arranged fights in Discord, staff sometimes ruled outcomes, and the in-game war GUI was barely used. That caused drama and unfairness.

**v1 automated wars** are fully system-driven after declare. The Discord ticket / one-time **declare code** is a **production gate**, not shipped yet (`war.require_declare_code: false` by default). Staff set battle **rule presets** in templates (lives, friendly fire, keep inventory, durations). Staff place **battle geometry** (spawns, jails, capture points) per scheduled fight via `/battle edit`. Players vote on battle times, sign up via warband join, and the campaign advances on the map without manual organisation.

---

## Design principles

| Principle | Rule |
|-----------|------|
| **Automated** | Campaign route, schedule, progression, goal enforcement, and occupation are system-driven. |
| **Staff-light** | Staff maintain **rule presets** and place battle geometry per scheduled fight; ticket/code gate only in production. No mid-battle rulings. |
| **Transparent** | Campaign line, occupation zones, next battle, votes, and initiative visible in-game and on the web map. |
| **Wars encouraged** | Reparations are rare and attacker-only. White peace and initiative exhaustion avoid punishing failed wars too harshly. |
| **One goal** | One war = one war goal. No per-participant goal picking. |

---

## Declaration flow

Every declare runs the same pre-checks: no existing war with that target, opinion at or below `war.declare_opinion_threshold`, and at least one target member online. The declare code gate sits after those.

### Production

`war.require_declare_code: true` in `war.yml`.

1. Players open a **Discord ticket** (war type, target, goal, belligerents).
2. Staff review, then mint a **one-time declare code** with `/warcode mint attacker defender goal [hours]`. Faction ids come from `/war admin factions [filter]` in game. The code is shown once; a lost code is revoked and reminted.
3. In-game: diplomacy → **Declare War** → the plugin asks for the code **in chat** (type `cancel` to back out). There is no anvil or sign input, so chat is the only free-text path.
4. The code is checked without being spent, and **pins the war goal**: the goal picker is skipped entirely and the leader lands on that goal's own sub-picker (title, subject, relation type, settlement, government) or straight on confirm.
5. On confirm, `WarManager.declareWar` runs. The code is **redeemed only if a war came back**, so a refusal from `WarGoalValidator`, `CampaignDeclareValidator` or `CampaignNavyGate` leaves the ticket spendable.

**Staff bypass:** `simplefactions.admin` skips the code entirely. That bypass is what makes the gate safe to **fail closed** - if ProvinceSystem is unreachable or slow (`war.declare_code_timeout_seconds`), the declare is refused rather than waved through.

### Development / testing

- `war.require_declare_code: false` (the default). Declare war **directly in-game** from the diplomacy GUI; the goal picker opens as normal.
- All goal validation and FSM rules still apply; only the code check is skipped.

**Code properties:** one-time use, expiry, bound to attacker/defender/goal/realm, audit log (`DECLARE_CODE` in the war log, plus `DECLARE_CODE_UNSPENT` if the war exists but redemption failed).

Codes live in ProvinceSystem (`war_declare_codes`), realm-scoped, hashed with no plaintext column. SimpleFactions reaches them through TFMCWeb's gateway, which injects the realm id because the plugin does not know its own. Routes: [ProvinceSystem/docs/identity/tfmcweb.md](../../ProvinceSystem/docs/identity/tfmcweb.md).

---

## War goals (locked)

Generic **conquest** is **not** a goal. The goal defines the political outcome. **One war = one goal**, chosen at declare.

Implementation lock: [planning/war-goals-apply/00-index.md](./planning/war-goals-apply/00-index.md). Phase sequence: [planning/war-goals-apply/01-phases.md](./planning/war-goals-apply/01-phases.md).

**Do not** add a second diplomacy/law/tax engine. Apply calls `RelationManager`, `FactionManager.usurp`, `Faction.applyLaw`, tax handlers, and one movement apply gate.

**War defender** is the **top liege** of the clicked faction. The goal payload may still be a nested vassal, title, or settlement.

### Shared declare blocks

Cannot declare (goal exceptions are in the planning lock) if: same realm (vassal / overlord / nested), ally, **NAP** (treaty overlay), or tributary unless the goal is **subjugate** or **War**. Usurp may target **direct overlord** only.

**NAP** is a diplomacy **treaty overlay** (slot right of the nation icon), not the Ally / Tributary / Subject political type. It can stack with tributary. While it is in effect, **all** declares are blocked, including Subjugate and War against a tributary. Clear it with **No Treaty** first.

**Navy (implemented):** if the generated **invasion** schedule includes a naval slot and the attacker has no **operational port**, declare is rejected (`You need an operational port for a naval path. Source ships before the battle.`). Empty port is allowed. Ships are not counted at declare or Push/Hold. If the next battle after a win is naval and that coalition has no port, they cannot **Push** (must **Hold**).

### Goal list

| Goal | Layer 2 | On attacker win |
|------|---------|-----------------|
| **Tributary** | None | `setRelationForced` tributary (not vassal) |
| **Subjugate** | Subject type (not Integrated; `getWarPickableVassalTypes`) | Chosen vassal type |
| **De jure annex** | Title (show blocked reasons) | Defender-realm provinces in title transfer; **unowned title is not granted** |
| **Transfer subject** | Nested realm faction | `transferSubject` |
| **Usurp** | None | `FactionManager.usurp` (primary title + subjects) |
| **Overthrow** | Movement / leader | Decline demands starts a civil war; apply gate + coup (stub). Not on the nation declare picker |
| **Change law** | Law GUI (movement) | Decline demands starts a civil war; law + Civil War stability. Not on the nation declare picker |
| **Change tax** | Tax pick + chat (movement) | Decline demands starts a civil war; rate + Civil War stability. Not on the nation declare picker |
| **Open market** | None (law ids in war-goal config) | Configured free-trade law + stability |
| **Change government** | Gov ± leadership | Laws + stability |
| **Pillage** | Settlement | Trade-income hit + loot (not a campaign raid) |
| **War** | None | Pickable; no auto-apply (ticket codes later) |
| **Revolt** | None | Staff / no-apply label; civil war uses overthrow / change law / change tax |

**De jure:** own the title, **or** title unowned and you own at least one province in it. No settlements in the title. Prestige must cover incoming land. Rank gate: title at or below attacker rank. Victory is still a single **objective province** (see [Objective province](#objective-province)), not 100% occupation.

**Pillage vs campaign raid:** pillage is a **war type / goal**. Campaign raids stay inter-battle installation assaults ([campaign-raids.md](./campaign-raids.md)).

### On war end

| Outcome | Apply |
|---------|--------|
| Attacker victory | Goal (civil war: movement apply on the restored host) |
| Defender victory | **External:** reparations from attacker (no goal). **Civil war** (`movementId` / snapshot): restore, empty movement, **no** auto reparations |
| White peace / admin | Neither. Civil war: restore, empty movement, **no** auto reparations |

---

## War types (campaign shape)

| Type | Campaign | Battles | End |
|------|----------|---------|-----|
| **De jure / subjugate / transfer / usurp / diplomatic / law** | Border → objective province (and capital push if counter-invasion) | Campaign battles on schedule | Goal applied or reparations / white peace |
| **Pillage (war type)** | Shortest path border → **one settlement** | **One** battle | Pillage apply + war ends (no return battle) |

**Pillage distance:** YAML `range_provinces` (default **3**): settlement within that many provinces of attacker land borders, **or** within that many of sea **and** `hasSeaConnection` between the realms. Disconnected oceans, landlocked attackers, and settlements deeper than that range cannot be pillaged. There is no airborne pillage.

### Campaign raids

**Not a war type.** Between scheduled **campaign** battles, faction leaders may launch **campaign raids** during the [raid window](#battle-day-timeline) on battle day. See [campaign-raids.md](./campaign-raids.md).

**Distinguish from:**

| Term | Meaning |
|------|---------|
| **Campaign raid** (this section) | 19-20 inter-battle installation assault; timer fight; no plugin scoring |
| **Pillage war** | One-battle **settlement** war goal/type (land range or connected-sea range) |
| **Staff `BattleType.RAID`** | Manual template battle with capture points (dev/lore tool; unchanged) |

#### Timeline (battle day, Europe/Paris)

| Phase | Default | Rule |
|-------|---------|------|
| Raid **call** window | 19:00-20:00 | May **initiate** a campaign raid only (`raid_window_start_hour` / `raid_window_end_hour`) |
| Campaign warband signup | **Blocked** 19:00-20:00; **open** 20:00-21:00 | `CampaignWarbandSignupService` |
| In-flight raids | May overrun past 20:00 | Muster (60s) + fight timer (10 min) |

#### Launch and flow

| Rule | Detail |
|------|--------|
| Who | Faction **leader** on a belligerent side |
| GUI | Campaign view **Start raid** → page 1 **source** (own port/airport) → page 2 **target** (enemy installation) |
| Quota | **One raid per coalition side** per `battleDay`; first leader to confirm spends the side quota |
| Mutex | **One active campaign raid** per war at a time |
| Muster | **60s** after confirm; broadcast + `/raid join <id>` |
| Join | **Attacker coalition** only; must **not** already be in any warband |
| Fight | **10 min** timer (`BattleEndReason.TIMER`); no winner scoring |
| Attacker TP | **Source** installation center at fight start |
| Defenders | Title + horn at fight start; **no** teleport; respawn at **target** center |

#### Source and target eligibility

Uses `CampaignRaidEligibilityService` (not installation picks).

| Role | Rule |
|------|------|
| **Source** | Launching faction's **operational** `port` or `airport` (any; pick not required) |
| **Target** | Any enemy **operational** `port`, `airport`, or `fort` |
| **Raid kind** | `NAVAL` (port→port), `AIR` (airport→airport), `FORT` (port/airport→fort); cross-kind invalid |

**Installation picks** control battle vehicle in-play and post-lock intel only. They do **not** limit campaign raid targets.

#### Warbands

| Rule | Detail |
|------|--------|
| Ids | `{raid_slug}_attacker` / `{raid_slug}_defender` (e.g. `harbor_raid_attacker`) |
| Exclusion | Cannot join raid if in **any** warband (including campaign shell) |
| Defenders | Online at fight start + login during raid → defender raid warband if warband-free |

#### Fight rules (`campaign_raid_template`)

| Rule | Value |
|------|-------|
| Capture points | **None** |
| Win condition | **Timer** or all raiders eliminated (defender win); no early end from defender logout |
| Attacker lives | **One each**; death or disconnect = out |
| Defender respawn | **Infinite** at target installation center |
| `keep_inventory` | `true` for raid participants |
| Province fence | **None** |
| Intruders | Attacker-coalition players in **target province** who are not raid participants (or eliminated) take periodic damage + `§cYou are not part of this raid. Leave the area!`; **normal death** (no battle keepInventory) |

Installation **damage gating** and **repair embargo** on the raid target: see [installations.md](./installations.md#campaign-raid-damage-and-repair). Vehicle repair is always allowed. The 48h target lock still blocks new berths at the raid target.

Config (`war.campaign_raid`): `muster_seconds` (60), `duration_seconds` (600), `repair_lock_hours` (48), `installation_repair_embargo_enabled` (true), `intruder_damage_interval_ticks` (10), `intruder_damage_amount` (4).

---

## Participants

Keep from legacy system (repurpose):

- **Attacker / defender** sides with **main participants**
- **Subjects** auto-included on participant side (direct subjects of each main)
- **Allies** via call-to-arms (`/faction accept`, 60s timeout)
- **Mercenary companies** hired by contract on one side, listed with their promised slots. A third kind, neither main nor secondary, and never a belligerent: see [Mercenaries (locked)](#mercenaries-locked) below.
- **No switch sides in war GUI.** Subject independence / rebellion uses the **movement system**, not a war-view button (legacy switch removed 2026-08-20).

**Internal (inter-vassal) wars:** two factions that share a top liege and are **not** on each other's overlord path. Defender is the clicked faction, not the king. The liege is not a participant and is not callable. Lock: [planning/inter-vassal-wars/00-index.md](./planning/inter-vassal-wars/00-index.md).

**Call to arms (all wars):** the caller must be a **main**. The target must be an unjoined ally on that main's ally snapshot (match by faction id). The target must not already be participating, must not be the overlord of a main, must not be nested under an enemy participant, and must not have a top liege who is already a main on either side. Same-realm allies are callable only when those rules hold.

**Declined call to arms:** **-30% stability** (config), decays over time.

**Multiple wars** per faction are allowed in design; implement **one war FSM first**, tag all military commits with **`war_id`** from day one.

**War leaders** (main attacker / main defender faction leaders) can still surrender and offer white peace on the campaign map. Council, leader proposal, or a movement can also force that side: **White Peace** (sticky offer on a chosen war) or **Surrender** (immediate). A rebel civil-war win for those causes applies the same action.

### Militia (locked)

| Rule | Detail |
|------|--------|
| Militia fights only on **that faction's direct land** | Province owner = faction |
| **Overlord militia** does **not** deploy in **vassal** territory | Overlord sends army + levies |
| Battle in **vassal land** | Vassal's **full** military including militia joins; overlord sends non-militia + levies |

### Mercenaries (locked)

A **mercenary company** is a guild-owned band of soldiers for hire. It fights where its contract sends it and is the third kind of thing that can be on a war side, alongside belligerents and their allies. Gameplay lock: [planning/war-companies/00-index.md](./planning/war-companies/00-index.md) - reference doc: [mercenaries.md](./mercenaries.md).

| Rule | Detail |
|------|--------|
| **Hired by contract** | A company joins a side only through a signed contract naming the war, the side, and the promised slots |
| **Listed with promised slots** | The war screen shows the company and the slot count it promised, not a regiment row |
| **Never a belligerent** | A company is not a participant: it has no war goal, no initiative, no call to arms, and cannot be declared on because it fought |
| **Never a party to a peace deal** | White peace, surrender, and goal apply ignore companies entirely; a contract ends on its own terms |
| **The host faction stays out** | The company's **host faction** (the one its guild belongs to) does **not** become a participant because its citizens fight for hire |

**Loyalty:** a company can never fight **its own host faction**, whichever side the contract is on. A rostered mercenary who is a plain, **non-government citizen of another faction may fight their own faction**; a leader or council member of that faction may not. A contract that becomes illegal (an ally joins on the opposing side, an overlord bond forms) terminates with no refund and no reputation change, and the days already served are still paid.

---

## Campaign route generation

Uses a new **`ProvincePathfinder`** module (not embedded in `ProvinceManager` trade pipeline). Graph: province adjacency from `province_neighbors.json` + terrain from `provinces.txt`. Edge costs: terrain-weighted (plains preferred over mountains), same philosophy as trade `terrain-modifiers`.

### WATER vs SEA (locked)

| Terrain | Pathfinder rule |
|---------|-----------------|
| **WATER** (rivers, lakes) | Always crossable on **land passes** at normal terrain cost (~`0.75`). Routes may cross water so paths do not zigzag around rivers. |
| **SEA** (ocean) | **Impassable** on land pass 1. Only traversable in **pass 2** (naval/amphibious). |

**Note:** `Province.isSea()` returns true for both WATER and SEA. Pathfinder code must use `terrain == Terrain.SEA` for ocean logic, not `isSea()`.

### Neutral provinces (locked)

**Neutral** splits into two cases for pathfinding:

| Type | Definition | Route UI |
|------|------------|----------|
| **Wilderness** | Province has no owner (`owner == null`) | Gray tiles on campaign route |
| **Liege transit** | Internal war only: owner is the war's top liege, or shares that top liege, and is **not** a belligerent | Gray tiles; land-pass allowed like wilderness; not occupied |
| **Foreign nation** | Owner exists, is not a belligerent, and is not liege transit | Gray tiles on campaign route |

Belligerent set = attacker side + defender side, including subjects and called allies.

| Pass | Wilderness | Liege transit | Foreign nation |
|------|------------|---------------|----------------|
| **1** Land | Allowed at normal terrain cost | Allowed | Blocked |
| **2** Sea | N/A (land tiles irrelevant on sea hops) | N/A | Blocked on **land** tiles; sea tiles always allowed |

`war.pathfinder.neutral_penalty` is no longer used by the pathfinder fallback chain.

### Route priority (locked)

Run passes **in order**; first pass that finds a route wins:

1. **Land campaign** - land + WATER + wilderness + liege transit (internal); no SEA; foreign nations blocked 
2. **Sea campaign** - SEA hops between coastal belligerent provinces; foreign-owned land blocked 

No sea-first routing; land (including through wilderness) is always preferred when possible.

**Future (not v1):** generate 2–3 alternative campaigns (e.g. long land vs risky naval); attacker chooses after declare.

### Step A — border start (conquest / de jure / subjugate)

**Intent:** shortest invasion corridor from **attacker–defender border** toward **objective province**, not capital-to-capital through messy borders.

1. For each province **B** on the **defender** side of the border (defender-owned adjacent to attacker-owned), pathfind **B → objective** using passes 1→2.
2. Pick **B** with minimum total cost → **`campaignStartProvinceId`** (first battle / invasion entry).
3. If no land border: use defender provinces adjacent to **sea** as **B** candidates; pathfind **B → objective** (sea landing on enemy soil).

### Step B — full campaign axis (locked)

Shipped **`B → objective`** only. A **full axis** is built at declare / `warpath` regen:

```text
← ATTACKER [ attacker capital … … atk border … B … … objective ] DEFENDER →
 ↑
 cursor_index (first battle)
```

**B** is the first **defender-owned** province on the invasion route (first battle on enemy soil). The attacker-owned border province remains on the axis but is not **B**.

1. **Step A:** pick invasion entry **B** (defender-side; see above).
2. **Capital-closer rule:** if defender/subject **faction capital** is closer from **B** than the regional objective (path cost), **capital replaces** `objectiveProvinceId`; rebuild right segment.
3. **Left segment:** pathfind **attacker faction capital → B** (full path, always at declare).
4. **Right segment:** pathfind **B → objective** (inclusive).
5. **`campaign_provinces[]`** = merged left + right (**B** once).
6. **`cursor_index`** = index of **B** in the array (**middle**, not `0`).
7. **`campaignStartProvinceId`** = **B**.

**Counter-push:** defender fights **leftward** on the **existing** line toward attacker capital - no polyline append at choice time.

### Pillage war type route (shipped)

`WarGoalType.PILLAGE`. Shortest path: attacker border (or connected-sea landing) → **one settlement**. One battle at the settlement, empty counter. Navy gate still applies if the natural path has a naval slot. Distinct from [campaign raids](./campaign-raids.md). Apply and campaign populate: [planning/war-goals-apply/06-phase-5.md](./planning/war-goals-apply/06-phase-5.md).

---

## Objective province (locked)

One province represents the de jure / vassal / regional target for capture and recake battles:

| Condition | Pick |
|-----------|------|
| Title/region **capital** in set | Capital province |
| Else **largest settlement** | Province with largest settlement; **capital settlement beats non-capital**; population/size tiebreaker |
| No settlements | **Geometric center** of title/region provinces |

All capture/recapture battles occur **at this province**. No multi-province occupation requirement to win.

When **capital itself** is the war target, capital province is the objective.

**Capital vs regional target (locked):** for subjugate / transfer / de jure wars, if the defender (or subject) **faction capital** is strictly **closer** from the campaign border start than the regional objective picked above, **faction capital replaces** the regional objective for both **`objectiveProvinceId`** and capture/recapture battles. De jure title capital in-set already wins via the table above; this rule covers cases where settlement/centroid pick would otherwise skip a nearer faction capital.

---

## Campaign progression

### Battle cadence

- **One campaign battle per day** (config) inside **battle window** (default **21:00-24:00** Europe/Paris; see [Battle day timeline](#battle-day-timeline)).
- Exact hour chosen by [voting](#battle-scheduling--voting).
- **First invasion land battle** is at border **B**, unless an enemy fort ZOC covers B (and B is not the objective). Then the siege is first (fort home, possibly off-axis with `chronologyProvinceId`).
- **Two battle lists** are built at declare (see [Campaign battle schedule](#campaign-battle-schedule-locked-70)), each trimmed to per-goal **`max_battles_per_leg`**, then fought via the **active** leg index for the current `pushTarget`.
- Fort ZOC covering a tile → **siege first**. The field that tile would have had is omitted unless that tile is the **objective** (siege then required field).
- **Field cadence:** default **`war.battle_cadence.provinces_between_battles: 3`**. Each leg walks its segment once; place a non-required **field** slot when `offset % N == 0` from the leg start (`N` = config value). This is a **grid from leg start**, not step-since-last-battle counting.
 - **Invasion:** `cadenceOrigin = borderIndex` (`cursor_index` at declare); offset = `abs(axisIndex - borderIndex)`.
 - **Counter:** `cadenceOrigin = borderIndex - 1` (first tile **left** of border **B**); offset = `abs(axisIndex - (borderIndex - 1))`.
 - Sieges, naval, and required terminal slots do not suppress cadence on the same province when rules both apply.

**Target feel:** default **4** slots per leg after trim (up to **8** total across both directions). Example: invasion 4 slots / counter 2 slots → starting fuel **6 / 3** at `initiative_factor` **1.5**. Back-and-forth spends fuel until exhaustion or victory.

### Campaign battle schedule (locked ; updated )

At declare, after the campaign axis is set:

```text
built = CampaignScheduleBuilder.buildAll(war, axis, cursorIndex, objectiveIndex, capitalIndex, fortIndex, portIndex)
invasionTrimmed = CampaignScheduleTrimmer.trimInvasion(built.invasion(), max_battles_per_leg[goal])
counterTrimmed = CampaignScheduleTrimmer.trimCounter(built.counter(), max_battles_per_leg[goal])
war.campaignBattleSchedule = invasionTrimmed
war.campaignCounterSchedule = counterTrimmed
war.campaignScheduleIndex = 0
war.campaignCounterScheduleIndex = 0
initiativeAttacker = ceil(invasionTrimmed.size × initiative_factor)
initiativeDefender = ceil(counterTrimmed.size × initiative_factor)
```

Only **`CampaignBattlePlacer.placeBattle`** mutates a leg list. Each slot is **inserted** at the correct axis position (fight order = list order = geographic order along that leg's segment). Same-province tie-break: siege → optional field → required field.

#### FB legs

| Leg | Segment | List order |
|-----|---------|------------|
| **Invasion** | FB province → DT (defender target) | Chronological along axis (increasing index) |
| **Counter** | `axis[cursorIndex - 1]` → AC (aggressor capital) | Chronological along axis (decreasing index); never includes border **B** |

**FB** = first invasion battle (required **FIELD** at border **B** = `campaign_provinces[cursor_index]`). If a **NAVAL** battle happens before landing, NAVAL is list index **0** and the FB field at **B** stays at index **1**.

**Example (Brume vs Lantan):** axis `452, 782, 758, 757, 672, 709, 713, 705`. Invasion list: `713 SIEGE` → `705 required` when Greenfort ZOC covers `709` (no `709` field). Optional `795 NAVAL` prefix when harbour covers sea. Geographic GUI row: `452 - 782 - 672 - 713 siege - 705`.

#### Two legs (axis walk)

| Leg | Axis walk | First battle province |
|-----|-----------|----------------------|
| **Invasion** | border → objective | `campaign_provinces[cursor_index]` (border **B** / FB) |
| **Counter** | `borderIndex - 1` → aggressor capital | First slot **left of border** (border itself is invasion-only) |

Natural slot rules (field cadence, fort siege, port naval) apply on **each leg independently** with coalition-appropriate direction. New wars do **not** emit `NAVAL_INVASION` slots (enum kept for old saves / display only).

#### Two layers: template vs display

| Layer | Purpose | Values |
|-------|---------|--------|
| **`BattleType`** | Win rules (capture vs contest) | `FIELD`, `SIEGE` (no `BattleType.NAVAL`) |
| **`CampaignBattleKind`** | GUI label / staff setup expectations | `Field Battle`, `Siege`, `Naval Battle`, `Naval Invasion` (legacy saves) |

Objective battles use **field** template and **Field Battle** display (required slot, never trimmed). Naval kinds use **`FIELD`** template plus **`navalVariant`** on the battle (staff sea spawn layout).

#### Natural slots (before trim)

| Slot | When | `BattleType` | Display |
|------|------|--------------|---------|
| **Border / FB** | Phase 1 at border **B**: optional **FIELD**, unless an enemy fort ZOC covers B (and B is not the objective). Then the siege is the first land battle, whether the fort home is on-axis, off-axis, or is B itself. | `FIELD` or `SIEGE` | Field Battle or Siege |
| **Siege** | Axis passes through province in operational fort ZOC; fort controller is enemy. Optional field at the same fight-order tile is omitted except at the **objective**, where siege and required field both remain. | `SIEGE` | Siege |
| **Naval** | Invasion: enemy port sea ZOC blocks sea on axis segment AC→DT; prepended at index 0 | `FIELD` + `navalVariant` | Naval Battle |
| **Field** | Leg walk: `offset % provinces_between_battles == 0` from leg start (non-terminal provinces) | `FIELD` | Field Battle |
| **Objective** | Always at `objectiveProvinceId` | `FIELD` | Field Battle (`required`) |

Siege fires **once per fort** on the line. The slot **`provinceId`** is the **fort home province** (e.g. Greenfort → **713**); **`fortInstallationId`** names the fort. When the fort home is **off the campaign axis**, **`chronologyProvinceId`** stores the axis tile where ZOC was entered; fight order and GUI geographic sort use that tile. That siege **replaces** the optional field on that chronology tile. On-axis fort homes sort by `provinceId` as before. The invasion leg never schedules battles after the objective province. Overlapping ZOC for **schedule** identity: **oldest** operational fort wins per province (`completedAt`, then id). Occupation still requires **all** covering forts to be taken. The GUI **First Battle** marker is the first non-naval invasion slot (landing field, or the replacing siege).

#### Port sea ZOC (shipped ; updated )

Sea zones use **`Terrain.SEA`** only (not `Province.isSea()` / rivers). Invasion sea scan walks axis indices **0 → objective** (AC toward DT) for contiguous **SEA** runs.

| Rule | Detail |
|------|--------|
| **Port coverage** | BFS from **SEA neighbours of the port land province**; expand only across ocean tiles; radius **`war.port_sea_zoc_radius`** (default **2**) |
| **Blocking port** | Operational port whose owner coalition is **enemy of aggressor** at declare and whose coverage intersects any sea province in the run |
| **Naval slot** | One **`NAVAL`** per blocking port at the **first sea province on the axis sea run** (`portInstallationId` on slot); invasion leg prepends at index 0 (before FB field); friendly port covering the run → no naval slot |
| **No enemy port** | Sea on axis alone does **not** insert a naval slot |
| **Landing** | Amphibious landing is the FB **FIELD** at border **B**; no new **`NAVAL_INVASION`** slot is emitted |
| **Overlap** | Oldest operational port wins per sea province (`completedAt`, then `id`) |

#### Trim priority

Each leg is trimmed **independently** via `CampaignScheduleTrimmer.maxBattlesPerLegForGoal`:

| Leg | Policy |
|-----|--------|
| **Invasion** | Drop optional **FIELD** from **DT side** first; never drop required objective; protect index **0** (first battle); if index 0 is **NAVAL**, also protect index **1** (first land battle). Then drop legacy `NAVAL_INVASION`, **NAVAL**, **SIEGE** if still over cap |
| **Counter** | Drop optional **FIELD** from border-adjacent side first (lowest axis index on counter segment) |

Config key **`max_battles_per_leg`** (default **4** per goal). Legacy **`max_battles`** is a deprecated alias with the same per-leg semantics.

#### War-time fort control

Installation DB ownership does **not** change. `fortControllers` on the war tracks who **controls the ZOC**. Siege winner becomes controller. Counter-push through enemy-held ZOC **inserts a siege slot** on the **active** leg schedule at the active index before the next battle resolves.

#### Progression (active leg)

| `pushTarget` | Active schedule | Active index |
|--------------|-----------------|--------------|
| `TOWARD_OBJECTIVE` | `campaignBattleSchedule` | `campaignScheduleIndex` |
| `TOWARD_AGGRESSOR_CAPITAL` | `campaignCounterSchedule` | `campaignCounterScheduleIndex` |
| `RETAKE_OBJECTIVE` | invasion schedule | `campaignScheduleIndex` |

- `nextBattleProvince(war)` → active leg `currentSlot` province (after re-siege insert check).
- Each fought campaign battle increments the **active** schedule index and `campaignBattlesFought`. Switching `pushTarget` does **not** reset the other leg's index.
- `cursorIndex` still advances on winner **Push** per existing rules.

#### Persistence (war JSON fields)

| Field | Role |
|-------|------|
| `campaignBattleSchedule` | Invasion leg slots (border → objective) |
| `campaignScheduleIndex` | Next slot index on invasion leg |
| `campaignCounterSchedule` | Counter leg slots (border − 1 → aggressor capital) |
| `campaignCounterScheduleIndex` | Next slot index on counter leg |
| `initiativeAttacker` | Starting fuel from invasion leg slot count (persisted at declare) |
| `initiativeDefender` | Starting fuel from counter leg slot count (persisted at declare) |
| `fortControllers` | installation id → coalition key |
| `wartimeInstallationOwners` | installation id → original faction id (snapshot before wartime transfer) |

Slot shape: `provinceId` (axis tile where the battle is fought), `kind`, `required`, optional `fortInstallationId` (siege), optional `portInstallationId` (naval).

### Cursor movement (after each fought battle)

Cursor moves only when the **battle winner chooses Push** after the battle. **Hold** keeps the cursor in place and auto-proposes white peace.

| Winner choice | Cursor |
|---------------|--------|
| **Push** | Advances along the current `pushTarget` (toward objective, toward aggressor capital, or retake objective) |
| **Hold** | Unchanged; white peace proposed to the loser |

### Initiative (locked; updated )

| Rule | Default |
|------|---------|
| Attacker starting fuel | `ceil(invasion_leg_slot_count × initiative_factor)` |
| Defender starting fuel | `ceil(counter_leg_slot_count × initiative_factor)` |
| Empty counter leg | Defender fuel **0** |
| `initiative_factor` | **1.5** (config) |
| Per-goal `max_battles_per_leg` | **4** each (`DE_JURE_ANNEX`, `SUBJUGATE`, `TRANSFER_SUBJECT`) |
| **`initiativeHolderCoalition`** | Which coalition may schedule the next campaign battle and is battle-offensive (starts **aggressor** at declare) |
| Each **fought** battle | **Battle offensive coalition** (holder at battle start) loses **1** fuel when the battle ends |
| **Winner** | Becomes initiative holder after post-battle choices resolve (unless Hold assigns attack to the loser) |
| **Postponed** battle (low votes) | **No** fuel spent; holder unchanged |
| Coalition at **0 fuel** while holding initiative | Cannot schedule until they win initiative back |

**Legacy load:** wars declared before without `campaignCounterSchedule` in JSON default defender fuel to the invasion-based symmetric value; re-declare to rebuild both legs.

**Removed:** symmetric fuel from a single schedule for both coalitions. Re-siege inserts do not recompute fuel.

### Post-battle choice (every battle)

After **every** campaign battle, the **winner's war leader** chooses on the campaign view (or admin `battlechoice`):

| Winner choice | Result |
|---------------|--------|
| **Push** | Continue the offensive; cursor moves per `pushTarget`; voting reopens |
| **Hold** | Front held; winner auto-proposes white peace; **loser** chooses **Attack** or **Accept peace** |

**Loser response after Hold:**

| Loser choice | Result |
|--------------|--------|
| **Attack** | Loser gets initiative at the held front; voting reopens |
| **Accept peace** | White peace; war ends with no goal |

**Defaults at deadline:** winner **Push**; loser **Attack** after Hold.

**Mandatory Hold (moment C):** if the battle winner cannot field an offensive army at the **next** battle province after a Push (troops must be ready immediately), the winner is treated as having chosen **Hold** - the loser gets **Attack** / **Accept peace** without a Push/Hold prompt.

While `postBattleChoicePhase` is not `NONE`, **no** new battle may be scheduled (vote close blocked until choice or deadline).

### Declare gate (locked)

War declare is blocked unless the **declaring attacker faction** has at least **1 offensive manpower** from live military (`Military.getManpower(true)`). Regiment types count when marked `offense: true` in `regiments.yml` (levy or professional). No first-battle province or `canAttack()` check at declare.

### Battle offensive forfeit (locked)

At **scheduled battle time**, if the **battle offensive coalition** (initiative holder) cannot `canAttack()` at that province, they **forfeit** the battle: the opponent wins with no casualties, then normal post-battle choice rules apply. The same forfeit applies in the military walkover chain when the initiative holder cannot attack.

### White peace proposals (locked)

Symmetric reach checks per **coalition** via `CampaignCapabilityService.canReachTarget`:

| Coalition | Capitulation target (axis steps from cursor) |
|-----------|-----------------------------------------------|
| Aggressor | Objective province index |
| Defender | Aggressor capital index |

| Situation | Result |
|-----------|--------|
| One coalition cannot reach its target | That coalition **auto-proposes white peace** |
| Other war leader **accepts** | **White peace** - war ends, no goal, no reparations |
| **Both** coalitions auto-propose (includes **neither can attack**) | **Automatic white peace** |
| Neither coalition can mount next offensive (VOTING/SCHEDULED) | **Automatic white peace** (offensive stalemate) |
| Hold peace proposal active | Winner's coalition stays flagged until next battle ends |

Persist `whitePeaceProposedByAttacker` / `whitePeaceProposedByDefender` (coalition flags); recalc after each choice resolution and walkover chain.

### Both sides initiative = 0

**Automatic white peace** via mutual auto-proposal (see above) — no goal, no reparations.

### Regional retake loop

1. Attacker wins at **objective** → objective held (attacker occupation).
2. Next battle: defenders **retake** at objective (defender offensive).
3. **Defenders win** → objective stays defender; cursor stays at objective; attackers must attack objective again.
4. **Attackers win retake attempt** → **attacker victory** (war ends; no cursor rollback).

Capital as objective: capital battle won → **auto victory** (no retake loop). Symmetric rule: aggressor wins at **defender capital** → attacker victory; defender wins at **attacker capital** → defender victory. Failed retake at objective (attackers win while `retake_objective` is active) → attacker victory.

---

## Campaign GUI (locked)

Primary player surface for campaign line, post-battle Push/Hold choice, white peace accept, and battle hour voting. **GUI-first** for campaign choices.

### Navigation

War list → War view → **Campaign** button → **Campaign view**.

### Route row

**Source of truth:** `campaignBattleSchedule` + `campaignCounterSchedule` on war JSON. The route row shows **only** scheduled battle slots - never axis provinces without a slot.

| Rule | Detail |
|------|--------|
| **Order** | Geographic: all slots from both legs merged and sorted by `campaignProvinces` index ascending (attacker-cap side left, defender objective right) |
| **Row layout** | Single row, inventory slots 10-18 (max 9 items); no pagination |
| **Cap** | `max_battles_per_leg` hard max **4** per goal at config load (max 8 battle items total) |
| **Both legs visible** | Full war plan at declare, regardless of active `pushTarget` |
| **First-battle marker** | Below the slot at border **B** (`campaignProvinces[cursorIndex]`; invasion schedule index 0 when tied) |
| **Axis fields** | `campaign_provinces[]` / `cursor_index` also drive map line and cursor push |

Schedule-only: never render axis provinces without a persisted slot. No `Counter-push schedule` lore.

### Concrete legend (viewer-relative)

| Material | Meaning |
|----------|---------|
| **Blue concrete** | Province owned by **your** belligerent coalition (upcoming slots) |
| **Red concrete** | Province owned by **enemy** belligerent coalition (upcoming slots) |
| **Green concrete** | **Next battle** on the **active** leg's current schedule slot |
| **Gray concrete** | **Fought** slot (`index < activeIndex` on that leg, not conceded) |
| **Gray concrete** + **Retreated** lore | **Conceded** slot (`concededScheduleSlots` key on that leg/index) |

Naval kinds use trident / iron sword icons instead of concrete when applicable.

**Not de jure.** Use **belligerent territorial ownership**. Neutral provinces on the line: **red** for both sides (v1).

Route row lists **all** slots from **both** legs (invasion then counter). Green concrete / "Next battle" lore follow the **active** leg for the current `pushTarget`. Fought slots stay visible with **Fought** lore and gray styling. Conceded slots show **Retreated** lore (checked before fought index).

### Display names

Player-facing title per schedule slot (GUI item name and export `display_name`):

| Kind | Pattern |
|------|---------|
| Field | `{ordinal}Battle of {location}` |
| Siege | `{ordinal}Siege of {location}` |
| Naval / invasion | Same as field template; kind shown in lore |

**Location** resolution: settlement name → fort name → county title → `Wilderness`.

**Ordinal** at render/export time:

```text
ordinal = locationBattleCounts[key] + 1 + count(previous slots in SAME leg with same location key)
```

Siege slots use `fort:{installationId}` as the location key. Two scheduled fields at the same settlement before any are fought → `Battle of Lanbury`, then `Second Battle of Lanbury`. Implemented in `BattleNamingService.resolveScheduledDisplayName`.

### Leader interactions

| Situation | Campaign view |
|-----------|----------------|
| Winner choice pending | **Push** / **Hold** buttons (slots 40-41) |
| Loser response after Hold | **Attack** / **Accept white peace** buttons (slots 42-43) |
| Pushed coalition war leader during voting | **Retreat** (slot **46**); confirm concedes active schedule slot |
| War leader (no choice pending) | **Surrender** (slot 47) |
| Enemy white peace proposed | **Accept peace** (slot 48) when eligible |
| White peace proposed | Other war leader **Accept white peace** button |
| Both auto-propose | Automatic white peace |
| Faction leader (belligerent) | **Installations** pick entry (slot **33**); post-lock enemy intel book (slot **34**) |
| Fort / objective / capital | Lore tags; scheduled battle kind (**Field Battle** / **Siege** / naval kinds) on route provinces for **both** legs; siege provinces show enchant glint |

Admin **`/war admin status`** and **`/war admin schedule`** output include invasion and counter schedule indices and slot lists.

Voting hour toggles and schedule info: Campaign view slots **28-32** (hour multi-select), info book slot **4**, autoresolve propose slots **49-51**.

---

## Occupation map (locked)

Each **won campaign battle** adds explicit province(s) to the occupier's zone (**not** a single snake: a natural **bulge / front**). Implemented in `OccupationService`.

| Field | Meaning |
|-------|---------|
| `occupied_by_attacker[]` | Province ids tinted attacker-held |
| `occupied_by_defender[]` | Province ids tinted defender-held |
| `objective_province_id` | Capture/recapture pin |
| `campaign_provinces[]` | Campaign polyline |
| `cursor_index` | Index into campaign line |
| `last_battle_occupied[]` | Provinces added by last battle (for chronicle / UI) |
| `whitePeaceProposedByAttacker` | Attacker auto-proposed white peace (unreachable capitulation) |
| `whitePeaceProposedByDefender` | Defender auto-proposed white peace |

**Website (shipped):** `occupied_by_*` on `wars[]` plus `province_data[].occupied_by`. ProvinceSystem remaps those tiles to the occupier nation colour (slightly greyer fill) and uses `occupied_held` for labels. The campaign line uses `occupied_by_attacker` to advance the dotted-line front. Chronicle events are owned elsewhere. See [map-export.md](./map-export.md) and [wars-on-map.md](../../ProvinceSystem/docs/map/wars-on-map.md).

**Campaign GUI (in-game):** route block colors use **belligerent territorial ownership**, not de jure title claims and not `occupied_by_*` bulge lists.

**Per-battle rule (locked):** winning battle **occupies** the battle province and qualifying adjacent enemy tiles (bulge front), then exports.

---

## Battle scheduling & voting

> **Shipped:** battle scheduling, voting, raid window, installation pick lock at vote close, and strategic retreat during voting.

All clock times under `war.battle_schedule` use **Europe/Paris** hours in shipped `war.yml` (CET/CEST intent):

| Key | Default | Role |
|-----|---------|------|
| `defender_choice_deadline_hour` | 12 | Hold / counter-push / white peace deadline on battle day; no choice → auto **Hold** |
| `vote_close_hour` | 16 | Hour vote tally on battle day; **installation picks lock** at same instant |
| `raid_window_start_hour` / `raid_window_end_hour` | 19 / 20 | Inter-battle raid window (campaign raid launch) |
| `window_start_hour` / `window_end_hour` | 21 / 24 | Fightable hours on battle day |

**Validation:** `vote_close_hour` < `raid_window_start_hour` <= `raid_window_end_hour` < `window_start_hour` <= `window_end_hour` <= 24.

- **Vote open:** when a next battle is pending (declare or after prior battle end); battle province not required. Installation picks editable in parallel.
- **Vote close:** `vote_close_hour` on battle day → pick hour, postpone, or autoresolve; installation picks frozen.
- **First battle day:** calendar day **after** declare (voting may start at declare).
- Valid battle slots: one per full hour in the battle window (e.g. 21, 22, 23, 24).
- **Eligible voters:** **online** members of participating factions (main + subjects + called allies on that side).
- Each player selects **all hours they can attend** (multi-select).

**Pick hour:** maximize `min(attacker_votes(H), defender_votes(H))`; tie → **earliest** hour.

### Quorum

Config under `war.battle_voting`:

| Key | Default | Role |
|-----|---------|------|
| `min_players` | 4 | Minimum distinct voters (any hour) |
| `require_smallest_side_full` | true | Smaller side must have 100% of **eligible members** represented |
| `pass_if_either` | true | Pass if **either** threshold met |
| `dev_min_players` | (optional) | Test-server override when key explicitly set; lower than `min_players` lowers quorum threshold. Remove before prod (dev-config.md). |

### Low turnout

| Situation | Resolution |
|-----------|------------|
| Quorum not met at `vote_close_hour` | **Postpone 1 battle day** (no initiative spent) |
| On postpone | `battleDay` +1; **votes persist**; stay in `VOTING` until next close |
| **Autoresolve** | Only if **both war leaders** agree (separate from white peace) |

### Strategic retreat

> **Shipped:** pushed coalition war leader may concede the active schedule slot during `VOTING` (before vote close).

| Rule | Detail |
|------|--------|
| **Who** | War leader of the **pushed** coalition (`defender` on invasion push; `aggressor` on counter-push) |
| **When** | `battleSchedulePhase == VOTING`, before `vote_close_hour`, no post-battle choice pending |
| **Push targets** | `toward_objective` and `toward_aggressor_capital` only (not `retake_objective`) |
| **Cost** | No initiative/fuel spent; not a battle (`campaignBattlesFought` unchanged) |
| **Effect** | Pusher wins the active slot; auto-push (no Hold prompt); siege slot flips fort controller; occupation applied |
| **Votes** | Hour votes persist; each confirm concedes one slot; phase stays `VOTING` until normal vote close |
| **GUI** | **Retreat** button (slot 46) + confirm; route lore **Retreated** on conceded slots |
| **Persistence** | `concededScheduleSlots[]` keys: `invasion:0`, `counter:1`, etc. |

Mid-fight surrender during a **started** campaign battle is separate: see **Battle retreat** below (warband leader, `/warband retreat`).

### Persistence

| Field | Role |
|-------|------|
| `battleSchedulePhase` | `IDLE`, `VOTING`, `SCHEDULED`, `AUTORESOLVE_PENDING` |
| `battleDay` | UTC calendar day of current slot |
| `scheduledBattleAt` / `scheduledBattleHour` | Chosen fight time |
| `scheduledBattleProvinceId` | From `resolveNextBattleNodes` at vote close |
| `battleVotes` | UUID → selected hours |
| `autoresolveProposedByAttacker/Defender` | Dual-leader autoresolve flags |
| `postponementsThisCycle` | Debug counter |
| `postBattleChoicePhase` | `NONE`, `WINNER_PUSH_HOLD`, `LOSER_ATTACK_PEACE` |
| `postBattleChoiceResolved` | Choice locked (deadline defaults applied) |
| `initiativeHolderCoalition` | `aggressor` or `defender` coalition key |
| `pushTarget` | `toward_objective`, `toward_aggressor_capital`, `retake_objective` |
| `defenderChoiceResolved` | Legacy alias of `postBattleChoiceResolved` (v2 saves) |
| `forceQuorumNextClose` | Dev-only: next admin/tick close bypasses quorum (dev-config.md) |
| `battleInstallationPicks` | Faction id → installation ids committed for current battle day |
| `battleInstallationPicksBattleDay` | UTC date the pick set applies to; must match `battleDay` when locked |
| `concededScheduleSlots` | Leg/index keys for slots conceded via retreat (`invasion:0`, `counter:1`) |
| `wartimeInstallationOwners` | installation id → original faction id before wartime transfer |

### Battle day timeline

On each **battle day**, phases run in this order (defaults from `war.battle_schedule` in `war.yml`):

| Phase | Default (Europe/Paris) | Config key |
|-------|------------------------|------------|
| Vote + installation picks open | — | From declare / prior battle end |
| Defender choice deadline | 12:00 | `defender_choice_deadline_hour` |
| **Vote close + installation lock** | 16:00 | `vote_close_hour` |
| **Raid window** | 19:00-20:00 | `raid_window_start_hour`, `raid_window_end_hour` |
| **Campaign battle window** | 21:00-24:00 | `window_start_hour`, `window_end_hour` |

Raids run **before** the main campaign battle on the same battle day.

### Installation picks

Faction leaders commit installations for the current battle day from the campaign GUI (**Installations** button, slot **33**). See [installations.md](./installations.md#campaign-installation-picks) for vehicle berth interaction.

| Rule | Detail |
|------|--------|
| Who picks | **Faction leader** only; each coalition faction picks **independently** |
| Pickable kinds | **`port` and `airport` only** |
| Territory | Province must be under your coalition's **control** (not enemy-occupied; occupation bulge + de jure ownership) |
| Forts | **Not pickable**; active **siege** schedule slot puts the owning faction's fort emplacements in play without a pick |
| Defender ZOC port | On a current `NAVAL` / `NAVAL_INVASION` slot, `portInstallationId` is **auto-committed** for the defender war leader and cannot be unpicked (`REJECTED_ZOC_PORT`). Other pickable ports and airports still toggle. |
| Lock | Same instant as vote close (`vote_close_hour`). After lock: **no berth or unberth** on in-play installs (committed picks, defender ZOC port, siege fort). Ports not in play stay open. |
| Empty pick | Nothing in play for that faction (no berthable vehicle pool for campaign battles) |
| Pre-lock enemy view | **Hidden** |
| Post-lock enemy view | Enemy intel book (slot **34**) shows per-faction committed lists |
| Reset | Cleared when `battleDay` advances |

**Vehicle in-play:** berthable vehicles at a **committed** port/airport, the defender **ZOC port**, **or** the active siege `fortInstallationId` for the owning faction. Trains and other non-berthable types follow rules. See [installations.md](./installations.md#campaign-battle-vehicle-eligibility).

After vote close, `VehicleInstallationLockService` blocks berth and unberth on those in-play installs. Raid/vulnerability embargo is unchanged.

### Runtime

- **UTC scheduler:** `BattleScheduleTickService` polls every minute; at `defender_choice_deadline_hour` applies post-battle choice defaults; at `vote_close_hour` runs tally. Persists on change.
- **Campaign battle launch:** On `SCHEDULED`, `CampaignBattleLaunchService` creates campaign battle, enrolls warbands. Naval slots: if the war attacker has no berthed `ships` vehicle at an in-play port, the defender wins the slot without a live battle and attacker fuel is spent (`lastBattleOffensiveCoalition` forced to aggressor). On `BattleEndedEvent`, casualties apply, then `CampaignBattleOutcomeService` spends fuel, begins winner Push/Hold choice, and may chain military walkovers after choice resolves.
- **Admin dev commands:** `/war admin schedule <warId> choice push|hold|attack|accept` (aliases: `battlechoice`, `defenderchoice`, `pushchoice`, `holdchoice`). Permission `simplefactions.admin`.

---

## Battles & Warbands

### Province presence (central tracker)

SimpleFactions runs **one** province location poll for all online players every **1 second**. It fires **`PlayerProvinceEnterEvent`** / **`PlayerProvinceLeaveEvent`** when a player's province changes.

Battles and future systems (ZOC, raids) **subscribe to these events** instead of running separate location scans. Province-leave battle penalty removed.

Lookup: [`RestServer.getProvince`](./ProvinceGrid.md) → local `ProvinceGrid`.

### Merge Warbands into SimpleFactions

Same pattern as professions → RPCharacters. SF owns campaign battles, join flow, lives, and campaign linkage. Warbands battle engine (capture points, deaths, respawn) becomes an SF submodule.

### Battle modes (locked ; zones removed )

| Mode | Win | Region | Respawns |
|------|-----|--------|----------|
| **Field** | Side eliminated when **lives = 0** and all online fighters are in **jail** (capture points gate spawn teleports only) | **No province fence** (64.08) | Campaign: collective lives from committed regiments (61.04). Staff manual: per-side lives in side edit GUI |
| **Siege** | Hold **contest area** until timer hits **0** (ETW-style bidirectional timer); defender elimination also ends battle | **No province fence** (64.08) | Same as field |
| **Raid** | Capture **target** to 100%; defender eliminated when `LIVES` mode exhausted | **No fence** - map-wide movement | Attackers: **none** (elimination on death/disconnect). Defenders: **infinite** or **set lives** (template) |

**Naval variant** (field + siege only): template flag for staff layout (**attacker spawn** on naval point). Campaign schedule inserts **`NAVAL`** slots (prepended on invasion leg when port blocks sea); launch sets **`navalVariant`** on field battles. Legacy **`NAVAL_INVASION`** slots from old saves still display and launch with naval variant. Does not enforce province bounds after 64.08.

**Province-leave penalty:** **removed** (was: leave allowed set → 10s → death). Staff place spawns, jails, capture points, and contest areas anywhere on the map.

### Automatic vs manual battles

| Mode | Use |
|------|-----|
| **Campaign battle** | System-created from schedule; join via command; mode = field or siege from campaign context |
| **Campaign raid** | Inter-battle installation assault; timer fight; no capture points |
| **Pillage war battle** | One-shot border settlement raid war type |
| **Staff raid battle** | Manual `BattleType.RAID` with capture points; lore/dev events |
| **Manual battle** | Lore / staff non-campaign fights. **One manual battle at a time** (61c.09); persisted to `plugins/SimpleFactions/Battles/`; delete via battle edit GUI (slot 22) or admin flow |

### Staff template battles (61c)

**YAML templates** (`battle-templates.yml`) apply **battle rules only**: lives, friendly fire, keep inventory, siege/raid durations, defender respawn mode, naval variant flag. They do **not** seed spawns, jails, or capture point coordinates.

For each **campaign battle**, staff use `/battle edit` (or battle GUI) to place spawns, jails, capture points (field), contest area + duration (siege), and naval spawn when applicable. Manual (non-campaign) battles from the war GUI reuse the same edit flow. **Fast edit:** open Sides, click a side, use Set spawn / Set jail / Add point at your feet (61c.10). **Siege contest:** open Contest Area (slot 23), stand at fort corners, click **Min** then **Max** to set corners at your feet; click duration to cycle hold time.

When a scheduled fight is overdue but cannot start (e.g. siege contest unset), the campaign GUI shows **Cannot start: …** instead of **Starting now**, and online belligerents receive a chat warning.

**Pre-battle signup reminders:** while phase is `SCHEDULED` and the battle has not started, chat reminders fire at configured offsets before `scheduledBattleAt` for coalition members not yet in any warband (`/warband list` to join). Set `battle.signup_reminder_seconds_before: []` to disable.

### Capture points enabled (`capture_points_enabled`)

Template YAML key (default `true` on `field_default`, `false` on siege/raid). When enabled:

- Battle edit slot 23 opens the points list
- Side Edit shows **Add point** (auto-names A, B, C per side)
- Capture point tick runs during the fight

When disabled, siege/raid use contest area or raid target UI on slot 23 instead.

### Battle & warband persistence (61c.09)

| Path | Content |
|------|---------|
| `plugins/SimpleFactions/Battles/battle_{id}.json` | Battle layout, rules, started state, `startedAt` (ISO-8601), side warband id references |
| `plugins/SimpleFactions/Warbands/warband_{id}.json` | Roster, leader, campaign shell fields (devmode dummy members not saved) |

- **Autosave:** every 60s and on plugin disable.
- **Resume:** `started=true` battles restore lives, capture progress, contest timer, and `startedAt`; tick loop continues without re-teleport/title.
- **Manual limit:** only one battle with `warId == null`; `/battle create` blocked until deleted.
- **Orphan cleanup:** manual warbands not attached to any persisted battle are removed on save/disable.
- **Campaign end:** `CampaignBattleOutcomeService` deletes battle + campaign warband JSON when the war outcome resolves the fight.

### In-battle rules

- **No province-leave penalty** (removed 64.08). Movement is unrestricted during field/siege battles.
- Friendly fire / keep inventory per template config.
- **Raid attackers** do not respawn; fight until eliminated.
- **Raid defenders:** infinite respawns or finite lives per template.

### Battle retreat (mid-fight)

> **Shipped:** campaign **warband leader** may concede a started field or siege battle via `/warband retreat`.

| Rule | Detail |
|------|--------|
| **Who** | **Warband leader** of the side's campaign shell warband (one per battle side) |
| **When** | Started **field** or **siege** campaign battle (`warId != null`); not campaign raid |
| **Cooldown** | `battle.retreat_min_elapsed_seconds` after battle start (default **1200** = 20 minutes) |
| **Command** | `/warband retreat` + confirm GUI |
| **Effect** | Opponent wins; **ledger casualties only** (partial deaths preserved); normal `CampaignBattleOutcomeService` path (fuel spend, post-battle choice, war end on final battle) |
| **vs strategic retreat** | Map voting only; no battle fought; no initiative cost; **Retreated** route lore |

Retreating the **final battle** is allowed: it is a normal battle loss and may end the war (preserves army vs fighting to elimination).

### Battle dev mode and capture (61b + 61c, test server)

Solo staging on the test server: [dev-config.md](./dev-config.md).

| Topic | Detail |
|-------|--------|
| Capture threshold | `battle.capture_min_players` (default **1**). The side with strictly more players in the zone ticks capture. |
| Devmode | `/war admin devmode on\|off\|status` (admin, volatile). **On:** fills every active campaign battle side warband with up to `phantom_count` dummy roster members (re-applies after restart); raid launch ignores schedule window and battle day. **Off:** strips dummy members from all warbands. Dummies are not persisted to disk. Manual `/warband create` also seeds dummies when devmode on. Dummies count toward roster display, lives subtraction, and join cap preview. Capture markers and capture presence still use online real players only. |
| Campaign join | When `battle.warId != null`: joining faction must be on the battle side; side roster capped by **pool lives** (`livesPerRegiment × committedRegiments`, pre-battle) or side lives (mid-battle join costs 1 life). `/battle join` redirects to `/warband list` signup. One auto warband per battle side. Player-facing errors: wrong side, roster full, no lives left, blocked rejoin after mid-battle leave. |

Config under `battle:`:

| Key | Default | Purpose |
|-----|---------|---------|
| `province_block_protection_enabled` | `false` | When `true`, players cannot break or place blocks in the battle province during started field/siege battles. Vehicle/artillery damage is unaffected. Staff bypass. |
| `capture_min_players` | `1` | Min players at a capture zone |
| `retreat_min_elapsed_seconds` | `1200` | Minimum elapsed time after battle start before `/warband retreat` is allowed |
| `signup_reminder_seconds_before` | `1800, 600, 300, 60` | Chat reminders before fight time for players not in a warband; `[]` disables |
| `war.devmode.phantom_count` | `10` | Dummy roster fill on manual warband create or campaign seed when devmode on |

### Campaign time dev mode (test server)

Staff can fast-forward the Paris battle schedule without waiting on real clock: `/war admin time` (`add`, `reset`, `status`, `skip-to-battle-day`). The active route slot shows a gray **Starts in X** countdown when a fight is scheduled. Offset is volatile (cleared on restart). Full command table and E2E workflow: [dev-config.md](./dev-config.md).

### Manpower pool per battle (locked )

Military commitment, battle pool, collective lives, and casualty apply are shipped. See levy and vassal rules below.

Offense/defense regiment pools depend on **where the battle is fought** and **campaign phase**, not who declared war. Use `CampaignProgressionService.getOffensiveSide(war)` to determine which belligerent role is offensive; that side's factions use offensive regiments, the other side uses defensive regiments.

| Location (simplified) | Attacker-side factions | Defender-side factions |
|----------|------------------------|-------------------------|
| Inside **defender** territory (invasion push) | Offensive regiments | Defensive regiments |
| Inside **attacker** territory (counter-push) | Defensive regiments | Offensive regiments |

**Militia** (`militia` regiment): deploys only on **that faction's direct land** (`TitleManager.getByProvince(battleProvinceId) == faction`). Overlord militia does **not** deploy in vassal territory; vassal gets full military including militia on vassal land; overlord sends professional army + levies only.

### War commitment (`WarCommitment`, +)

Minimal rules (locked 61.01 + 61.01b):

1. **Fighter OR levy-only, never both** on a war side. Fighters = main leaders, their **direct subjects**, and **called allies** (`BattleSideMembers.collectParticipatingFactions`). Nested vassals are levy-only.
2. **Fighter own regiments:** live slot count at each battle (mid-war buildup counts).
3. **Levy:** frozen rows `holder → source → count`. Snapshotted at **declare** and when an **ally joins**. Nearest **fighter** on the overlord chain is the holder (not the top overlord when a subject also fights).
4. Casualties always debit the **source** faction for levy rows.

**Levy mid-war:**

| Event | Effect |
|-------|--------|
| Ally joins | New levy rows for joiner only |
| Subject buildup / levy % change | No change |
| **New vassal** (of main, subject fighter, or ally) | No new rows |
| **Vassal bond breaks** | Remove rows for broken subject **and its subject subtree**; if a fighting subject leaves, remove all rows it held |

Today `WarManager.getCommitmentsForWar` returns snapshot rows via `WarCommitmentService` (61.02). Re-commit is forbidden per own-regiment row. Levy rows use `sourceFactionId`. Commitments persist on war JSON (`WarData.commitments`, 61.06) and reload on server start.

**Admin debug:** `/war admin status <warId>` prints one JSON line per war. Use `commitmentRows` to inspect per-faction rows (`factionId`, optional `sourceFactionId`, `regimentId`, `count`). Example own row: `{"factionId":"atk","regimentId":"militia","count":4}`. Example levy row: `{"factionId":"atk","sourceFactionId":"sub","regimentId":"levy","count":6}`. The `commitments` field is the row count (same as `commitmentRows.length`). After a campaign battle ends, re-run `war admin status` to confirm rows and counts decreased (61.06).

Militia eligibility is filtered at **battle pool** time, not at commit.

### Lives (collective, campaign field + siege)

Applies when `battle.warId != null` and type is **FIELD** or **SIEGE** at `battle.start()` (61.04). Campaign lives are **computed per side** from war commitment and roster size; the battle editor shows a read-only preview before start. `/battle setlives` is rejected on campaign battles. Staff manual battles (`warId == null`) configure **per-side lives** in the side edit GUI (FIELD/SIEGE); **raids** keep template defender lives.

**Formula (per side):**

```text
sideLives = max(minSideLives, livesPerRegiment × committedRegiments − rosterFighters)
```

- `committedRegiments`: eligible pool total from battle pool resolver (61.03), **plus mercenary slots** (below)
- `rosterFighters`: unique warband roster members on that side (includes devmode dummies; offline real players included)
- Capture markers and capture presence still count **online real players only**
- Pre-battle join cap uses **pool lives** (committed regiments × lives per regiment); mid-battle join costs 1 life from the started side pool

**Mercenary contribution (locked):** a hired company adds to `committedRegiments` only its **filled and attending** slots - enlisted players actually on that side's warband roster, capped at the slots the contract promised. An empty or unfilled slot adds nothing, and a company cannot inflate the pool past its promise. Lives stay a **single shared side pool**: mercenary lives are not a separate allowance, so a company burning lives spends the belligerent's pool too.

A **dual-role** player (a mercenary who is also a citizen fighting on that side) is counted once in the pool and **subtracted once** from `rosterFighters`, because both figures walk unique roster ids.

**Attendance (locked):** a slot attends when its player is present at **battle start** and still on the roster at **resolution**. End means on the roster, not alive and not online: a player who dies or logs out mid-battle still attends as long as they were not removed. Absence is what drives the refund, so a restart that loses the battle-start snapshot records **no** absence rather than guessing one.

Config under `war.battle_military`:

| Key | Default |
|-----|---------|
| `lives_per_regiment` | `5` |
| `min_side_lives` | `1` |

Mercenary slot, price and contract keys are documented in [mercenaries.md](./mercenaries.md); the gameplay lock is [planning/war-companies/00-index.md](./planning/war-companies/00-index.md).

### Casualties (locked )

**Ledger (61.05):** During campaign field/siege battles, track per-side casualties from deaths and disconnects after start. Province-leave penalty deaths **removed** with 64.08. No ledger for staff manual or raid battles.

**Apply (61.06):** After battle via `CampaignBattleOutcomeService` (before `openVote`). Implemented in `BattleCasualtyService`: militia first (when eligible at battle province), then army + levies split **proportionally** across contributors. Debits `WarCommitment.count` and faction `Regiment.currentSlots` (permanent until rebuilt). Applies even when **no winner**. Side casualties are snapshotted on `BattleEndedEvent` before the ledger clears.

**Out of scope for 61:** staff battles, goal apply (**62**), campaign battle schedule / fort sieges (**64**), raid war type (planned).

### Levies (war-scoped)

- Frozen integer pool per `(holder, source)` row; snapshotted at **declare** and on **ally join** only.
- **Holder** = nearest participating fighter walking up from source (avoids double count when main and subject both fight).
- **Source** = levy-only faction whose troops and casualties are tracked; can be any depth in the vassal chain.
- **No** mid-war add from subject troop buildup, levy % changes, or **becoming** a new vassal (of main, subject fighter, or ally).
- **Yes** mid-war **remove** when a subject stops being a vassal: drop that source and **all its subject descendants**; drop holder rows if a fighting subject leaves the side.
- **transferSubject** mid-war: remove old subtree only; no snapshot for new overlord.
- Fighter on the war side never also appears as levy from the same side (no double count).
- All commits tagged **`war_id`**. Losses decrement committed levy and source faction sent counts.

### Battle loot

One identical reward, paid once, to every fighter who was **online when the battle ended**. Both sides are paid: the battle happened in lore, so losing it still earns the reward. A retreat pays too, since a retreat produces a winner.

Three things must all hold, checked in `BattleLootService.shouldPay`:

1. The battle produced a **winner**. A timer expiry with no winner pays nothing.
2. The battle is **not a campaign raid**.
3. The battle's own **loot toggle** is on.

The toggle is a per-battle flag, persisted with the battle and flippable at **slot 14** of `/battle edit` ("Battle Loot"). It defaults **on** for manual and campaign field and siege battles, and **off** for campaign raids. Battle files written before this feature existed load as on, except campaign raids, which load as off. Staff can turn a raid's loot on by hand and it sticks.

What gets paid is server-wide, not per battle - see the `war.battle_loot` keys in [dev-config.md](./dev-config.md). `COMMAND` mode dispatches each configured line from console once per rewarded player, which is how a crate key is handed out; `ITEM` mode gives a TLibs item and drops it at the player's feet if the inventory is full. An empty command list or a blank item path means no loot, so there is no separate enable key.

Everyone is paid the same regardless of contribution, kills, or time on the field.

**Wins that pay nothing:** the reward hangs off `BattleEndedEvent`, which only fires for battles that were actually fought. Walkovers, offensive forfeits, the naval auto-loss, campaign slot concessions and admin `/war admin schedule winbattle` all record a winner without a live battle, so none of them pay. Offline roster members are also skipped, which means joining a warband and logging off earns nothing.

---

## Naval & installations

### Campaign naval segments (shipped )

Full rules: [Port sea ZOC](#port-sea-zoc-shipped) under campaign battle schedule.

- Enemy **port** sea ZOC blocking an axis sea run → **`NAVAL`** schedule slot prepended on the invasion leg (index 0; `navalVariant` field battle). Landing fight is the FB **FIELD** at border **B** (no new **`NAVAL_INVASION`** slot).
- Sea on axis without an enemy blocking port → no naval slot.

### Port protection

`war.port_sea_zoc_radius` (default **2**): sea-hop BFS from the port's adjacent ocean tiles. Distinct from `port-sea-proximity-blocks` (construction validation only). See [installations.md](./installations.md#config).

### Installation picks per battle (shipped )

Leaders commit **ports and airports** each battle day via the campaign GUI; picks lock at vote close. Schedule slots still carry `portInstallationId` / `fortInstallationId` for **blocking** ports and **siege** battles respectively; those are separate from the pick UI except the defender **ZOC port**, which is auto-committed from `portInstallationId`.

- **Campaign raids**: leaders launch source→target assaults during the raid window; targets are **any operational** enemy port/airport/fort (`CampaignRaidEligibilityService`). See [Campaign raids](#campaign-raids).
- **Fort raids** in campaign raids use port/airport source → fort target; not chosen via installation picks.

### Attacker naval launch

For `NAVAL` / `NAVAL_INVASION` slots, the **war attacker** needs at least one **in-play port** with a berthed naval vehicle (registry `INSTALLATION` row, category `ships`). Personal unberthed ships do not count. Null registry counts as no navy.

If that check fails at launch (including both sides empty of official navy):

- Unstarted battle is purged; defender wins the slot through the existing battle-end path.
- Attacker initiative is spent (`lastBattleOffensiveCoalition` = aggressor).
- Field and siege slots are unchanged.

From battle day through launch, while this would still fire, `CampaignNavalAutoLossReminderService` pings the **attacker war leader** every schedule tick.

### Wartime installations and peace

Occupation (and siege take of the fort's province) **transfers** installations on occupied tiles to the occupying coalition's **war leader**. Snapshot `wartimeInstallationOwners` stores original faction ids. This does **not** change de jure province owner.

**Every** `WarEndReason` (`WarManager.endWar`): revert the snapshot **first**, then `WarOutcomeService.apply`. Land apply that calls `addProvince` can transfer installs again for tiles the winner keeps. White peace and admin end still revert.

Fort ZOC on campaign line → **siege** when line passes through ZOC and fort is enemy-controlled. War-time fort controller may differ from installation owner; see [Campaign battle schedule](#campaign-battle-schedule-locked). Capital inside fort ZOC → siege then objective field battle.

See [installations.md](./installations.md) for fort/port/airport pipeline. War-aware ZOC on map export (`ZocRealm` controller filter) is shipped; see [installations.md](./installations.md#fort-zoc-export-forts).

---

## War end conditions

| Outcome | Trigger | Goal | Reparations |
|---------|---------|------|-------------|
| **Attacker victory** | Aggressor wins battle at defender capital, failed objective retake, or defender leader **surrenders** (slot 47) | Goal apply | No |
| **Defender victory** | Defender wins battle at attacker capital, or attacker leader **surrenders** (slot 47) | None | **Attacker pays** |
| **White peace** | Leader accept of auto-proposal, voluntary mutual agreement, mutual exhaustion auto-proposal, or offensive stalemate | None | **No** |
| **Pillage success** | Pillage battle won at settlement | Pillage apply | No (unless attacker loses; N/A for one-shot pillage) |

### `WarEndReason` values (shipped )

| Value | Meaning |
|-------|---------|
| `white_peace` | No winner; no goal |
| `attacker_victory` | Attacker coalition wins |
| `defender_victory` | Defender coalition wins |
| `admin_end` | Staff / command end |

Opening the campaign view **recalculates** white peace proposal flags only; it does **not** auto-end the war.

`WarManager.endWar` always reverts wartime installation transfers (`WartimeInstallationService.revert`) **before** `WarOutcomeService.apply`. Civil wars also run `CivilWarUntangleService.restore` after that revert and before apply.

### War reparations (attacker-only)

**Only when attacker loses badly, on an external war:**

- Attacker **surrenders**, or
- Attacker **loses capital** (defender counter-push and wins there)

**Not when:** defender loses (land/subject loss is enough), any **white peace** (including accepted auto-proposal), initiative exhaustion white peace, or a **civil war** defender / white peace / admin end (restore + empty movement; no auto imprison).

**Mechanic:** flat **% of main guild ledger income** for **X days** paid to winner. Source: **main faction guild ledger only** - not subsidiary guilds. Applied via ledger pipeline (`Cashflow.WAR_REPARATIONS` / `WAR_REPARATIONS_PAYMENT`). Config: `war.reparations.income_percent` (default **25**) and `war.reparations.days` (default **10**).

Staff can add the same obligation by hand (after a civil war, or any time): `/war admin reparations <fromFaction> <toFaction> [percent] [days]`. Optional args default to the config values. Uses `WarReparationsService.apply`. Permission: existing war admin. Not tied to an active war.

---

## Map export contract

Exported in `map_markers.json` (or sidecar) per `map-export-schema.json`.

Emit on declare, after each battle, and on war end. Chronicle events: `war_declared`, `battle_scheduled`, `battle_result`, `province_occupied`, `war_ended`.

### Web map campaign visualization

Active campaign wars export a **`wars[]` route slice** in `map_markers.json` (SF `WarMapExporter` + PS loader enrichment). The ProvinceSystem web map renders:

| Feature | Source | Notes |
|---------|--------|-------|
| Smooth dotted campaign line | `campaign_provinces[]` / `campaign_line_points[]` | Catmull-Rom spline; border `#2a1810` + dash `#8b3a3a` |
| Battle pins | `campaign_battle_schedule[]` + `campaign_counter_schedule[]` | One `battle.png` per slot (`leg` + `schedule_index`); siege/port coords from installation when set |
| Pin hover | `display_name` or `kind_label` + `province_name` + `status` | Prefer `{display_name} - {status}` when SF export includes `display_name` |
| Next battle highlight | slot `status === "next"` on active leg | 1.1x scale + ring on pin |
| Occupier nation fill | `province_data[].occupied_by` | Political overlay remaps occupied tiles to occupier colour |
| Campaign-line front | `occupied_by_attacker[]` | Pushes the dotted front along the axis |

Visible on nation, county, duchy, kingdom, empire, and trade map modes (same as settlement markers).

Re-upload `map_markers` or wait for the next regen after deploy so active wars pick up the route slice and occupation lists. Chronicle event stream is owned elsewhere.

---

## Related documentation

| Doc | Topic |
|-----|--------|
| [installations.md](./installations.md) | Forts, ports, airports, ZOC |
| [settlements.md](./settlements.md) | Settlement provinces (de jure annex block) |
| [province-grid.md](./province-grid.md) | Province ids and neighbours |
| [campaign-raids.md](./campaign-raids.md) | Inter-battle installation assaults |
| [mercenaries.md](./mercenaries.md) | Companies, contracts, wages, reputation, config keys |
| [map-export.md](./map-export.md) | War route slice in `map_markers.json` |
| [roadmap.md](./roadmap.md) | Shipped vs planned features |
| [war-goals-apply lock](./planning/war-goals-apply/00-index.md) | Navy gate, goal apply, movement gate |
| [inter-vassal-wars lock](./planning/inter-vassal-wars/00-index.md) | Internal peer wars, CTA, liege transit |
| [war-companies lock](./planning/war-companies/00-index.md) | Mercenary gameplay lock, dividends, recruitment rule |
| [ProvinceSystem map wars overlay](../../ProvinceSystem/docs/map/wars-on-map.md) | Website overlay |

---

## Open items

- Inter-vassal wars **shipped** (Participants, campaign pathfinder, apply): [planning/inter-vassal-wars/00-index.md](./planning/inter-vassal-wars/00-index.md)
- NAP treaty overlay **shipped** (stacks with tributary; blocks all declares until cleared)
- Occupation overlay **shipped** (occupier fill)
- Council-forced peace **shipped** (white peace offer or surrender on a chosen war)
- Map chronicle events: other member (`war_declared`, `battle_scheduled`, `battle_result`, `province_occupied`, `war_ended`)
- Production declare codes / Discord ticket gate: last
- When to **recalculate** white peace auto-proposal flags after cursor / phase change

Civil wars: [planning/naval-installations/02-phase-2.md](./planning/naval-installations/02-phase-2.md) (done).

War-goal apply and navy gate: [planning/war-goals-apply/00-index.md](./planning/war-goals-apply/00-index.md) (Phases 0-7 done).

`provinces_between_battles` (default **3**), `max_battles_per_leg`, and `initiative_factor` are locked in config (see `war.yml`).
