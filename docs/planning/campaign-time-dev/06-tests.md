# Batch 06 — Tests

## New test classes

| Class | Covers |
|-------|--------|
| `CampaignClockTest` | add, reset, now offset, resetForTests |
| `CampaignDurationParserTest` | `1h 31m`, `1h31m`, `1d`, invalid |
| `CampaignScheduleCountdownTest` | SCHEDULED starts-in, VOTING vote-closes, not-on-battle-day |
| `CampaignTimeCommandServiceTest` | parse + skip-to-battle-day math (pure, no Bukkit) |

## Extend existing

| Class | Add case |
|-------|----------|
| `BattleScheduleTickServiceTest` | tick with clock offset crosses vote close |
| `CampaignRaidMusterSchedulerTest` | `processOverdue` with spoofed now; no double fire |

## Patterns

- Use fixed `Instant.parse` + `BattleWindowService.atScheduleHour` (existing style).
- `CampaignClock.resetForTests()` in `@BeforeEach` / `@AfterEach`.
- Mock `CampaignClock` only if needed; prefer static reset over mockStatic.

## Command

```bash
cd simplefactions && mvn test -Dtest="CampaignClockTest,CampaignDurationParserTest,CampaignScheduleCountdownTest,BattleScheduleTickServiceTest,CampaignRaidMusterSchedulerTest"
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.War.**"
```

## Acceptance

- [x] All new tests pass
- [x] Full `War.**` suite green (644+ tests)
