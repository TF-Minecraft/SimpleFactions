# Phase 2: Company core (batch plan)

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 2
**Depends on:** Phase 1 batch 3 (guild to player money leg) only for the wage display; the rest is independent.

No contracts and no war in this phase. The company must be a complete, testable entity first.

## Exit (must all be true)

- One company per guild, formed for 100 d after a 24 h timer, arriving with 1 slot.
- Slots expand in 24 h, survive a restart mid-expansion, and refuse to expand while an unfilled slot exists.
- Factions never see the mercenary regiment anywhere in their military.
- A player can be in exactly one company, can accept or decline an invite, and can be kicked.
- Upgrades cap at level 10 and apply stats **only** inside a battle as a hired mercenary.
- A player who disconnects mid-battle has no residual health or mana modifier.
- `mvn test -Dtest="me.Plugins.SimpleFactions.mercenary.**"` passes.

Suggested package root: `me.Plugins.SimpleFactions.mercenary`, with `company`, `slot`, `stat` and (Phase 3) `contract` subpackages.

---

## Batch 1 - Entity and persistence

**Files:** new `mercenary/company/MercenaryCompany.java`, `Guild/Guild.java`, `Database/GuildData.java`, `Database/Database.java`, `Cache.java`

1. `MercenaryCompany` owned by a `Guild`: id, name, banner pattern list, formation state, its own `Regiment` instance, enlisted roster, upgrades, reputation (Phase 5), wage settings (Phase 5).
2. One per guild, held on `Guild` as a nullable field. `Guild.getCompany()` and `hasCompany()`.
3. Formation costs `mercenaryFormationCost` (default 100) withdrawn from the guild bank at request time, then a `mercenaryFormationSeconds` timer (default 86400) before the company exists with 1 slot. Reuse the `MilitaryExpansion` shape rather than a new timer type.
4. Leader is always the guild leader, resolved live rather than stored, so a guild leadership change carries automatically.
5. Persistence on `GuildData`: name, banner, formation remaining, slot count, expansion queue, enlisted members, upgrade levels, upgrade queue. Follow the existing compact string encoding used for military (`regimentId + "." + currentSlots` in `Database` around line 297) and upgrades, so the save format stays consistent.
6. `Guild.getMembers()` is deliberately **not** consulted. Company membership is independent.

**Tests:** formation debits once and yields exactly one slot; a second company on the same guild is refused; leadership follows the guild leader; full save and load round-trip including a mid-formation timer.

---

## Batch 2 - Mercenary regiment type

**Files:** `Army/Regiment.java`, `Army/Military.java`, `Loaders/RegimentLoader.java`, `resources/regiments.yml`

1. `mercenary: true` flag on `Regiment`, parsed in the config constructor alongside `levy` and `offense`, with `isMercenary()`.
2. `Military`'s constructor currently gives every faction every loaded type:

```19:24:simplefactions/src/main/java/me/Plugins/SimpleFactions/Army/Military.java
	public Military(Faction f) {
		this.f = f;
		for(Regiment r : RegimentLoader.getRegiments()) {
			regiments.add(new Regiment(r));
		}
	}
```

Skip mercenary types there. This is the single enforcement point for "regular factions cannot use it in their military"; do not rely on GUI filtering.

3. Add a `mercenary` regiment to `regiments.yml` with `expansion-time: 86400`, `upkeep: 8`, `default-slots: 0`, `levy: false`, `offense: true`, its own icon and description.
4. `RegimentLoader.getMercenaryRegiment()` helper so the company can build its instance without string-matching an id in several places.

**Tests:** a fresh faction `Military` contains no mercenary regiment; `getTotalSlots`, `getManpower` and `getTotalUpkeep` are unaffected by the new type existing; the company's own instance is independent of the loader prototype.

---

## Batch 3 - Slots and expansion

**Files:** `mercenary/slot/`, `mercenary/company/MercenaryCompany.java`, `Database/Database.java`

1. Slot count is the company regiment's `currentSlots`. Expansion enqueues a `MilitaryExpansion` on the company, ticked from the same place the faction military is ticked, and persisted as `regimentId + "." + timeLeft` exactly like `data.militaryQueue`.
2. **Expansion is blocked while any slot is unfilled.** Unfilled means no enlisted player assigned to it, not un-contracted. Return a reason string so the GUI and any command share one message. This rule is the anti-panic-mobilisation gate and must be enforced in the service, not the GUI.
3. Slot upkeep is `mercenarySlotUpkeep` (default 8) per slot per day, charged to the host guild. It lands as a `Cashflow` expense in Phase 5; for this batch expose the daily figure so the GUI can display it.
4. Losing a slot (dropping `currentSlots`) must be a single method, because Phase 3 hangs contract-breach detection off it.

**Tests:** expansion refused with an unfilled slot; allowed when every slot is filled; a queued expansion survives serialization and resumes with the correct remaining time; upkeep scales linearly with slots.

---

## Batch 4 - Enlistment

**Files:** `mercenary/company/`, `Managers/CommandManager.java` or a dedicated mercenary command manager, `Utils/TabCompletion.java`, `plugin.yml`

1. Company leader invites by command; the invitee accepts or declines. Model the request on the existing request objects in `Objects/Request/` so expiry and messaging behave like everything else.
2. **A player may be in exactly one company.** Global check across all guilds, not just the inviting one.
3. Enlisting is refused when every slot already has a player.
4. The company leader may kick at any time. Kicking frees the slot, which by Batch 3 also freezes expansion until it is refilled, and by Phase 3 may breach a contract.
5. Membership is not restricted by guild, faction or nationality. Do not add any such check here; loyalty is enforced per contract in Phase 3, not at enlistment.
6. **Character trait gate.** `MercenaryEligibility` checks the active character for the `mercenary` trait defined in `rpcharacters/src/main/resources/traits/evil-traits.yml`. Same seam gates company creation, join, and the per-second company tick (leader disbands, members are kicked). The default probe is open for tests; production installs `RpCharactersMercenaryTraitProbe` when RPCharacters is present.

**Tests:** second company join refused; invite accept and decline paths; kick frees the slot; enlist beyond slot count refused; the eligibility seam is consulted for both create and join.

---

## Batch 5 - Upgrades

**Files:** `Guild/upgrade/Upgrade.java`, `enums/GuildModifier.java`, `resources/` guild upgrade config

1. `max-level` on `Upgrade` (default unlimited to preserve current behaviour), enforced in `levelUp()` and surfaced for the GUI. `Upgrade` has no cap today.
2. `GuildModifier` gains `MAX_HEALTH`, `MAX_MANA`, `MANA_REGEN`, all positive. They join the existing trade and admin entries so regular guild PvP stats stay possible later without a second system.
3. Three company upgrades in YAML using the existing `modifiers` string form (`MODIFIER base perLevel`), `upkeep: 10`, `expansion-time` per the lock:

| Id | Modifier | Per level | Max level |
|----|----------|-----------|-----------|
| Health | `MAX_HEALTH` | 0.5 | 10 |
| Mana | `MAX_MANA` | 1 | 10 |
| Mana regen | `MANA_REGEN` | 0.1 | 10 |

4. Upgrades queue through `UpgradeExpansion` and are persisted like guild upgrades already are (`GuildData.upgrades` plus `upgradeQueue`).
5. Company upgrades are scoped to the company, not merged into the host guild's normal `GuildModifier` totals. A company upgrade must never leak into guild trade or admin math.

**Tests:** `levelUp` refuses past `max-level`; existing guild upgrades without `max-level` are unchanged; `getAmount` returns `perLevel * level`; company upgrade levels do not appear in the host guild's modifier totals.

---

## Batch 6 - Stat application

**Files:** new `mercenary/stat/MercenaryStatService.java`, `pom.xml`

The template is `magic/src/main/java/net/tfminecraft/magic/integration/SpellModifierApplyService.java`. Copy its **shape**, not its stat: an availability guard, a per-player stored list of applied modifiers, and unregister from that stored list rather than recomputing.

1. `pom.xml` gains system-scoped `MythicLib` and `MMOCore` entries pointing at `${tfmc.refs}`, matching `magic/pom.xml` (MythicLib 1.7, MMOCore 1.13.1). Soft-depend in `plugin.yml`.
2. Availability guard: both plugins enabled, else log once and no-op. The plugin must work with neither installed.
3. Mana and mana regen are MythicLib `StatModifier` on the MMOCore stats, registered against `MMOPlayerData.getOrNull(player)`. Max health is a plain Bukkit `AttributeModifier` with a plugin-namespaced name so stripping is idempotent.
4. **Apply only when the player is in a battle as a hired mercenary.** Not in the world, not when they joined that battle as a normal faction fighter. Until Phase 4 exists, gate on an injectable predicate so this batch is testable without a war.
5. **Strip on battle end, battle abort, death, and disconnect.** `BattleEndSupport.endBattle` already collects the roster via `BattleParticipantCollector.collect(battle)` before `battle.end()`, and fires `BattleEndedEvent`; hook the event for the normal path and add explicit quit and death handling. Also strip defensively on player join, in case the server died mid-battle.
6. `clearAll()` on plugin disable, as `SpellModifierApplyService` does.

**Tests:** applied set matches the company's upgrade levels; stripping removes every modifier and leaves the stored map empty; double apply does not stack; unavailable dependencies leave the player untouched; strip is safe on a player who never had modifiers.

---

## Batch 7 - GUI

**Files:** `enums/SFGUI.java`, `enums/MenuItemType.java`, `Managers/Inventory/` new company creator and view, `Managers/InventoryManager.java`, `Managers/Inventory/InventoryUpdater.java`

1. New `SFGUI` entries for the company screen, slot screen, roster screen and upgrade screen. Register each in `InventoryUpdater` so live data refreshes, and in `InventoryManager.clickButton` for back navigation. Reuse the guild-keyed holder pattern; the company is reachable from its host guild.
2. Company screen: name, banner, home settlement stamp (Phase 1 batch 4), leader, slots filled over total, reputation (Phase 5), and **total daily burn** (slots times upkeep, plus upgrade upkeep, plus peacetime wages). Burn must be visible because a bankrupt host guild silently voids every contract.
3. Slot screen: per-slot occupant or empty, expansion queue with remaining time, and the blocked-expansion reason when applicable.
4. Roster screen: enlisted players with per-player wage overrides (Phase 5) and a kick action for the leader.
5. Upgrade screen: level over max, per-level effect, upkeep, and on **every** item an explicit line that the buff applies only while fighting as a hired mercenary. This warning is the reason the gate exists; it must not be easy to miss.
6. Entry point from the guild screen, with back navigation to it.

**Tests:** creator lore assertions in the style of the existing `PlayerLedgerCreatorTest`; burn total equals the sum of its parts; the buff-scope warning is present on all three upgrade items.

---

## Out of scope for this phase

- Contracts, hiring, prices, refunds
- War participation and battle lives
- Wages and any `Cashflow` entry
- Reputation values (the field may exist; the calculator is Phase 5)
- Enforcing the character trait gate
