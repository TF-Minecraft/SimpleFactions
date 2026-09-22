# Batch 04 — `/faction campaigntime` command

## Command surface

**Permission:** `Permissions.isAdmin` (same as `/battle devmode`).

```
/faction campaigntime status
/faction campaigntime reset
/faction campaigntime add <duration...>
/faction campaigntime skip-to-battle-day <warId>
```

### `add`

Examples:

```
/faction campaigntime add 1h 31m
/faction campaigntime add 1h31m
/faction campaigntime add 1d
/faction campaigntime add 4h
```

On success:

1. `CampaignDurationParser.parse(...)` → `CampaignClock.add(duration)`
2. `BattleScheduleTickService.onClockOffsetChanged()`
3. `BattleScheduleTickService.tick(CampaignClock.now())` — immediate pass
4. Feedback: new offset, Paris date/hour, wars affected count (optional)

### `status`

```
Offset: +1h 31m
Schedule time (Paris): 2026-08-26 14:31
Real time (UTC): ...
```

### `reset`

- `CampaignClock.reset()`
- `onClockOffsetChanged()`
- Confirm message

### `skip-to-battle-day <warId>`

Sets clock offset so `BattleScheduleService.battleDayDate(CampaignClock.now())` equals `war.getBattleDay()` at **start of battle day** (00:00 Paris), or jumps to morning of battle day if already past midnight.

Alternative implementation: add enough duration to reach next occurrence of battle day — document chosen behavior in command help.

Resolves the **first_battle_day_after_declare** gotcha without requiring staff to guess `+1d`.

## Files

| Action | Path |
|--------|------|
| Add | `War/campaign/admin/CampaignTimeCommandService.java` (messages + handlers) |
| Edit | `Managers/CommandManager.java` — subcommand branch |
| Edit | `Utils/TabCompletion.java` — `campaigntime`, `add`, `reset`, `status`, `skip-to-battle-day`, war ids |

## Messages (player-facing)

| Case | Message |
|------|---------|
| Success add | `§aCampaign time advanced by §e1h 31m§a. Paris: §e...` |
| Reset | `§aCampaign time reset to real time.` |
| Not admin | `§cYou do not have access to this command.` |
| Bad duration | `§cInvalid duration. Example: §e/faction campaigntime add 1h 31m` |
| Unknown war | `§cUnknown war id.` |

## Acceptance

- [x] Tab complete works for subcommands
- [x] `add 1h 31m` changes `BattleScheduleService.battleDayHour` as expected in status output
- [x] `skip-to-battle-day` enables voting on freshly declared war (default config)
