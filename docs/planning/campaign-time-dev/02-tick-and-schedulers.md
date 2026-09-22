# Batch 02 — Tick + hour gate + raid schedulers

## Goal

Make the **driver** of campaign time use `CampaignClock`, and fix scheduler edge cases when time is spoofed.

## Changes

### `BattleScheduleTickService`

| Location | Change |
|----------|--------|
| `start()` runnable | `tick(CampaignClock.now())` instead of `Instant.now()` |
| `shouldRunForHour` | unchanged logic; uses spoofed `now` from tick |
| New package method | `onClockOffsetChanged()` → `resetHourGateForTests()` + cancel raid tasks |

After any `CampaignClock.add` / `reset`, call `onClockOffsetChanged()` then optionally `tick(CampaignClock.now())` once for immediate effect.

### `CampaignRaidMusterScheduler` / `CampaignRaidFightScheduler`

| Issue | Fix |
|-------|-----|
| Bukkit `runTaskLater` uses real wall clock | When `CampaignClock.isSpoofed()`, **do not** schedule tasks; rely on `processOverdue(war, now)` from tick only |
| Callback uses `Instant.now()` | Use `CampaignClock.now()` in `onMusterEnd` / `onFightEnd` callbacks |
| Offset change | Expose `cancelAllScheduled()` (or per-war cancel) called from `onClockOffsetChanged()` |

`delayTicksUntil(endsAt, now)` already uses injected `now` — keep that; ensure `now` is `CampaignClock.now()` at schedule time.

### `WarScheduleAdminService.closeVote(war, Instant.now())`

Admin close should use `CampaignClock.now()` when evaluating close rules (batch 03).

## Files

| Action | Path |
|--------|------|
| Edit | `War/campaign/runtime/BattleScheduleTickService.java` |
| Edit | `War/campaign/raid/CampaignRaidMusterScheduler.java` |
| Edit | `War/campaign/raid/CampaignRaidFightScheduler.java` |

## Acceptance

- [x] `campaigntime add 4h` + manual `tick(now)` closes vote if hour gate crossed (test)
- [x] Spoofed muster end fires via `processOverdue`, not 60s real wait
- [x] No double muster→fight transition when both Bukkit task and overdue run
