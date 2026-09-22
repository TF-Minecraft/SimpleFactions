# Phase 3: Contracts and market (batch plan)

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 3
**Depends on:** Phase 2 complete.

Contracts are stateful, validated, money-bearing objects. Build and test them in isolation before a battle can touch them in Phase 4.

## Exit (must all be true)

- A contract below a config minimum, over 14 days, with an absence refund under the per-battle price, or over-promising an overlapping window, is rejected with a specific message.
- A company cannot be hired against its host faction, directly or through nested relations.
- An enlisted player who is in their own faction's government cannot fight that faction; a non-government citizen can.
- The loyalty re-check fires on faction-joins-war, relation change, and government change, and terminates with no breach refund and no reputation change.
- Hiring from outside the company's home settlement is refused.
- `mvn test -Dtest="me.Plugins.SimpleFactions.mercenary.**"` passes.

---

## Batch 1 - Contract object

**Files:** new `mercenary/contract/MercenaryContract.java`, `mercenary/contract/ContractStatus.java`, `mercenary/contract/ContractKind.java`, `Database/` new data class, `Cache.java`

Model on `Guild/loans/Loan.java`: an id, both parties, a millisecond issue and due date derived from a day count, a status enum that exists so one-shot effects fire once, and a `LoanData` style flat data class.

1. **Fields, all absolute denars written at signing.** Nothing is recomputed from live income later; this is the rule that keeps the new cashflows structurally unable to recurse.

| Field | Notes |
|-------|-------|
| Company, hiring faction, war id | Party identity |
| Slots hired | Reserved for the whole window |
| Price per slot per battle | Config minimum `mercenaryMinPricePerBattle`, default 50 |
| Price per slot per day | Config minimum `mercenaryMinPricePerDay`, default 10 |
| Duration in days | Max `mercenaryMaxContractDays`, default 14 |
| Absence refund per slot per battle | Must be at least the per-battle price |
| Breach refund | Contract field, spawns pre-filled with `mercenaryDefaultBreachRefund`, default 500 |
| Reputation at signing | Stamped for the record |

2. **`ContractKind` discriminator** from day one (`MERCENARY`, and later `ASSASSIN`). Assassins are the next program and must reuse this object rather than copy it.

3. **Validation at creation**, each with its own message:
   - Either price below its config minimum.
   - Duration over the maximum or under one day.
   - **Absence refund below the per-slot per-battle price.** This is the important one. If the refund is smaller than the price, a company earns more per head by not showing up than by fighting, which inverts the entire incentive. Reject it.
   - Slots hired over the company's total slots, or over its free capacity in the requested window (Batch 2).

4. **Status transitions** so refunds and reputation fire exactly once: offered, active, completed, breached, terminated. Copy the `LoanStatus` discipline.

**Tests:** every validation rule rejects with its own message and accepts at the boundary; refund exactly equal to the price is accepted; a round-trip through the data class preserves all figures; status transitions are one-way.

---

## Batch 2 - Reservation calendar

**Files:** `mercenary/contract/SlotReservations.java`

The constraint is on **overlapping** windows, not lifetime totals. Two back-to-back 7-day contracts for every slot are legal; two overlapping ones are not. A running counter cannot express this.

1. For a candidate window, sum slots already promised by active and offered contracts whose windows intersect it, and require `existing + requested <= totalSlots`.
2. Offered-but-unaccepted contracts hold a soft reservation so a company cannot promise the same slots to two prospects simultaneously. Released on decline or expiry.
3. Expose remaining capacity per window so the market screen can show honest availability.

**Tests:** back-to-back windows allowed; overlapping windows refused; partial overlap counted; an expired offer releases its hold; capacity query matches the accepted contracts.

---

## Batch 3 - Loyalty validation

**Files:** `mercenary/contract/MercenaryLoyalty.java`

Two independent checks. Both must be services, not GUI checks.

1. **Company level.** A company cannot take a contract for a side whose opposing side includes or is allied to its host faction, following nested relations. `Side.isParticipating(Faction)` already walks leader, subjects and joined secondaries, so this is one call per opposing side rather than a new traversal.

2. **Player level.** An enlisted player from another faction may fight their own faction **only if they are not in that faction's government**. `Government.isCouncilMember(Player)` already returns true for council members and the faction leader, which is exactly the gate:

```337:340:simplefactions/src/main/java/me/Plugins/SimpleFactions/government/Government.java
    public boolean isCouncilMember(Player p) {
        String name = p.getName();
        return council.isMember(name) || f.getLeader().equalsIgnoreCase(name);
    }
```

Enforced when the player would be rostered into a battle (Phase 4), not at enlistment. A ruler may belong to a company; they simply cannot deploy against their own realm.

3. **Re-check hook.** This is the part that will bite if skipped. `isParticipating` only counts secondaries that have already **joined**, so a legal contract can become illegal after signing when an ally joins the war, a vassalization lands, or an election changes the government. Re-validate every active contract on: faction joins war, relation changed, government changed. On failure, terminate as a **loyalty conflict**: no breach refund, no reputation change, days already served still paid. Neither party caused it.

**Tests:** direct host-faction opposition refused; nested vassal of the enemy refused; allied enemy refused; a government member blocked from deploying against their own faction; a plain citizen allowed; each of the three re-check triggers terminates a now-illegal contract with the correct no-penalty outcome.

---

## Batch 4 - Offer flow and the book

**Files:** `mercenary/contract/ContractHandler.java`, `mercenary/contract/ContractBook.java`, `Managers/Inventory/` contract creator and view

1. Propose, counter, accept, decline, expire, mirroring how loans are offered. Offers expire rather than lingering.
2. **Signed book** modelled on `Guild/loans/LoanBook.java`, which already writes a binding-on-signing book and stashes an id in `Keys.SECONDARY_STRING_KEY`. The book lists slots hired, both prices, duration, both refunds, and the company reputation at signing.
3. The book must state explicitly that **a battle day costs both the day price and the battle price**, so it cannot be read as either/or.
4. Contract creation and detail GUIs following the `LoanCreator` and `LoanView` shape.

**Tests:** accept creates an active contract and consumes the reservation; decline and expiry release it; book lore contains every locked field; the both-prices sentence is present.

---

## Batch 5 - Market

**Files:** new mercenary command manager, `plugin.yml`, `Utils/TabCompletion.java`, market GUI

1. `/mercenaries` lists companies with slots filled over total, both prices, reputation, and home settlement name from the Phase 1 batch 4 helper.
2. **Signing is local.** The signer must be at the company's home settlement. The list is informational; remote hiring is refused with a message naming the settlement. Decide and document the range check against the settlement's centre province.
3. **Any government member of the hiring faction may sign**, using the same `isCouncilMember` gate. Requiring the leader personally would make a front-line capital a single point of failure.
4. Sort by reputation so the market rewards a track record.

**Tests:** remote signing refused; local signing accepted; a non-government member cannot sign; list ordering by reputation; a company with no capital shows the `None` fallback.

---

## Batch 6 - Termination

**Files:** `mercenary/contract/ContractTerminationService.java`

One service, four triggers, each with the locked outcome:

| Trigger | Refund | Reputation |
|---------|--------|------------|
| Duration elapsed | None | Up if attendance was clean throughout |
| Company drops below promised slots | Breach refund from the contract | Large hit |
| Host guild bankrupt | None possible | Large hit |
| Loyalty conflict | None | No change |

1. Hook the slot-loss path from Phase 2 batch 3 (deliberately a single method) so a kick or a slot decrease immediately checks every active contract.
2. **Bankruptcy.** `Ledger` already makes a bankrupt guild inert in both directions: `getIncome` returns 0 at the top and `applySettlementFor` returns early. Without an explicit termination the contract would silently reserve slots while moving no money in either direction, so terminate explicitly. No mechanical punishment beyond reputation; bankruptcy is a real-world style exit and the consequence is social.
3. Days already served are always paid, in every termination path.
4. Reputation deltas are computed in Phase 5; call into a seam here.

**Tests:** each trigger produces exactly its row; a refund fires once even if the trigger repeats; a bankrupt company terminates rather than idling; served days are still owed after every termination.

---

## Out of scope for this phase

- War participant listing and battle rosters (Phase 4)
- Attendance measurement (Phase 4)
- Any `Cashflow` entry or actual money movement (Phase 5)
- The reputation calculator itself (Phase 5)
- Assassin contracts (next program, same object)
