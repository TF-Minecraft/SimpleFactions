# Phase 2: Civil wars

**Phase:** [00-index.md](./00-index.md)  
**Depends on:** [01-phase-1.md](./01-phase-1.md) exit (Done)  
**Movement apply:** [war-goals-apply Phase 6](../war-goals-apply/07-phase-6.md)  
**War-goals index:** [01-phases.md](../war-goals-apply/01-phases.md) Phase 7

Dedicated start and teardown. Do not punch `sameRealm` on `WarManager.declareWar`. Politics stay `MovementOutcomeService`. Installations follow Phase 1 transfer. Do **not** auto-apply staff terms (imprisonment, extra land, custom reparations).

Player-facing strings: no em dash. Use `-` or `:`.

---

## Intent

A movement sometimes creates a temporary `"<faction name> Rebels"` faction. That faction is always dissolved back into the host **before** other apply (independence, government change, coup). Decline demands starts this path; it must **not** `endMovement` (the movement is the war payload).

---

## Start shape

| Movement leader | Host supporting guilds | Supporting vassals |
|-----------------|------------------------|--------------------|
| In a **host** rebel guild | Merge into temp rebels | **Direct** supporting vassals become wartime subjects of the temp rebels (same type as under the host) and stay extra main attackers. Nested vassals stay under their overlord and are not extra mains. |
| Not in a host rebel guild (typically a vassal) | Temp faction only if some host guilds rebel | Independent wartime; still attacker main participants; nested stay under them |

Pure vassal independence with no host rebel guilds: **no** temp faction and **no** overlord land split. Snapshot old subject type; `endVassalage` is wartime map only; lasting independence is apply after restore.

Foreign backers: allies of the rebel war leader (existing call-to-arms). They do not enter the land split.

War leader: movement leader's faction (temp rebels or the leading vassal). Direct supporting vassals of the host are extra main participants (not flattened into one nation). Nested vassals are not extra mains.

---

## People and main guild

Host **base guild never joins** a movement and never relocates. It is the government.

Rebel main guild, in order:

1. Movement leader's non-base guild, if it is rebelling.
2. Else the strongest rebelling host guild (trade power).
3. Else a **generated** main guild on the temp faction. Citizen supporters (`Member.MEMBER`) with no guild to follow move into it. Movement leader is faction leader of that guild.

`CHANGE_LEADER` wanted leader must pass `canBecomeLeader` (host member who is not the sitting faction leader and does not lead a guild). They become temp rebel faction leader and are pulled from **any host guild** (main or regional) they belong to. Cause leaders pick a target by request: the candidate must be **online** and confirm with `/faction accept`. Offline pick is blocked. Staff `/movement admin target <id> <causeIndex> <player>` sets the target with no handshake (dummies / solo). Snapshot origin guild (and whether they led it). On restore, put them back there unless the rebels **won**, in which case the wanted leader goes to the host main guild and `CoupService` runs after restore.

Citizen supporters in the host main guild are also snapshotted and returned to origin.

---

## Land split (host provinces only)

Per-province trade presence of supporting **host** guilds vs loyal **host** guilds (not guild-total `tradePower`). Vassal land is not split.

- Tie: loyalists keep the tile.
- Exactly two host provinces: loyalists keep the capital tile, rebels get the other.
- Checkerboard is intended. Civil-war start uses `addProvince` (Phase 1 transfer), **not** `canClaim` adjacency.
- Rebels and loyalists each need at least one province. One-province host: block **host guilds** from starting or joining movements. Vassals may still start independence.
- Sea crossing between capitals: dry-run the split. If rebels would get **no port on that water**, **refuse**. No gifted coastal tile, no fake port.

Host rebel guilds become a temp nation via `Guild.rebel()`: detach without dissolving the capital settlement, then land split transfers the city. Snapshot the guild's own name at rebel time; restore uses that field only. Temp rebels get vassalage law from `war.yml` (`civil_war.vassalage_group` / `vassalage_law`). After the split, move (do not copy) the highest holdable title the rebel capital sits in, except the loyalists' primary title; restore moves it back.

Campaign field names use a settlement only when the fight province **is** that city's center. Neighbor cities in the same county are not used.

Camps and capital moves (settlement / fort / port / airport / generated camp) are snapshotted and reverted. Details when implementing, not a second settlement engine.

---

## Border lock

While the civil war is active, host, temp rebels, and the transferred subject tree cannot claim, unclaim, or be prestige-stolen. Occupation (the campaign) still works. `TitleManager.overProvinceCap` steal must be blocked too (prestige does not auto-unclaim; others steal).

Cannot start a civil war if any involved faction is already in a civil war. Cannot pick de jure annex or transfer subject against a faction in an active civil war; cannot start a civil war if the host is the target (or payload) of those goals.

---

## Assets

- **Regiments:** transfer `X%` of each host regiment type from `Movement.getPower()` (0-100) **before** `WarCommitmentService.commitAllParticipants`. Loyalists keep the rest. Vassal armies stay on those vassals.
- **Vehicles:** follow installations (Phase 1). Personal ships stay on the player.
- **Battles:** both sides use all troop types (offensive and defensive) regardless of province; militia not limited to own land. `BattlePoolService` change lives here, not Phase 1.

---

## War goal and apply

Goal type = **primary (first) cause** only: `OVERTHROW` / `CHANGE_LAW` / `CHANGE_TAX`. Extra causes remain valid and must show on the war GUI (primary first, then also: ...).

Start: `CivilWarStartService` builds a `War` with `goal` + `movementId`. Skip `WarGoalValidator` nation-vs-nation rules. Freeze the movement. Campaign populate + Phase 1 navy gate on the real map.

End: `CivilWarUntangleService` always (every `WarEndReason`): restore snapshot (temp faction into host first, vassal trees back, delete war camps, Phase 1 installation revert). Then:

| Reason | After restore |
|--------|----------------|
| Attacker victory | `MovementOutcomeService.apply(movement, WAR)` on the restored host (all causes, leader first) |
| Defender victory | End movement empty. **No** causes, **no** `WarReparationsService.applyFromWar`, **no** imprisonment or other staff terms |
| White peace / admin | Restore only. End movement empty. No causes, no auto reparations |

Do not copy coup/law/tax into war-end code. Do not auto-imprison. Staff handle leftover terms in-game.

`WarOutcomeService` on `DEFENDER_VICTORY` still applies ledger reparations for **external** wars. Civil wars (`movementId` set) skip that arm.

---

## Staff reparations command

Auto reparations stay for **non-civil** defender wins only ([wars.md](../../wars.md#war-reparations-attacker-only)).

Staff can add the **same** obligation by hand after a civil war (or any time):

`/war admin reparations <fromFaction> <toFaction> [percent] [days]`

| Arg | Meaning |
|-----|---------|
| `fromFaction` | Payer (faction id) |
| `toFaction` | Payee (faction id) |
| `percent` | Optional. Default `war.reparations.income_percent` (25) |
| `days` | Optional. Default `war.reparations.days` (10) |

Reuse `WarReparationsService` / `WarReparationsObligation` (main guild ledger, `Cashflow.WAR_REPARATIONS*`). Permission: existing war admin (`Permissions.isAdmin`). Not tied to an active war. No em dash in usage or success/error strings.

---

## Navy (uses Phase 1)

Declare still needs an operational port if the dry-run campaign is naval. Empty port is allowed; attacker must source ships before naval launch or auto-lose. Refuse the civil war entirely if the dry-run split has no required port.

---

## Exit (must all be true)

- Decline demands starts a civil war without `endMovement`.
- Untangle always restores before politics.
- Civil-war defender win / white peace / admin: restore + empty movement; no auto reparations and no auto imprison.
- Staff `/war admin reparations` can add a config-or-custom obligation from X to Y.
- Phase 1 navy and installation transfer used; no gifted ports.

---

## Batches

### Batch 1 - Staff reparations command

**Done.** `WarReparationsService.apply(payer, payee[, percent, days])`; `applyFromWar` still attacker-pays-defender. `/war admin reparations <from> <to> [percent] [days]` persists the payer. External `DEFENDER_VICTORY` auto reparations unchanged.

### Batch 2 - Start: freeze, skip nation validator, temp rebels, land split, navy refuse

**Done.** Decline demands → `CivilWarStartService` (does not `endMovement`). Freeze movement. Skip `WarGoalValidator` nation-vs-nation rules via `WarManager.startCivilWar`. Build `War` with primary-cause goal + `movementId`. Temp rebels / vassal-only start per start shape. Host land split via `addProvince` (Phase 1 transfer). Dry-run: refuse if rebels would have no port on a sea crossing. Campaign populate + Phase 1 navy gate. One-province host: block host guilds from movements.

### Batch 3 - Border lock + declare blocks

**Done.** Host, temp rebels, and the wartime subject tree cannot claim, unclaim, or be prestige-stolen (`MapSystem` + `TitleManager.overProvinceCap` steal). `ProvinceHandler.provinceCap` skips locked factions. Occupation unchanged. Second civil war involving those factions refused. De jure annex / transfer subject cannot be picked against a locked defender (or transfer payload). Cannot start a civil war if the host is the defender or payload of those goals.

### Batch 4 - Regiment split + civil-war battle pools

**Done.** `CivilWarRegimentSplitService` moves `Movement.getPower()` % of each non-levy host regiment type onto temp rebels after land split and before `WarManager.startCivilWar` (commit is inside start). Null temp rebels: no host split; vassal armies untouched. Failed populate/navy gate restores slots. `BattlePoolService` civil wars: all non-levy types in both pool modes, militia not own-land gated, levy rows in both modes. External wars unchanged.

### Batch 5 - Untangle + apply table (no auto staff terms)

**Done.** `WarManager.endWar` runs `WartimeInstallationService.revert`, then `CivilWarUntangleService.restore`, then `WarOutcomeService.apply`. Restore merges temp rebels into the host (land, installs, settlements, guilds, remaining non-levy slots, capital) and puts wartime vassals back. Pure vassal start only restores relations. Attacker victory still `MovementOutcomeService.apply(movement, WAR)`. Defender / white peace / admin: empty `endMovement`, skip `applyFromWar`. External defender wins still pay auto reparations. No auto imprison.

### Batch 6 - Tests + canonical docs

**Done.** GUI decline (demands slot 33) starts a civil war and does not `endMovement` (`MovementViewDeclineTest`). Defender civil-war win still adds no obligation; staff `/war admin reparations` still does. [wars.md](../../wars.md) records the civil-war end exception, untangle-before-apply order, and the admin reparations command.

---

## Out of scope

- Inter-vassal wars without a rebel nation (war-goals Phase 8)
- Flattening vassals into the temp faction
- Auto-giving a port tile
- Auto imprisonment or other staff terms on defender win
- Gating `Faction.applyLaw` for war apply
- `Revolt` as a fourth apply path (staff/no-apply label only if needed for tests)
