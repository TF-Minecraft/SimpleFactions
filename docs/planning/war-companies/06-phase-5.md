# Phase 5: Money and reputation (batch plan)

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 5
**Depends on:** Phase 1 batch 3 (guild to player leg) and Phase 4 complete.

Everything accrued in Phases 3 and 4 now moves, and the market gets its memory.

## Exit (must all be true)

- A full contract cycle moves money in both directions, and every leg appears as its own ledger line with the correct sign.
- Mercenary income and refunds are never netted together.
- A test asserts no new cashflow reads another guild's ledger.
- 20% base wage at the config minimums pays exactly 2 d/day and 10 d/battle per soldier.
- Full attendance raises reputation, partial lowers it proportionally, an early breach hits hard, and a loyalty termination does nothing.
- `mvn test -Dtest="me.Plugins.SimpleFactions.Guild.**"` and `-Dtest="me.Plugins.SimpleFactions.mercenary.**"` both pass.

---

## Batch 1 - Cashflow entries

**Files:** `Guild/income/Cashflow.java`, `player/income/PlayerCashflow.java`, `Guild/income/Ledger.java`

Five guild entries and one player entry. `Cashflow` takes `(display, affectsInflation, grossCounted)`.

| Entry | Side | `affectsInflation` | `grossCounted` |
|-------|------|--------------------|----------------|
| `MERCENARY_CONTRACT` | company host guild, income | false | **true** |
| `MERCENARY_PAYMENTS` | hiring capital, expense | false | false |
| `REFUNDS` | hiring capital, income | false | **false** |
| `REFUND_PAYMENTS` | company host guild, expense | false | false |
| `WAGE_PAYMENTS` | company host guild, expense | false | false |

`PlayerCashflow.WAGES`, income, so it shows in `/ledger`.

**Why these flags.** `affectsInflation` is false on all five because only `TRADE` mints and the upkeeps sink; these are transfers between existing balances, and player wallets are already treated as part of the same supply (citizen tax carries no inflation flag either). Contract income is business income so it is gross-counted and therefore taxable by guild tax and feeds the overlord, tribute and reparations bases. Refunds are not gross-counted: taxing a company's compensation for failing to deliver would make the refund figure mean different things depending on the payer's guild tax rate.

**The discipline that keeps this safe.** Every recursion hazard in `Ledger` is a percentage-of-another-ledger's-income cashflow, guarded by the `isBase()` early returns and the `isCrossFactionGrossCashflow` exclusion list. All six new entries read **absolute denars accrued onto contract objects**, so they cannot recurse. Follow the loan pattern exactly: the payer computes from its own objects and the receiver reads a pre-pushed map, the way `Loan.pay()` pushes into the issuer via `addLoanPaymentEntry`.

1. Add push-entry maps and `addMercenaryPaymentEntry` / `addRefundEntry` methods on `Ledger`, alongside the existing `loanPayments` and `interestPayments` maps, cleared in `populateDailyTransfers` with the others.
2. Add the six `getIncome` arms, reading accrued contract buckets and pushed maps only.
3. Add the arms to the income and cost lists in `getNetIncome`, matching sign conventions.
4. **Never net mercenary income against refunds.** They are separate lines both in the data and in the GUI.

**Tests:** a static check that no new arm calls another `Guild`'s `getLedger()`; each arm's sign; gross-counted flags produce the intended guild tax effect; income and refund appear as two lines.

---

## Batch 2 - Settlement wiring

**Files:** `Guild/income/Ledger.java` (`applySettlementFor`), `Managers/FactionManager.java`

1. `MERCENARY_PAYMENTS`: `buffer.add(hiringCapital, companyHostGuild, amount)`, same shape as `GUILD_PAYMENTS`.
2. `REFUND_PAYMENTS`: `buffer.add(companyHostGuild, hiringCapital, amount)`.
3. `WAGE_PAYMENTS`: `buffer.addPlayerPayout(companyHostGuild, playerUuid, amount)` using the Phase 1 batch 3 leg. This is the only guild to player flow besides dividends.
4. `MERCENARY_CONTRACT` and `REFUNDS` are the receiving halves and stay display-only in the settlement switch, exactly like `GUILDS` and `TRIBUTES`.
5. Slot upkeep from Phase 2 batch 3 lands as an external delta on the host guild, matching how `MILITARY_UPKEEP` is handled for factions.
6. Clear accrued buckets after settlement so a day cannot be paid twice, mirroring the `citizenTaxes` / `loanPayments` clear at the end of `populateDailyTransfers`.

**Tests:** one full day of a contract moves exactly the accrued amounts; a bankrupt party on either end moves nothing; buckets are empty after settlement; double settlement is a no-op.

---

## Batch 3 - Active wage

**Files:** `mercenary/company/WageSettings.java`, `mercenary/contract/ContractAccrualService.java`

1. Base percentage on the company plus an optional per-player override on each enlisted slot.
2. The wage is a percentage of **what that slot earns**, so the day wage is a share of the contract day price and the battle wage a share of the contract battle price. Locked worked example: 20% base against the config minimums pays 2 d/day and 10 d/battle per soldier.
3. Active wage is only earned while the slot is under contract. No contract, no active wage.
4. The per-battle share requires **passing attendance** for that battle, using the Phase 4 batch 4 result. No-shows are not paid for the battle they missed.
5. Wages are paid by the host guild whether or not the hirer has paid yet. The company carries its own payroll risk; that is what the reputation system ultimately prices.
6. Wages are **not** taxable as citizen income for now. Leave a seam and a comment; revisit if it distorts anything.

**Tests:** the locked 2 d and 10 d example exactly; per-player override wins over base; no contract pays no active wage; a failed attendance pays no battle share but still pays the day share; percentages clamp to 0..100.

---

## Batch 4 - Peacetime wage

**Files:** `mercenary/company/WageSettings.java`

1. Optional flat denars per day, base plus a per-player override. Zero by default.
2. Paid whether or not a contract is active, which is the point: it is how a company retains good soldiers between wars.
3. Charged to the host guild as `WAGE_PAYMENTS`, the same entry as the active wage. Two reasons for one wage, one ledger line.
4. Counts toward the company's daily burn display.

**Tests:** paid with no contract; base and override; zero pays nothing; included in burn.

---

## Batch 5 - Reputation

**Files:** new `mercenary/company/MercenaryReputationCalculator.java`, `mercenary/company/MercenaryCompany.java`, `Database/GuildData.java`

Mirror the loan credit score exactly. `LoanHandler` already establishes the shape: an int field defaulting to `50`, clamped in one place, with a coloured display string.

```38:41:simplefactions/src/main/java/me/Plugins/SimpleFactions/Guild/loans/LoanHandler.java
    public void changeCreditScore(int amount) {
        creditScore += amount;
        creditScore = Math.max(0, Math.min(100, creditScore));
    }
```

1. `reputation` on the company, default 50, clamped 0..100 in a single `changeReputation(int)`. Persist as `GuildData.mercenaryReputation`, loaded with the same null-defaults-to-50 pattern used for `creditScore`.
2. `MercenaryReputationCalculator` in the shape of `CreditCalculator`: per-event maximums as constants, weighted factors clamped to 0..1, so severity scales rather than snapping.

| Event | Direction | Scaling |
|-------|-----------|---------|
| Contract completed with full attendance throughout | Up | Larger for longer contracts and more slots |
| Battle with partial attendance | Down | Proportional to the fraction of slots absent |
| Contract broken early by the company | Large hit | Worse the earlier in the window it breaks, like `calculateDefaultPenalty` weighting an early default |
| Loyalty conflict termination | No change | Neither party caused it |

3. Each event applies **once**, gated by the contract status transitions from Phase 3 batch 1.
4. Coloured display string plus a plain-language band (for example Trusted, Reliable, Unproven, Notorious) shown on the `/mercenaries` list and stamped on the contract book at signing.
5. Bankruptcy termination takes the large hit. There is no mechanical punishment for bankruptcy by design, so reputation is the entire consequence.

**Tests:** default 50; clamped at both ends; full attendance raises; half the slots absent lowers by roughly half the maximum; an early breach hurts more than a late one; loyalty termination changes nothing; each event fires once even if its trigger repeats.

---

## Batch 6 - Burn display

**Files:** company GUI creator from Phase 2 batch 7

1. Daily burn on the company screen: slot upkeep, plus upgrade upkeep, plus peacetime wages, plus expected active wages while under contract.
2. Show net position against current contract income so a leader can see whether the company is profitable.
3. Warn explicitly when burn exceeds the host guild's net income, because a bankrupt host guild voids every contract it holds and takes the reputation hit for each.

**Tests:** burn equals the sum of its parts; the warning triggers on the boundary; a company with no contracts shows peacetime burn only.

---

## Out of scope for this phase

- Taxing wages
- Assassin contracts
- Ledger memoization
- Reputation affecting prices automatically; the market reads it, players decide
