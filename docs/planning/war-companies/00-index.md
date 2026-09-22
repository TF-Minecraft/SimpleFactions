# War companies: gameplay lock

**Batch plan:** [01-phases.md](./01-phases.md)
**Canonical war spec:** [wars.md](../../wars.md)
**Roadmap:** [roadmap.md](../../roadmap.md)

This file is the **decision record**. Everything here is locked. If a batch file disagrees with this file, this file wins. Do not re-litigate a locked rule inside an implementation batch; if a rule turns out to be wrong, change it here first.

Scope of this program: army recruitment rule, guild dividends, mercenary companies, contracts, war participation, wages, and company reputation.

---

## 1. Army recruitment rule

`Rules.CAN_RECRUIT_PROFESSIONAL_ARMY` already exists and `laws.yml` already sets it false on levy-focused laws, but **nothing reads it**. Lock:

- When the rule is false, a faction may only expand regiments where `Regiment.isLevy()` is true.
- Enforced at the expansion entry point, not only hidden in the GUI. GUI shows the blocked regiments greyed with the reason.
- Mercenary regiments are never gated by this rule; they are not faction regiments (see section 4).

---

## 2. Dividends

Guilds may pay a share of profit to their members. Base is 0%.

**Order of operations (locked):**

1. `dividendBase` = the same sum as `Ledger.getNetIncome()` but with `DIVIDENDS`, `DIVIDEND_PAYMENT` and `DIVIDEND_PAYOUT` excluded. Guild tax is already inside net income as `GUILD_PAYMENTS`, so "net income after the faction takes its guild tax" is exactly this number.
2. `pool` = `max(0, dividendBase) * dividendPercent / 100`.
3. `tax` = `pool * factionDividendTaxRate / 100`, withheld from the pool.
4. Members split `pool - tax` **equally**.

**Why the exclusion matters:** defining the payout as a percentage of `getNetIncome()` is circular, because net income contains `DIVIDEND_PAYOUT`. The exclusion list is the same technique already used by `Ledger.getInternalTaxableIncome()` and `isCrossFactionGrossCashflow`.

**Ledger entries (all three already exist in `Cashflow`):**

| Entry | Side | Value |
|-------|------|-------|
| `DIVIDEND_PAYOUT` | paying guild, expense | `-(pool - tax)` |
| `DIVIDEND_PAYMENT` | paying guild, expense | `-tax` |
| `DIVIDENDS` | faction capital, income | sum of `tax` from its guilds |

No double count: payout and tax are disjoint slices of the pool.

**Rules:**

- The base guild (faction capital) cannot pay dividends. It is the treasury, not a company.
- Bankrupt guilds pay nothing. `Ledger` already returns 0 at the top of `getIncome` and returns early in `applySettlementFor` when bankrupt; do not add a second bankruptcy check.
- Negative base pays nothing (clamped at step 2).
- **Anti payday-joining:** a member only receives a share if they were a member at the previous daily tick. Config toggle, default on.
- Players receive it in their **bank** (`Accounts.BANK`), not the pouch, and see it as the existing `PlayerCashflow.DIVIDEND_PAYOUT`.
- After other daily settlement, the pool is **clamped to what the guild can still afford**. The payout must not push the guild negative or trigger `liquidateRandom()`. Tax and member shares scale with that clamped pool.
- Only the guild leader can change the percentage. Button in the guild GUI next to the ledger.

---

## 3. Ledger discipline (applies to every new cashflow in this program)

The recursion hazard in `Ledger` is **percentage-of-another-ledger's-income** cashflows. `GUILD_PAYMENTS`, `OVERLORD_TAX`, `TRIBUTE_PAYMENTS` and `WAR_REPARATIONS_PAYMENT` are all `base * rate` where base is a taxable-income sum, and the aggregate halves (`GUILDS`, `VASSALS`, `TRIBUTES`, `WAR_REPARATIONS`) read other guilds' ledgers. Two guards keep that finite: the `if(!guild.isBase()) return 0` early returns, and the `isCrossFactionGrossCashflow` exclusion list.

**Locked rules:**

1. **No mercenary or dividend amount may ever be a percentage of another ledger's income.** Every contract figure is an absolute denar value written into the contract at signing. This is what keeps the new cashflows structurally incapable of recursing.
2. **Receivers pull nothing.** Follow the loan pattern: the payer computes from its own objects, and the receiver reads a pre-populated map pushed to it. `Loan.pay()` pushes into the issuer via `addLoanPaymentEntry`; mercenary income does the same.
3. **`affectsInflation` is false on every new entry.** Only `TRADE` mints and the upkeeps sink. Wages and contracts are transfers between existing balances, and player wallets are already treated as part of the same supply (citizen tax carries no inflation flag).
4. **`grossCounted`:** contract income is business income, so true. Refunds are false; taxing compensation for a failure to deliver would make the refund figure mean different things per faction guild-tax rate.

**Vassal tax rollup is correct as written.** `VASSALS` reading each vassal's `OVERLORD_TAX` walks the whole vassal chain on purpose, and it terminates because cycles cannot be created: `RelationManager.setRelation`, `transferSubject` and `WarGoalValidator` all reject with `isOnOverlordPath`. No change needed.

Performance is explicitly **not** a concern for this program. Do not add memoization.

---

## 4. Mercenary company

A company is a **company inside a company**: hosted and owned by a guild, but its membership is not the guild's membership.

- **One company per guild.** The host guild owns it. Company finances are the guild's finances; there is no separate bank.
- **Formation** costs `100` denars (config) and takes **24 hours**. Grants **1 slot** on completion.
- **Name and banner** are set by the company. Guild banner plumbing already exists (`GuildData.banner`).
- **Leader** is the guild leader, always and automatically. Leadership follows the guild leader when it changes.
- **Membership:** the company leader invites by command, the invitee accepts or declines. Membership is not restricted by guild, faction, or nationality.
- **A player may be in one company at a time.** The company leader may kick at any time.
- **Character gate:** creating a company and joining one both require the acting character to have the `mercenary` trait (`rpcharacters/src/main/resources/traits/evil-traits.yml`). Factions stay player-based. Each company tick (once per second, before formation countdown) checks the guild leader even if they are not enlisted, then every enlisted name. Online players with no active character or without the trait are ineligible: kick a fighter, disband if it is the leader (abort if still founding). Offline players and a missing RPCharacters plugin are left alone. Disbanding terminates active contracts as a company-side breach (refund and reputation hit, same as a slot breach) and drops open offers.

### Slots

Slots reuse the military system with a new `mercenary` regiment type.

- The mercenary regiment is **invisible to factions**. `Military`'s constructor loops `RegimentLoader.getRegiments()` and gives every faction every type, so the type carries a `mercenary: true` flag and is filtered out there. The company holds its own regiment instance.
- **Expansion takes 24 hours** (`expansion-time: 86400`), reusing `MilitaryExpansion`. Expansions persist across restarts.
- **You cannot expand while you already have one unfilled slot.** Unfilled means no enlisted player assigned, not un-contracted. This is deliberate: capacity must be built in peacetime, not conjured when war breaks out.
- **Upkeep is 8 denars per slot per day** (config), paid by the host guild, not by or to the enlisted player. Same shape as a faction paying for a professional army.
- **You cannot hire more players than you have slots**, and you cannot promise more slots in overlapping contracts than you own (see section 5).

### Upgrades

Company upgrades use the existing `Guild.upgrade.Upgrade` system so PvP stats are available to regular guilds later if wanted. `Upgrade` needs a new `max-level` field; it has none today.

| Upgrade | Per level | Max level | Cap | Upkeep |
|---------|-----------|-----------|-----|--------|
| Max health | +0.5 | 10 | +5.0 | 10 d/day per level |
| Max mana | +1 | 10 | +10 | 10 d/day per level |
| Mana regen | +0.1 | 10 | +1.0 | 10 d/day per level |

`GuildModifier` gains `MAX_HEALTH`, `MAX_MANA`, `MANA_REGEN`.

**These buffs apply only while the player is in a battle as a hired mercenary.** Not in the world, not in a battle they joined as a normal faction fighter. This is the whole reason the buffs are gated: nobody should found a company to farm stats. The GUI must say so on every upgrade item.

Mana and mana regen are **MMOCore** stats, not the magic plugin's per-spell mana. `magic/src/main/java/net/tfminecraft/magic/integration/SpellModifierApplyService.java` is the template: register MythicLib modifiers against `MMOPlayerData`, store the applied list per player UUID, unregister from that stored list. Use `StatModifier` rather than `SkillModifier`. Max health is a plain Bukkit `AttributeModifier`, named so it is idempotent to strip. Everything must be stripped on battle end, battle abort, death, and disconnect.

---

## 5. Contracts

Modelled on loans, including a signed written book (`LoanBook`).

**Every number lives in the contract.** Nothing is recomputed later. Config only supplies minimums and the pre-filled defaults the contract spawns with.

| Field | Meaning | Config minimum |
|-------|---------|----------------|
| Slots hired | How many slots are reserved | - |
| Price per slot per battle | Paid for each battle that starts | 50 d |
| Price per slot per day | Paid every day, battle or not | 10 d |
| Duration in days | How long slots are reserved | max 14 |
| Absence refund per slot per battle | Paid back for each slot that failed attendance | must be **at least** the per-battle price |
| Breach refund | Paid if the company drops below promised slots | default 500 d, set in contract |
| Peacetime wage (optional) | Flat denars per day per soldier, base plus per-player override | - |

- The company sets its own prices and may not go below the config minimums.
- **The absence refund must be at least the per-slot per-battle price.** Otherwise no-showing is more profitable per head than showing up, which inverts the whole incentive. Validated at contract creation.
- The contract book lists slots, both prices, duration, both refunds, and the company's reputation at signing.
- **No over-promising:** the constraint is on **overlapping** contract windows. Two back-to-back 7-day contracts for every slot are legal; two overlapping ones are not. This needs a reservation calendar, not a running total.
- **Hiring is local.** You must be at the company's home settlement to sign. `/mercenaries` lists companies and where they are based but cannot be used to hire remotely.
- Any government member of the hiring faction may sign, not only the leader.
- Offers expire if unaccepted, same as loans.

### Termination

| Trigger | Result |
|---------|--------|
| Duration elapses | Ends normally, reputation gain if attendance was clean |
| Company drops below promised slots | Terminated, breach refund paid, **large** reputation hit |
| Company disbands (leader's active character has no mercenary trait) | Same as a slot breach: refund paid, large reputation hit. Open offers are dropped. |
| Host guild goes bankrupt | Terminated. No refund is possible; a bankrupt guild is inert in both directions by design. Bankruptcy is a real-world style exit and its punishment is social, not mechanical. |
| Loyalty conflict appears mid-contract (see section 6) | Terminated with no breach refund and no reputation change. Neither party caused it. Days already served are still paid. |

---

## 6. Loyalty rules

Two separate checks.

**Company level:** a company cannot fight against its host faction. It may only take a contract for a side where the opposing side does not include or is not allied to its host faction, following nested relationships. `Side.isParticipating(Faction)` already walks leader, subjects and joined secondaries.

**Player level:** an enlisted player from a different faction may fight their own faction **only if they are not part of that faction's government**, meaning not the faction leader and not a council member. Turncoat citizens are allowed; turncoat rulers are not.

**Re-check hook (this is the gap that will bite):** `isParticipating` only counts secondaries that have already *joined*. An ally joining later, a vassalization, or an election can flip a legal contract into an illegal one after signing. Re-validate on: faction joins war, relation changed, government changed. Outcome is the "loyalty conflict" row in section 5.

A company may be hired by its own host faction. That is the normal case and only costs the faction the tax and wage complement.

---

## 7. War and battle

- Mercenary companies are a **third participant kind**, neither main nor secondary. They are listed with soldiers equal to the slots promised in the contract.
- **Implementation shape:** a separate `List` on `Side`, not a generalized `Participant`. `Participant` is `Faction`-typed all the way down (leader, subjects, allies, backers) and `WarCommitment` is keyed by faction id; generalizing it would ripple into persistence, peace deals, war goals, occupation and casualties for no gameplay gain. Companies are genuinely not factions, so a parallel list is also the honest model. Only the display and lives paths learn about them.
- **Attendance is present at battle start and present at battle end.** "Present at end" means on the battle roster when it resolves, not alive and not online. A mercenary who burns through the shared lives and is eliminated has fought; failing them would punish exactly the people who fought hardest. Relogging mid-battle and finishing is fine.
- Failing attendance for a slot triggers the absence refund for that slot.
- **Every battle that actually starts in the plugin counts**, and the per-day price fires on battle days too. A battle day costs day rate plus battle rate. The contract book must say this so it does not read as either/or.
- **Lives from mercenary regiments are only added for slots that are filled and attending**, computed from the roster at battle start rather than from a commitment row written earlier.
- **Lives are shared with the whole side.** No double-count problem exists: `BattleLivesService.countRosterFighters` builds a `Set<UUID>` across all warbands, so a player who is both faction militia and mercenary is subtracted exactly once. If the company's host faction is a belligerent, the person counts as a mercenary first.

---

## 8. Wages

Two kinds, both paid by the host guild to the enlisted player.

| Kind | Basis |
|------|-------|
| Active wage | A percentage of what that slot earns. Base percentage plus a per-player override. |
| Peacetime wage | Optional flat denars per day. Base plus a per-player override. Paid whether or not there is a contract. |

Worked example, locked: at a 20% base against the config minimums, a soldier earns 2 denars per day and 10 denars per battle.

- Active wage is only earned while the slot is under contract. No contract, no active wage.
- Per-battle wage requires passing attendance for that battle.
- **Structural gap:** `DailyGuildTransfers` is guild to guild plus a per-guild external delta. There is no guild to player leg. Wages need one, settled in `FactionManager.settleIncome()` alongside the guild transfers. Route the actual money through the existing `DenarEconomyPlayerBank.PlayerBank` wrapper rather than calling `DenarEconomy` directly.
- The company GUI must show total daily burn (slots times 8, plus upgrade upkeep, plus peacetime wages) because a bankrupt host guild silently voids every contract it holds.

### New ledger entries

Guild side (`Cashflow`), five entries in the pairs the ledger already uses:

| Entry | Side | Notes |
|-------|------|-------|
| `MERCENARY_CONTRACT` | company host guild, income | `grossCounted` true |
| `MERCENARY_PAYMENTS` | hiring faction capital, expense | |
| `REFUNDS` | hiring faction capital, income | `grossCounted` false |
| `REFUND_PAYMENTS` | company host guild, expense | |
| `WAGE_PAYMENTS` | company host guild, expense | the only guild to player leg |

Player side (`PlayerCashflow`), one entry:

| Entry | Notes |
|-------|-------|
| `WAGES` | income, shows in `/ledger` |

Mercenary income and refunds are **separate entries**, never netted against each other.

Open decision left to implementation: whether wages are taxable as citizen income. Default to **not** taxable and revisit.

---

## 9. Reputation

Company reputation is the credit score system for violence. Mirror `Guild.loans.CreditCalculator` and `LoanHandler.changeCreditScore` exactly: an int, default `50`, clamped `0..100`, persisted on the company, with a coloured display string.

| Event | Direction |
|-------|-----------|
| Contract completes with full attendance throughout | Up |
| Battle with partial attendance | Down, scaled by the fraction absent |
| Contract broken early by the company | Large hit |
| Loyalty conflict termination | No change |

Severity is computed the way `CreditCalculator` does it: weighted factors clamped to 0..1, multiplied by a per-event maximum, so an early breach on a barely-served contract hurts far more than one that almost finished. Reputation shows on the `/mercenaries` list and is stamped on the contract book at signing.

With bankruptcy as a legitimate exit and refunds capped at whatever the contract says, reputation is the only thing that makes a repeat market work instead of a one-shot scam market.

---

## 10. Discoverability and stamps

- `/mercenaries` lists companies with slots, prices, reputation, and home settlement.
- **Stamp home settlement on companies, guilds and factions.** `faction.getSettlementHandler().getByProvince(provinceId)` already exists and is used by `BattleNamingService` and `WarMapExporter`. Show the settlement name, no coordinates; the website has the location.
- `tiers.yml` loses `Mercenary Company` from the village tier name list to avoid colliding with real companies.

---

## 11. Config defaults

| Key | Default |
|-----|---------|
| Formation cost | 100 d |
| Formation time | 24 h |
| Slot expansion time | 24 h |
| Slot upkeep | 8 d/slot/day |
| Minimum price per slot per battle | 50 d |
| Minimum price per slot per day | 10 d |
| Maximum contract duration | 14 days |
| Default breach refund | 500 d |
| Upgrade upkeep | 10 d/day per level |
| Upgrade max level | 10 |
| Dividend percent | 0% |
| Dividend requires previous-tick membership | true |

---

## 12. Explicitly out of scope

| Item | Why / when |
|------|------------|
| Assassins | Next program. Build the contract object with a kind discriminator so it is reused, not copied. |
| Character-trait gate enforcement | After the player to character migration, which is last |
| Generalizing `Participant` beyond factions | Never in this program |
| Ledger memoization | Not needed yet |
| A second bank for companies | Never; company money is guild money |
| PvP stats as a normal guild upgrade | Possible later, which is why they are `GuildModifier` entries |
| Making any mercenary figure a percentage of income | Never |
