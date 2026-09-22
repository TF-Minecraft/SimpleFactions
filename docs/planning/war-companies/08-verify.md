# Batch 08: In-game verification matrix

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 6, Batch 4
**Reference doc:** [mercenaries.md](../../mercenaries.md)

The 21 checks that Phase 6 requires on a dev server. Everything here needs a live server and at least two players, so none of it is covered by `mvn test`; the automated backstop for each area is named at the bottom.

## Prerequisites

- Two factions and two players (a third helps for the dual-role and turncoat checks).
- `war.battle_voting.dev_min_players: 1` in `war.yml`.
- `/war admin devmode on` for roster fill, and `/war admin time` to move the campaign clock.
- Lower `mercenary-formation-seconds` and the `expansion-time` keys to `10` so the 24 h timers finish inside a session. See [dev-config.md](../../dev-config.md).
- The faction daily tick is real time (`timer >= 86400`), so the money checks need either a long session or a forced tick.

Commands: `/company <found|invite|accept|decline|kick|expand|draft|offer|contracts>`, `/mercenaries [list|hire <name>]`, `/ledger`.

---

## Recruitment and dividends

| # | Step | Expected |
|---|------|----------|
| 1 | Pass a levy-focus law, then try to expand a professional regiment | Refused at the expansion entry point, not just hidden. The military GUI greys the blocked regiments and shows the reason |
| 2 | Set a guild dividend to 20%, run a daily tick | Every eligible member gets an **equal** share, the faction collects dividend tax, and both show in `/ledger` |
| 3 | Add a member, then tick immediately | The new member is excluded (previous-tick membership) |

## Company

| # | Step | Expected |
|---|------|----------|
| 4 | Found a company for 100 d and wait out formation | Charged once, arrives with exactly **1 slot** |
| 5 | `/company expand` with the slot empty, then enlist someone and retry. Restart the server mid-expansion | Refused while empty; accepted once filled; the 24 h expansion survives the restart and resumes |
| 6 | Open every faction's military screen | The mercenary regiment appears **nowhere**, and faction manpower, slots and upkeep totals are unchanged |
| 7 | Buy one level of each of the three upgrades | Caps enforced at level 10, and every upgrade item carries the buff-scope warning (hired mercenary battles only) |

## Contracts

| # | Step | Expected |
|---|------|----------|
| 8 | Attempt a contract (a) under a minimum price, (b) over 14 days, (c) with an absence refund below the per-battle price, (d) over-promising overlapping windows | **Four distinct refusals**, each naming its own rule |
| 9 | Attempt to hire from outside the company's home settlement | Refused, and the message names the settlement you must travel to |
| 10 | Sign a valid contract as a **council member**, not the leader | Accepted. The book lists slots, both prices, duration, both refunds, the reputation stamp, and the sentence saying a battle day costs day price **and** battle price |

## War and battle

| # | Step | Expected |
|---|------|----------|
| 11 | Open the war screen | The company is listed as a mercenary with its promised slots, and its host faction is still **not** a belligerent |
| 12 | Fight a battle with every slot attending | Side lives match the formula with the mercenary slots folded into `committedRegiments`, and a dual-role player is subtracted **once** |
| 13 | Fight a battle with one slot absent | The absence refund accrues for that slot, and the per-battle wage is **not** paid for it |
| 14 | Log out mid-battle and return before resolution | Attendance passes (end means on the roster, not alive or online) |
| 15 | Roster a faction leader against their own faction, then a plain citizen | Leader refused; plain citizen allowed |

## Money and reputation

| # | Step | Expected |
|---|------|----------|
| 16 | Run a daily tick mid-contract | **Six** distinct ledger lines with correct signs. Host guild: `MERCENARY_CONTRACT` income, `REFUND_PAYMENTS` and `WAGE_PAYMENTS` expenses. Hirer capital: `MERCENARY_PAYMENTS` expense, `REFUNDS` income. Wages appear in the soldier's own `/ledger`. Income and refunds stay separate lines |
| 17 | Set a 20% base wage against the config minimums | 2 d per day and 10 d per battle |
| 18 | Kick a soldier so the company drops below the promised slots | Contract terminates, breach refund pays, reputation takes a large hit |
| 19 | Bankrupt the host guild mid-contract | Contract terminates, **no** refund, reputation hit |
| 20 | Have an ally join the war on the opposing side to force a loyalty conflict | Contract terminates, no refund, **no** reputation change, and the days already served are still paid |
| 21 | Complete a clean contract to term | Reputation rises and the new value shows on `/mercenaries` |

---

## Regression sweep

1. `cd simplefactions && mvn test` (full suite).
2. Confirm unchanged behaviour in: guild upgrades without a `max-level` (still uncapped), faction military totals and upkeep, existing battle lives, loan settlement.
3. Tribute and reparations bases **do** move now: `MERCENARY_CONTRACT` is gross-counted and is not a cross-faction cashflow, so contract income legitimately feeds `Ledger.getInternalTaxableIncome()`. Verify the numbers are sane rather than unchanged. Refunds and wages must not move the base.
4. Confirm the plugin still loads with **MythicLib and MMOCore absent**: one log line, mercenary buffs no-op, nothing thrown.

## Automated backstop

| Area | Test |
|------|------|
| Lives formula, dual-role subtracted once | `MercenaryLivesTest` |
| Attendance rules | `AttendanceServiceTest` |
| All four terminations, refunds, served days | `ContractTerminationTest` |
| Reputation per event, clamping, display bands | `MercenaryReputationTest` |
| Contract validation refusals | `MercenaryContractTest`, `SlotReservationsTest`, `ContractOfferFlowTest` |
| Remote hiring refused | `MercenaryMarketTest` |
| Levy-focus recruitment rule | `MilitaryRecruitmentRuleTest` |
| Dividends, tax, previous-tick membership | `LedgerDividendTest`, `GuildDividendEligibilityTest` |
| Slot rules and mid-expansion persistence | `MercenaryCompanySlotTest`, `MercenaryCompanyPersistenceTest` |
| Mercenary type kept out of faction military | `MercenaryRegimentTest` |
| 2 d/day and 10 d/battle | `WageSettingsTest`, `WageAccrualTest` |
| Six signed ledger lines, tribute and reparations base | `MercenarySettlementTest`, `LedgerMercenaryTest` |
| Player `WAGES` line | `PostSettlementPayoutsTest` |
| Optional dependencies absent | `MercenaryStatServiceTest` |

Steps 1 (GUI reason text), 6 (military screens), 10 (book in hand) and 11 (war screen) are the four that only a live server can really answer.
