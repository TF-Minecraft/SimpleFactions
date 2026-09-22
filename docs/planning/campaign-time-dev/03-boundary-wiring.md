# Batch 03 — Boundary wiring (`Instant.now()` → `CampaignClock.now()`)

## Rule

Replace `Instant.now()` with `CampaignClock.now()` at **campaign eligibility boundaries** only. Keep real `Instant.now()` for audit stamps and unrelated systems.

## War package (~20 call sites)

| File | Notes |
|------|-------|
| `BattleScheduleTickService` | done in batch 02 |
| `CampaignRaidMusterScheduler` / `CampaignRaidFightScheduler` | done in batch 02 |
| `CampaignRaidBattleEndService` | endRaid(..., clock) |
| `RaidCommandManager` | launch/join timestamps for eligibility |
| `RaidTargetService` | no-arg overloads → `CampaignClock.now()` |
| `BattleInstallationPickService` | no-arg overloads |
| `CampaignWarbandSignupService` | no-arg signup overload |
| `BattleAutoresolveService` | internal `Instant.now()` |
| `BattleInventoryManager` | signup open check |
| `CampaignUiTimeFormatter` | fallback `scheduleDate(now)` for display |

## GUI layer (~11 call sites)

| File | Notes |
|------|-------|
| `CampaignView` | raid entry, autoresolve, choice handlers |
| `CampaignRaidLaunchView` | eligibility, launch |
| `CampaignInstallationPickView` | lock checks |
| `CampaignCreator` | intel lines, pick lock |

## Command / admin

| File | Notes |
|------|-------|
| `CommandManager` | `warschedule closevote` |
| `WarScheduleAdminService` | admin close |

## Do NOT change (real time)

| File | Why |
|------|-----|
| `War.java` `startedAt` / `endedAt` | Historical fact |
| `WarCommitmentService` `committedAt` | Audit |
| `FactionManager.timer` | Faction day — out of scope |
| `VehicleInstallationLockService` | Optional follow-up; not required for campaign QA |

## Refactor pattern

Prefer keeping `foo(War war, Instant now)` signatures in domain services. Only **callers** switch to `CampaignClock.now()`.

## Acceptance

- [x] Grep `War/` for `Instant.now()` — only stamp/audit paths remain
- [x] Campaign GUI open under spoofed clock shows correct raid/signup/autoresolve state
