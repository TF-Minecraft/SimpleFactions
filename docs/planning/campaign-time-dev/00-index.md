# Campaign time dev mode — Batch index

**Repo:** `simplefactions`  
**Goal:** Spoof campaign schedule time on the test server so staff can walk the full war pipeline (vote → hold/push → raid → auto battle) without waiting on real clock. Add a **Starts in X** countdown on the next-battle route icon.

**Scope:** Campaign / war schedule only. **Out of scope:** `FactionManager` daily timer (86400), guild income, vehicle upkeep, construction queues.

**Canonical gameplay:** [wars.md](../../wars.md) · **Existing dev tools:** [dev-config.md](../../dev-config.md)

---

## Problem

Campaign logic mostly accepts `Instant now`, but boundaries call `Instant.now()` directly. There is no way to fast-forward Paris schedule time for E2E QA. The route map shows **Next battle** with no relative countdown.

**Gotcha:** Default `war.battle_schedule.first_battle_day_after_declare: true` sets battle day to **tomorrow** (Paris). `+1h` alone does not open voting; devs need `+1d` or `skip-to-battle-day`.

---

## Design lock

| Piece | Choice |
|-------|--------|
| Clock | `CampaignClock` in `War/campaign/runtime` — `Duration` offset on `Instant.now()` |
| API | `CampaignClock.now()`, `add(Duration)`, `reset()`, `getOffset()`, `isSpoofed()` |
| Stamping | **Real** time for `war.startedAt`, `committedAt`, persistence audit fields |
| Eligibility | **Spoofed** time for windows, overdue checks, GUI, tick |
| Command | `/faction campaigntime …` — admin only (`Permissions.isAdmin`) |
| Parse | `add 1h 31m`, `add 1h31m`, `add 1d`; `reset`; `status`; optional `skip-to-battle-day <warId>` |
| Tick | `BattleScheduleTickService` uses `CampaignClock.now()`; reset hour gate on offset change |
| Raid tasks | On offset change: cancel Bukkit muster/fight tasks; rely on `processOverdue` while spoofed |
| Countdown | Gray lore under **Next battle** on active route slot (`CampaignRouteRenderer`) |
| Countdown source | `CampaignScheduleCountdown` — phase-aware next milestone vs `CampaignClock.now()` |
| Display format | Reuse `TLibs` `TimeFormatter.formatTime(seconds)` → `1h 32m` |
| Faction day | Not wired |

---

## Batches (implement in order)

| # | Doc | Deliverable |
|---|-----|-------------|
| 1 | [01-campaign-clock](./01-campaign-clock.md) | `CampaignClock`, `CampaignDurationParser` |
| 2 | [02-tick-and-schedulers](./02-tick-and-schedulers.md) | Tick + hour gate + raid scheduler integration |
| 3 | [03-boundary-wiring](./03-boundary-wiring.md) | Replace campaign `Instant.now()` at boundaries |
| 4 | [04-campaigntime-command](./04-campaigntime-command.md) | `/faction campaigntime` + tab complete |
| 5 | [05-ui-countdown](./05-ui-countdown.md) | **Starts in** / **Vote closes in** on route + schedule panel |
| 6 | [06-tests](./06-tests.md) | Unit tests for clock, parser, countdown, tick gate |
| 7 | [07-docs-verify](./07-docs-verify.md) | `dev-config.md`, `wars.md` snippet, E2E checklist |

---

## Target E2E workflow (test server)

1. Declare war (two factions).
2. `campaigntime skip-to-battle-day <warId>` **or** `add 1d` if battle day is tomorrow.
3. `add 4h` (or whatever) until vote window / vote close hour.
4. Vote (or `warschedule closevote`); confirm **SCHEDULED** and **Starts in** on route icon.
5. `add` until `scheduledBattleAt` — battle auto-starts via tick.
6. Post-battle: hold/push choice deadline via further `add`.
7. Raid window: `add` into raid hours; launch raid; muster/fight via tick or short real wait.
8. `campaigntime reset` when done.

---

## Checkpoint

```text
CampaignClock.now() drives all schedule eligibility
/faction campaigntime add 1h 31m advances Paris battle-day logic
Route icon: "Next battle" + gray "Starts in 1h 32m" when fight is scheduled
BattleScheduleTickService closes vote / starts battle / processes overdue raids under spoofed time
campaigntime reset restores real time; hour gate and raid tasks sane
War.** tests green
```

**Done when:** Batch 7 verify matrix passes; `dev-config.md` documents commands; no dependency on faction `newDay`.

---

## Status

| Batch | Status |
|-------|--------|
| 01 Campaign clock | done |
| 02 Tick + schedulers | done |
| 03 Boundary wiring | done |
| 04 campaigntime command | done |
| 05 UI countdown | done |
| 06 Tests | done |
| 07 Docs + verify | done |
