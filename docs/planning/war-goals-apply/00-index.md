# War goals, apply, navy gate - lock

**Repo:** `simplefactions`  
**Status:** Phases 0-8 **done** (navy, apply spine, relation/title/law/pillage goals, movement gate, civil wars, inter-vassal). Generic **War** remains pickable with no auto-apply. Next: Phase 9 leftovers.  
**Canonical gameplay:** [wars.md](../../wars.md)

This is the implementation lock for (1) navy / sea connectivity gates, (2) war-goal declare + auto-apply, (3) movement outcome apply. **No parallel diplomatic, law, tax, or government engines.** War and movements call existing systems.

Implementation sequence: [01-phases.md](./01-phases.md). Do not start a later phase until the current phase's exit criteria are met.

---

## Architecture lock

| Piece | Choice |
|-------|--------|
| Declare source of truth | v2 `WarGoalType` + `WarGoalValidator` + `DeclareWarView`. Do not revive `enums.Goal` / `WarGoal.canTarget()` as a second validator. |
| YAML | Display, cost, open-market law ids, `can-pick-for-war` on relation types. Not a second eligibility engine. |
| Diplomacy | `RelationManager` only. Forced set is a boolean on `setRelation` (default `false`). Wrapper `setRelationForced` calls that. |
| Usurp | `FactionManager.usurp(...)`. Do not reimplement title/subject moves. |
| Transfer subject | `RelationManager.transferSubject(...)`. Nested picker; same apply. |
| Laws / tax / gov | `Faction.applyLaw`, `TaxHandler`, `Proposal.apply` / `applyPoliticalAction`. |
| Movement | One `MovementOutcomeService.apply(movement, source)`. War and "accept demands" both use it. |
| Coup | Standalone function, stub + document first. Called from the movement apply gate, not from war-only code. |
| NAP | Stub query, always false until implemented. |
| Campaign raids | Unchanged. Not the pillage war goal. |

### Outcomes

| `WarEndReason` | Apply |
|----------------|--------|
| `ATTACKER_VICTORY` | War goal applicator |
| `DEFENDER_VICTORY` | War reparations **from attacker** (ledger cashflows already exist). No goal. |
| `WHITE_PEACE` | Neither |
| `ADMIN_END` | Neither |

White peace and admin end never apply goal or reparations.

### Forced relations

`setRelation(..., forced)`:

- **Always:** loop check, top-overlord check, `vassalCheck`, `atLimit`, map update, linked reverse type.
- **If not forced:** opinion thresholds, mutual request when `check` is true.
- **If forced:** skip opinion and mutual request. No "leader must be online."

War apply and `usurp` (when called with no player / war path) use forced.

### Targeting

- **Internal peer war:** defender is the **clicked** faction. See [inter-vassal-wars/00-index.md](../inter-vassal-wars/00-index.md).
- **External (this lock, not yet wired):** war defender is the **top liege** of the clicked faction (`RelationManager.getTopLiege`), or the faction itself if independent. Do not mix retarget into Phase 8.
- **Goal payload** may be a nested vassal, title, settlement, law, etc.
- Campaign path / objective may still resolve to the payload faction (already true for transfer subject).

---

## Shared declare rules (all wars)

Cannot declare if any of these hold (unless a goal lists an exception):

1. Same faction.
2. Already at war (hostile) with them.
3. **Same realm** (`RelationManager.sameRealm`) - not a vassal of them, they are not a vassal of you, not nested under the same path. **Exception:** Usurp may target your **direct** overlord only.
4. Ally (`RelationManager.getAllies` / ally relation type).
5. Non-aggression pact (stub: always allowed until NAP exists).
6. They are your **tributary**, unless the goal is **Subjugate** or **War** (tickets / declare codes will later restrict which goals a player can pick).
7. Attacker lacks offensive manpower (`CampaignDeclareValidator` as today).
8. **Navy gate** (below) after campaign populate.

Full civil wars are **out of scope** for this lock (shipped: [naval-installations/02-phase-2.md](../naval-installations/02-phase-2.md)). Inter-vassal wars: [inter-vassal-wars/00-index.md](../inter-vassal-wars/00-index.md). Stop using `endVassalage` as a side effect of every same-realm declare.

---

## Navy and sea (implemented)

Code: `InstallationNavyQueries.hasOperationalPort`, `Map.SeaConnectivity.hasSeaConnection`, `CampaignNavyGate`. Declare: `WarManager.declareWar` after populate. Push: `CampaignPushProjection.canMountOffensiveAfterPush`.

### Queries (general, not war-only)

| Query | Meaning |
|-------|---------|
| `hasOperationalPort(Faction)` | Completed `PORT` on **that faction**. Not vassals, not under construction. |
| `hasSeaConnection(Faction a, Faction b)` | BFS through **sea provinces only**, from any sea-adjacent land province of A to any sea-adjacent land province of B. Landlocked or two disconnected oceans: false. |

Reuse the province neighbour graph and `Terrain.SEA` (same idea as `PortSeaZocIndex` sea BFS). Do not build a second ocean model.

### Declare

After `populateCampaign` succeeds (already before `addWar`):

- If the **invasion** schedule contains `NAVAL` or `NAVAL_INVASION`, and the **attacker** has no operational port:
  - Reject declare.
  - Message: `Faction has a navy blockading your approach, and you lack a navy to challenge them`
  - Do not add the war.

Land-only invasion schedules remain declarable with no port.

### Push / Hold

After a battle, current schedule index is the **next** slot.

If the next slot is `NAVAL` or `NAVAL_INVASION` and the coalition that would **attack** that slot has no operational port:

- Cannot Push.
- Must Hold.
- Campaign GUI: Push disabled, same blockade message in lore.
- `canMountOffensiveAfterPush` / `resolveMandatoryHoldIfNeeded` include this check (today they only check army).
- Post-battle **deadline** currently auto-Pushes; if navy-blocked it must auto-**Hold**.

If a port is lost mid-war, the same rule applies.

---

## Declare GUI: two layers

Layer 1: goal type. Layer 2: specific payload when needed (same pattern as title / subject pickers today).

| Goal | Layer 2 |
|------|---------|
| Tributary | None |
| Subjugate | Subject relation type (see picker rules) |
| De jure annex | Title list, including **ineligible** rows with reasons |
| Transfer subject | Any faction in the defender's nested realm |
| Usurp | None (primary title is `getHighestTitle()`) |
| Overthrow / Change law / Change tax | Movement-only; law GUI / tax chat / leader pick as today |
| Open market | None (laws from war-goal config) |
| Change government | Government + optional leadership, pre-filled with target's current laws |
| Pillage | Settlement |
| War | None (no auto-apply; pickable; ticket codes later) |
| Revolt | None (no auto-apply; not in player GUI yet) |

Subjugate types come from diplomacy YAML where `vassal: true` and `can-pick-for-war` is not `false`. **Integrated subject** is `can-pick-for-war: false`. March / palatinate still use existing `limit`. Query: `RelationLoader.getWarPickableVassalTypes()` (subjugate type picker in declare GUI).

---

## Goals

### Tributary

- Must be able to set tributary/suzerain via diplomacy types (`diplomacy.yml`).
- Must not already be tributary of the attacker.
- Defender must be **independent** (no overlord; not another nation's vassal).
- Shared rules; tributary target is allowed (they are not `sameRealm`).
- **Apply:** `setRelationForced` tributary type. Not a vassal relation.

### Subjugate

- Not already a subject of the attacker.
- Not the attacker's overlord.
- Same structural checks as diplomacy vassal set (loop, already has another overlord, `atLimit` for chosen type). Skip opinion/request.
- Type picker: Subject, Mercantile, March, Palatinate. Not Integrated.
- Tributary of attacker: **allowed** (shared rule exception).
- **Apply:** `setRelationForced` chosen type.

### De jure annex

**New spec** (replace current "defender holds the title and you already occupy a piece"):

- Enough prestige headroom for incoming provinces (existing prestige vs province-count rule; project size after transfer).
- Target de jure area has **no settlements** (and no faction capitals in those provinces), same protection as today.
- Attacker **owns the title**, **or** the title has **no owner** and the attacker owns **at least one** province in it.
- Rank: title at or below attacker rank (keep current rank gate).
- Layer 2 lists titles you **could** want, including blocked ones:
  - Settlements in title: "use subjugate instead" only if subjugate vs top liege would be valid; otherwise "unavailable because …"
  - Do not own title / no province in unowned title: show why
- **Apply:** provinces in that title owned by the **defender's realm** transfer to the attacker. **If the title is unowned, do not grant the title.** Forming stays the existing title menu (third parties may still hold land in the title).

Campaign shape stays border → objective; occupation/battles unchanged.

### Transfer subject

- Must be able to take the target as a subject (same vassal checks / `atLimit` for their **current** subject type).
- Picker: any faction under the defender nested realm (vassal of vassal, etc.). War defender remains top liege.
- **Apply:** `RelationManager.transferSubject(subject, attacker)` (keeps type).

### Usurp

- Take the target's **primary title** (`getHighestTitle()`).
- Attacker rank **same or lower** than the target (cannot usurp a strictly lower-rank independent as an "usurp").
- **May** target **direct overlord**. Other same-realm targets still blocked.
- **Apply:** `FactionManager.usurp` only: title to attacker, subjects of target to attacker, target becomes `subject` of attacker, attacker takes target's former overlord slot if any.

### Overthrow government (movement)

- Only from a movement.
- Wanted leader must be able to be faction leader (`canBecomeLeader` / existing checks).
- **Apply:** movement apply gate with source `WAR` or `ACCEPTED`. Coup function (stub until civil wars): always change leader; council:
  - Autocracy **or Community**: do nothing to council (no council).
  - Oligarchy: empty council.
  - Plutocracy or Democracy: switch government law to oligarchy, empty council.
- Cause order: **coup / leader first**, then other causes (so a later "become democracy" still applies).
- Stability: source-dependent (war vs accepted cave-in). Coup war hit: **Coup -75%**, decaying (`StabilityModifier`).

### Change law (movement)

- Only from a movement.
- Law picker: reuse law GUI.
- Law must not already be current.
- `CanHaveLaw` / `Law.isAvailable` (implement requirements + compatibility; today it is a stub).
- **Apply:** `applyLaw` + Civil War stability **-75%** decaying (same modifier family as coup; name **Civil War**).

### Change tax rate (movement)

- Only from a movement.
- Pick tax target + type rate in chat (existing tax proposal / `TaxLawChange`).
- Rate must be inside law brackets.
- **Apply:** `TaxHandler.setTaxRate` + same Civil War **-75%** decaying.

### Open market

- Target must not already have the configured open-market law.
- Attacker must not have the configured isolation-style law(s).
- **Ids live on the war-goal config**, not in Java. Example shape:

```yaml
open_market:
  defender_must_not_have: [free_trade]
  attacker_must_not_have: [isolationism]
  apply_defender_law: free_trade
```

- **Apply:** `applyLaw` on defender + **Forced Market Open -25%** decaying.

### Change government

- Not callable by movement unless later merged with Change Law.
- Layer 2: government law (autocracy / oligarchy / plutocracy / democracy) and optionally leadership (fixed vs elected). GUI opens on the **target's current** selections; player changes what they want and applies. May change one axis only.
- Target must not already have **exactly** the selected combination.
- **Apply:** `applyLaw` on changed groups + **Forced Government Change -50%** decaying.

### Pillage (war goal / war type)

Player-facing name **Pillage**. Code ids `pillage` (not `raid`, to avoid campaign-raid collision).

- Settlement within **X** provinces of attacker **land borders**, **or** within **X** of sea **and** `hasSeaConnection(attacker, settlement owner / defender)`.
- No seaborne pillage across disconnected oceans or if landlocked.
- Navy declare gate still applies if the generated campaign includes a naval slot.
- **Apply:** for every guild with capital in that settlement: **-100% trade income**, decaying hour by hour over **10 days**. Attacker is spawned money equal to **10 days** of those guilds' trade income at resolution time. Query existing ledger trade income; do not invent a second trade engine.

One-battle campaign (existing pillage war-type shape in `wars.md`). Distinct from campaign raids.

### War (generic / no apply)

- Shown in the player declare picker (same list as Subjugate). Declare-code / ticket filtering is later.
- Must be allowed to be at war (shared rules). **Allowed against tributaries.**
- **Apply:** none.

### Revolt (staff / civil war)

- Civil war with special demands. Full rebel-faction / relation-snapshot system is **later**.
- **Apply:** none in this lock. Movement-driven revolts later call the movement apply gate.

---

## Movement apply gate

`MovementView` accept-demands today: add `"Caved to Movement"` stability, `proposal.apply` per cause, `endMovement`.

Replace with one service:

```
MovementOutcomeService.apply(movement, ACCEPTED | WAR)
```

- Same cause order for both sources.
- Stability name and magnitude from source (cave-in vs war coup / civil war).
- Then apply causes, then end movement.
- Coup stub is called from leader-change apply, not duplicated in war code.

---

## Reparations (defender win)

Already specified in [wars.md](../../wars.md#war-reparations-attacker-only): % of **main guild** ledger income for X days, via `Cashflow.WAR_REPARATIONS` / `WAR_REPARATIONS_PAYMENT`. Applicator on `DEFENDER_VICTORY` for **external** wars only (surrender / capital loss as already listed). Not on white peace.

**Civil wars** ([naval-installations/02-phase-2.md](../naval-installations/02-phase-2.md)): defender win does **not** auto-apply reparations or imprisonments. Staff `/war admin reparations <from> <to> [percent] [days]` uses the same obligation pipeline when manual terms require it.

---

## Out of scope (later)

- Full coup sequencer beyond the Phase 6 stub + documented order (civil-war start/untangle is shipped: [naval-installations/02-phase-2.md](../naval-installations/02-phase-2.md))
- Airborne pillage (not a feature)
- Production declare codes / Discord ticket gate (last; see [roadmap.md](../../roadmap.md))
- Reviving legacy YAML goals as a second runtime (`independence`, `war_reparations` as a **declare** goal, etc.)

---

## Implementation order

Phased sequence (several batches per phase): [01-phases.md](./01-phases.md).

Phases 0-8 are **done**. Inter-vassal wars: [inter-vassal-wars/00-index.md](../inter-vassal-wars/00-index.md) (shipped). NAP is shipped. Remaining follow-ups: [roadmap.md](../../roadmap.md).

---

## Code touchpoints (for implementers)

| Area | Today |
|------|--------|
| Declare | `WarManager.declareWar`, `WarGoalValidator`, `DeclareWarView`, `WarDeclareHelper`, `CampaignDeclareValidator` |
| End | `WarManager.endWar` → installation revert, civil-war untangle, `WarOutcomeService` |
| Push | `CampaignPushProjection.canMountOffensiveAfterPush`, `CampaignPostBattleChoiceService`, `CampaignCreator` Push button |
| Schedule | `CampaignScheduleService.currentSlot` after `advanceIndex` |
| Usurp | `FactionManager.usurp` |
| Movement accept | `MovementView.handleDemandsViewClick` |
| Reparations ledger | `Cashflow.WAR_REPARATIONS*` |
| Sea BFS | `PortSeaZocIndex`, `Province` neighbours, `Terrain.SEA` |
