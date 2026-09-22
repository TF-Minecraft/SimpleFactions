# Batch 05 — UI countdown ("Starts in …")

## Goal

On the **active next battle** route icon, show a gray relative time line under **Next battle**.

## New helper: `War/campaign/ui/CampaignScheduleCountdown.java`

```java
public final class CampaignScheduleCountdown {
    public static Optional<String> formatNextMilestone(War war, Instant now);
}
```

Returns lore fragment only (no color prefix); caller wraps with `CampaignUiCopy.MUTED`.

### Phase logic

| Condition | Label | Target instant |
|-----------|-------|----------------|
| `SCHEDULED` + `scheduledBattleAt != null` | `Starts in %s` | `war.getScheduledBattleAt()` |
| `VOTING` + on battle day + before vote close | `Vote closes in %s` | `BattleWindowService.atScheduleHour(battleDay, Cache.warVoteCloseHour)` |
| Not on battle day yet | `Battle day in %s` | `battleDay.atStartOfDay(SCHEDULE_ZONE)` |
| Past target / in battle | omit or `Starting now` | — |

- `seconds = max(0, target.epochSecond - now.epochSecond)`
- `TimeFormatter.formatTime((int) seconds)` → `1h 32m`
- Use `CampaignClock.now()` at all GUI call sites

## Route icon

**File:** `War/campaign/progression/CampaignRouteRenderer.java`

After the green **Next battle** line:

```java
CampaignScheduleCountdown.formatNextMilestone(war, CampaignClock.now())
    .ifPresent(text -> lore.add(StringFormatter.formatHex(CampaignUiCopy.MUTED + text)));
```

Only on the active schedule slot (same condition as today’s **Next battle** line).

## Schedule panel (optional same batch)

**File:** `Managers/Inventory/CampaignCreator.buildScheduleInfoLines`

When `SCHEDULED`, add matching **Starts in** line alongside existing **Fight At** absolute time (keep both: relative + CET/EST).

## Acceptance

- [x] Scheduled fight 1h 32m ahead shows `Starts in 1h 32m` in gray under route icon
- [x] After `campaigntime add 1h 31m`, reopening campaign GUI updates countdown
- [x] Voting on battle day shows `Vote closes in …` when fight not yet scheduled
