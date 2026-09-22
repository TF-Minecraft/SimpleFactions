# Phase 1: Groundwork (batch plan)

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 1

Do not start a later batch until the current batch's tests pass. Batch 1 is independent of the whole program and can ship alone.

**Settled after the first lock (also in [00-index.md](./00-index.md)):**

- Player payouts (dividends now, wages in Phase 5) credit `Accounts.BANK` through `DenarEconomyPlayerBank.PlayerBank`, never the pouch.
- A dividend pool is **clamped to what the guild can afford after all other daily settlement** (Phase 3). Scaled payouts must not push the guild negative or trigger `liquidateRandom()`.
- Batch order: the guild-to-player payout leg lands **before** dividends, because dividends are its first consumer.
- Settlement is four phases: populate transfers, net guild deltas, apply guild banks / liquidate, then **Phase 4 post-settlement payouts**. Phase 5 wages reuse Phase 4.

## Exit (must all be true)

- Levy-only factions cannot expand professional regiments, blocked at the service and shown as blocked in the GUI.
- A guild can set a dividend percentage; members receive an equal share in their bank at the daily tick; the faction collects dividend tax on it.
- A test asserts the dividend base excludes `DIVIDENDS`, `DIVIDEND_PAYMENT` and `DIVIDEND_PAYOUT`, so the payout cannot be circular.
- A guild that cannot afford its full pool pays a scaled amount and never goes negative.
- Guilds and factions display their home settlement name.
- `mvn test` is green.

---

## Batch 1 - Army recruitment rule

`Rules.CAN_RECRUIT_PROFESSIONAL_ARMY` exists and `laws.yml` already sets `can_recruit_professional_army false` under levy-focused laws, but nothing reads it.

**Files:** `Army/Military.java`, `Managers/Inventory/MilitaryView.java`, `Managers/Inventory/MilitaryCreator.java`

1. Add `Military.canExpand(Regiment r)` returning a result with a reason. False when `!r.isLevy()` and `!f.hasFactionRule(Rules.CAN_RECRUIT_PROFESSIONAL_ARMY)`. Mercenary regiments (Phase 2) are exempt and never reach this path because they are not in a faction `Military`.
2. Gate `Military.enqueue(Regiment)` on it. This is the real enforcement point; the GUI is cosmetic.
3. `MilitaryView` click handler (the `increase` branch) reports the reason instead of silently queueing.
4. `MilitaryCreator` greys the regiment item and appends the reason to its lore when blocked.

**Tests:** levy regiment allowed with the rule off; professional regiment blocked with the rule off; both allowed with the rule on; `enqueue` refuses even when called directly.

---

## Batch 2 - Guild to player money leg

`DailyGuildTransfers` is guild-to-guild plus per-guild `externalDeltas`. There is no way to move money from a guild to a player. Citizen tax only works because it is pushed after the player was already debited. Dividends and later mercenary wages both need the reverse, with a clamp.

**Files:** `Utils/DailyGuildTransfers.java`, `Utils/PostSettlementPayouts.java`, `Managers/FactionManager.java`, `vehicles/maintenance/DenarEconomyPlayerBank.java`

1. Add `Map<Guild, Map<UUID, Double>> playerPayouts` with `addPlayerPayout(Guild, UUID, double)`, matching existing style (ignore non-positive, merge on collision).
2. Add `Map<Guild, Double> pendingDividendPools` with a setter, following the `Loan.setTempPayment` earmark precedent (earmark without processing during that calculation cycle).
3. Extend `PlayerBank` with a deposit targeting `Accounts.BANK`. All player money in this program routes through this wrapper.
4. Add Phase 4 to `FactionManager.settleIncome()` **after** the existing three phases apply guild banks. Clamp against the post-Phase-3 bank balance. A shared clamp helper scales a guild's payouts proportionally when the balance cannot cover them all.

Why Phase 4 rather than folding into Phase 2: the clamp needs the guild's balance *after* the day's other movements, which only exists once Phase 3 has run.

Ordering note, already correct: `PlayerEconomyManager.get().clearAllDaily()` runs *before* `settleIncome()`, and `processDailyUpkeep()` runs at the end of it, so tick-time entries land in the ledger players see for the following day. Same as `PlayerCashflow.VEHICLE_UPKEEP` today.

**Tests:** payout debits the guild once and credits the player once; multiple payouts from one guild aggregate; payouts to one player from several guilds aggregate; a guild that cannot cover its payouts pays a proportionally scaled amount and never goes negative; bankrupt guild pays nothing.

---

## Batch 3 - Dividends

Every enum and tax path already exists (`Cashflow.DIVIDENDS` / `DIVIDEND_PAYMENT` / `DIVIDEND_PAYOUT`, `TaxTarget.DIVIDENDS`, `Brackets.DIVIDEND_TAX`, `Rules.DIVIDEND_TAX`, `TaxHandler.dividendTax`, `PlayerCashflow.DIVIDEND_PAYOUT`). Only `Ledger` had `//TODO implement` arms and there was no percentage to drive them.

**State.** `dividendPercent` on `Guild`, default 0, leader-only, clamped 0..100. Persist as `GuildData.dividendPercent`. Also persist `GuildData.dividendEligible` (list of member names), refreshed to the current member list at the end of each tick's payout. Eligibility is the intersection of current members and that list. On first load with no stored list, grandfather all current members. Config toggle `dividendRequirePreviousTickMembership`, default true, loaded in `ConfigLoader` into `Cache`.

**`getDividendBase()`.** Copy the `getNetIncome()` cashflow walk, skipping `DIVIDENDS`, `DIVIDEND_PAYMENT` and `DIVIDEND_PAYOUT`. Guild tax is already inside net income as `GUILD_PAYMENTS`. The exclusion is load-bearing: a percentage of raw `getNetIncome()` would be circular, because net income contains the payout.

**Pool math**, in one place so GUI and settlement cannot disagree:

- `pool = max(0, getDividendBase()) * dividendPercent / 100`, then clamped to the affordable amount in Batch 2's Phase 4
- `tax = pool * faction.getTaxRate(TaxTarget.DIVIDENDS, guild.getId(), true) / 100`, withheld from the pool
- `perMember = (pool - tax) / eligibleMembers.size()`

**Settlement.** `populateDailyTransfers` earmarks the unclamped pool via `setPendingDividendPool` **before** it clears `citizenTaxes` / `loanPayments` / `interestPayments`, because `getDividendBase()` reads those. Phase 4 then clamps, withdraws from the guild, deposits `tax` into the faction capital's bank, deposits `perMember` into each eligible player's bank, and writes `PlayerCashflow.DIVIDEND_PAYOUT`.

**Ledger arms** replacing the three TODOs, as projections for the GUI rather than settlement instructions (same precedent as `INSTALLATIONS`).

- `DIVIDEND_PAYOUT`: `-(pool - tax)`
- `DIVIDEND_PAYMENT`: `-tax`
- `DIVIDENDS`: base guild only, sum of `tax` across the faction's non-base guilds
- All three return 0 on the paying side when `guild.isBase()`; the treasury is not a company
- Bankruptcy needs no new check: `getIncome` already returns 0 at the top and `applySettlementFor` returns early
- `applySettlementFor` keeps all three in the display-only block

**GUI.** Dividend button at slot 17 of `guildView`, on the same row as the ledger (14) and upgrades (16). Leader-only click starts a chat prompt following the `InventoryManager.setRate` pattern.

**Tests:** base excludes the three dividend cashflows; negative base pays nothing; base guild pays nothing; bankrupt guild pays nothing; payout plus tax equals the pool exactly; a clamped pool scales tax and shares proportionally; a member who joined since the last tick is excluded; percentage clamped 0..100.

---

## Batch 4 - Home settlement stamps

`faction.getSettlementHandler().getByProvince(provinceId)` already exists.

1. Shared helper resolving a capital province to a display name: settlement name, else title or province fallback, else `None`.
2. Stamp it on the guild item in `GuildCreator` and the faction item in `FactionCreator`. Name only, no coordinates.

---

## Batch 5 - Cleanups

1. `GuildCreator.createLedgerItem` closing lore line pointing at `/ledger` for personal cashflow.
2. Remove `"#b7aae3Mercenary Company"` from the `landless.aliases` list in `tiers.yml`.

---

## Out of scope for this phase

- Any mercenary entity, slot, contract or cashflow
- Taxing wages
- Ledger memoization
- Changing how citizen tax is collected
