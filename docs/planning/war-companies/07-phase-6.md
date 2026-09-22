# Phase 6: Docs and verify (batch plan)

**Lock:** [00-index.md](./00-index.md)
**Phase:** [01-phases.md](./01-phases.md) Phase 6

## Exit (must all be true)

- `wars.md` documents mercenaries as a participant kind and the attendance and lives rules.
- `roadmap.md` moves war companies to Shipped and names assassins as next.
- `TODO.md` no longer lists army recruitment rule or mercenaries.
- Config keys are documented with their defaults.
- The in-game checklist below passes on a dev server.
- Full suite green: `mvn test`.

---

## Batch 1 - Canonical docs

**Files:** `docs/wars.md`

1. **Participants** section gains mercenaries as a third kind: hired by contract, listed with promised slots, never a belligerent, and never a party to a peace deal. State plainly that a company's presence does not make its host faction a participant.
2. **Battle lives** section: mercenary regiments contribute only for filled and attending slots, computed from the battle-start roster; lives stay a shared side pool; a dual-role player is subtracted once.
3. **Attendance:** present at battle start and present at battle end, where end means on the roster at resolution rather than alive or online.
4. **Loyalty:** a company cannot fight its host faction; a non-government citizen of another faction may fight their own.
5. Link to [00-index.md](./00-index.md) as the gameplay lock rather than restating the config table.

---

## Batch 2 - Roadmap and TODO

**Files:** `docs/roadmap.md`, `TODO.md`

1. `roadmap.md`: war companies under Shipped with a link to this program. Next becomes assassins, then map chronicle events, then declare codes last. Note that assassins reuse the Phase 3 contract object with its `ContractKind` discriminator.
2. `TODO.md`: remove army recruitment rule and mercenaries from the War list. Add the two deferred items so they are not lost:
   - Character-trait gate for creating and joining a company, enabled after the player to character migration.
   - Decide whether mercenary wages are taxable as citizen income.
3. Add dividends to the shipped economy notes, since it was scaffolded for a long time and is easy to think is still missing.

---

## Batch 3 - Config documentation

**Files:** config docs wherever the other `Cache` keys are documented, plus inline comments in the YAML

Document every key with its default and, where it is not obvious, its interaction:

| Key | Default | Note |
|-----|---------|------|
| Formation cost | 100 d | One-off, taken at request |
| Formation time | 86400 s | Company arrives with 1 slot |
| Slot expansion time | 86400 s | Blocked while an unfilled slot exists |
| Slot upkeep | 8 d/slot/day | Host guild pays, not the soldier |
| Min price per slot per battle | 50 d | Company may charge more |
| Min price per slot per day | 10 d | Charged on battle days too |
| Max contract duration | 14 days | |
| Default breach refund | 500 d | Contract field, this is only the pre-fill |
| Upgrade upkeep | 10 d/day per level | |
| Upgrade max level | 10 | |
| Dividend percent | 0% | Per guild, leader sets |
| Dividend requires previous-tick membership | true | Blocks payday-joining |

Call out the one non-configurable rule: the absence refund **must** be at least the per-slot per-battle price, validated at contract creation, because a smaller refund makes no-showing more profitable per head than fighting.

---

## Batch 4 - In-game verification

Run on a dev server with the campaign clock accelerated where possible.

**Recruitment and dividends**

1. Pass a levy-focus law, confirm professional regiments cannot be expanded and the reason shows in the military GUI.
2. Set a guild dividend to 20%, run a daily tick, confirm each eligible member receives an equal share, the faction collects dividend tax, and the shares appear in `/ledger`.
3. Add a member, tick immediately, confirm the new member is excluded.

**Company**

4. Form a company for 100 d, wait out formation, confirm 1 slot.
5. Try to expand with the slot empty: refused. Enlist someone, expand, confirm 24 h and that it survives a server restart mid-expansion.
6. Confirm the mercenary regiment appears nowhere in any faction's military screen.
7. Buy one level of each upgrade, confirm the caps and the buff-scope warning text.

**Contracts**

8. Attempt a contract under a minimum price, over 14 days, with an absence refund below the battle price, and over-promising overlapping windows: four distinct refusals.
9. Attempt to hire remotely: refused, message names the settlement.
10. Sign a valid contract as a council member (not the leader). Confirm the book lists every field, the reputation stamp, and the both-prices sentence.

**War and battle**

11. Confirm the company appears in the war screen as a mercenary with promised slots, and that its host faction is still not a belligerent.
12. Fight a battle with all slots attending: confirm the lives figure matches the formula and that a dual-role player is subtracted once.
13. Fight a battle with one slot absent: confirm the absence refund accrues and the per-battle wage is not paid for that slot.
14. Log out mid-battle and return before resolution: attendance passes.
15. Attempt to roster a faction leader against their own faction: refused. Repeat with a plain citizen: allowed.

**Money and reputation**

16. Run a daily tick mid-contract: confirm six distinct ledger lines with correct signs, mercenary income and refunds shown separately, and wages in the soldier's `/ledger`.
17. Confirm a 20% base wage pays 2 d/day and 10 d/battle at the config minimums.
18. Kick a soldier to drop below promised slots: contract terminates, breach refund pays, reputation takes a large hit.
19. Bankrupt the host guild mid-contract: contract terminates, no refund, reputation hit.
20. Trigger a loyalty conflict by having an ally join the war on the opposing side: contract terminates, no refund, no reputation change, served days still paid.
21. Complete a clean contract to term: reputation rises, and the new value shows on `/mercenaries`.

---

## Batch 5 - Regression sweep

1. `mvn test` full suite.
2. Specifically confirm unchanged behaviour in: existing guild upgrades without a `max-level`, faction military totals and upkeep, existing battle lives tests, loan settlement, and the tribute and reparations bases (which the new gross-counted mercenary income legitimately feeds, so verify the change is intended and the numbers are sane rather than that they are unchanged).
3. Confirm the plugin still loads with MythicLib and MMOCore absent, with the stat service logging once and no-opping.

---

## Out of scope

- Assassins (next program)
- Enabling the character-trait gate
- Taxing wages
