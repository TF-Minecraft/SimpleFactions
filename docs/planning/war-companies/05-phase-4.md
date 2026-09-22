# Phase 4: War and battle (batch plan)

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 4
**Depends on:** Phase 3 complete.

The only phase that touches `Side`, `BattleLivesService` and `BattlePoolService`. Keep the blast radius small: `Participant` is not modified.

## Exit (must all be true)

- A company appears in the war screen as a mercenary, distinct from main and secondary.
- Side lives equal `livesPerRegiment * (faction committed + attending merc slots) - unique roster fighters`, floored at the minimum, with no double subtraction for a player who is both militia and mercenary.
- A mercenary who logs out and returns before resolution passes attendance; one who never returns fails it and generates an absence refund.
- A battle day accrues both the day price and the battle price.
- A government member cannot be rostered against their own faction; a plain citizen can.
- `mvn test -Dtest="me.Plugins.SimpleFactions.War.**"` passes unchanged plus the new cases.

---

## Batch 1 - Third participant kind

**Files:** `War/core/Side.java`, `War/core/WarMapper.java`, `Database/SideData.java`

**Do not generalize `Participant`.** It is `Faction`-typed throughout (leader, subjects, allies, backers), `Side.isParticipating` takes a `Faction`, and `WarCommitment` is keyed by faction id. Widening that would ripple into persistence, peace deals, war goals, occupation and casualties for no gameplay benefit, and companies genuinely are not factions, so a parallel list is also the more honest model.

1. `List<MercenaryEngagement>` on `Side`, where an engagement is a company plus the contract that put it there. Add `getMercenaries()` and a lookup by company.
2. Persist on `SideData` and map in `WarMapper` alongside the existing side fields. Bump `WarData.schemaVersion` and make old saves load with an empty list.
3. `Side.isParticipating(Faction)` stays exactly as it is. A company being present must never make its host faction a belligerent, which is what makes "hired guns are not allies" true mechanically as well as narratively.
4. Engagements are removed when their contract terminates, through the Phase 3 termination service rather than by war code.

**Tests:** an old save loads with no mercenaries; `isParticipating` is unchanged by a company's presence; a terminated contract removes the engagement; round-trip persistence.

---

## Batch 2 - War display

**Files:** `Managers/Inventory/WarView.java`, `Managers/Inventory/WarCreator.java`

`WarView` currently fills two fixed 16-slot regions from `getMainParticipants()`:

```71:77:simplefactions/src/main/java/me/Plugins/SimpleFactions/Managers/Inventory/WarView.java
		for(int x = 0; x < w.getAttackers().getMainParticipants().size(); x++) {
			i.setItem(attackerSide.get(x), creator.createParticipantItem(player, w.getAttackers().getMainParticipants().get(x), "main_attacker", w, true, false));
		}
		
		for(int x = 0; x < w.getDefenders().getMainParticipants().size(); x++) {
			i.setItem(defenderSide.get(x), creator.createParticipantItem(player, w.getDefenders().getMainParticipants().get(x), "main_defender", w, true, false));
		}
```

1. Append mercenary engagements after the faction participants on each side, with a distinct item and a `mercenary_attacker` / `mercenary_defender` marker so click handling can tell them apart from `main_` and secondary entries.
2. Soldier count shown is **slots promised in the contract**, not slots currently filled. That is what was bought; whether they turn up is the attendance question.
3. Lore shows company name, host guild, home settlement, promised slots, reputation, and days remaining on the contract.
4. If a side runs out of display slots, prefer paging over silently dropping entries.

**Tests:** creator lore assertions; promised rather than filled count; the marker distinguishes mercenaries from mains.

---

## Batch 3 - Battle roster

**Files:** `War/battle/campaign/CampaignBattleJoinService.java`, `War/battle/engine/core/BattleSideSetupService.java`, `mercenary/contract/MercenaryLoyalty.java`

1. A hired mercenary joins the side named in their company's contract, not the side their own faction is on.
2. **Mercenary first.** If the company's host faction is also a belligerent, the player counts as a mercenary, per the lock. One person, one role, resolved in favour of the contract.
3. Enforce the player-level loyalty gate here rather than at enlistment: refuse to roster a player who is in the government of a faction on the opposing side, using `Government.isCouncilMember`. Message names the reason.
4. A mercenary occupies a slot for attendance purposes; two players cannot cover one slot.

**Tests:** mercenary joins the contracted side even when their own faction fights the other one; a dual-role player resolves to mercenary; a council member is refused; a plain citizen is allowed; slot to player mapping is one to one.

---

## Batch 4 - Attendance

**Files:** new `mercenary/contract/AttendanceService.java`, `War/battle/engine/core/BattleEndSupport.java`

Attendance is **present at battle start and present at battle end**, where end means on the roster at resolution rather than alive or online. A mercenary who burns through the shared lives and is eliminated has fought; failing them would punish exactly the people who fought hardest.

1. The roster snapshot already exists in the right shape and at the right moments: `BattleParticipantCollector.collect(battle)` returns a `Set<UUID>` excluding dummy members, and `BattleEndSupport.endBattle` captures it **before** `battle.end()` clears state. Take the same snapshot at start.
2. There is no start event today, only `BattleEndedEvent`. Either add a matching started event or hook wherever `started` is set true; prefer the event for symmetry.
3. Per slot, per battle: attended if the assigned player is in both snapshots. Record the result on the contract so Phase 5 can price it and reputation can score it.
4. Relogging mid-battle and being back by resolution passes. Never returning fails.

**Tests:** in both snapshots passes; missing from the start snapshot fails; missing from the end snapshot fails; eliminated but still rostered passes; a rejoin between snapshots passes; an unfilled slot is a failure, not an absence of data.

---

## Batch 5 - Lives

**Files:** `War/battle/military/BattleLivesService.java`, `War/battle/military/BattlePoolService.java`

The formula stays as it is and mercenaries add to the committed count:

```79:85:simplefactions/src/main/java/me/Plugins/SimpleFactions/War/battle/military/BattleLivesService.java
	public static int computeSideLives(int committedRegiments, int rosterFighters) {
		if (committedRegiments <= 0) {
			return 0;
		}
		int raw = Cache.warBattleLivesPerRegiment * committedRegiments - rosterFighters;
		return Math.max(Cache.warBattleMinSideLives, raw);
	}
```

1. Mercenary regiments contribute **only for slots that are filled and attending**, computed from the battle-start roster rather than from a commitment row written earlier. An empty or absent slot adds nothing.
2. Add the merc contribution where `committedRegiments` is assembled, so `computeSideLives` itself is untouched and the existing tests keep their meaning.
3. Lives remain a shared side pool. **No double-subtraction risk exists:** `countRosterFighters` builds a `Set<UUID>` across all warbands, so a player who is both militia and mercenary is subtracted exactly once. Do not add a separate mercenary subtraction.
4. `previewCampaignSideLives` must include the merc contribution or the pre-battle preview will lie.

**Tests:** filled and attending slots add lives; empty slots add none; filled but absent adds none; a dual-role player is subtracted once; preview matches the applied value; existing lives tests still pass.

---

## Batch 6 - Battle accrual

**Files:** `mercenary/contract/ContractAccrualService.java`

Accrue now, settle at the daily tick in Phase 5. Accrual writes absolute denars onto the contract; it never reads a ledger.

1. **Every battle that actually starts in the plugin counts.** Not scheduled, not cancelled, not skipped: started. This also gives both sides a reason not to drag a war out.
2. On each started battle: accrue `slotsHired * pricePerSlotPerBattle` owed to the company, and `absentSlots * absenceRefundPerSlotPerBattle` owed back to the hirer.
3. The per-day price accrues on **every** day of the window, including battle days. A battle day therefore costs day rate plus battle rate.
4. Accrual is idempotent per battle id so a replayed or re-fired event cannot double charge.
5. Mercenary payment and refund accrue to **separate** buckets and are never netted against each other. They are separate ledger lines in Phase 5.

**Tests:** a started battle accrues once; a cancelled battle accrues nothing; a battle day accrues both prices; absence accrues the refund at the contract rate; re-firing the same battle id changes nothing; payment and refund buckets stay separate.

---

## Out of scope for this phase

- Moving any money (Phase 5)
- Wages (Phase 5)
- Reputation scoring (Phase 5)
- Changing `Participant`, `WarCommitment` or peace deals
- Mercenaries as a peace-deal party; the hirer negotiates, the company does not
