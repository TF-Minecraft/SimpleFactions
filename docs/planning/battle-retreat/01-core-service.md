# Batch 01: Core service

**Status:** done

## Deliverable

[`BattleWarbandRetreatService`](../../../src/main/java/me/Plugins/SimpleFactions/War/battle/campaign/BattleWarbandRetreatService.java) plus battle `startedAt` persistence, `BattleEndReason.RETREAT`, and config cooldown.

## API

| Method | Purpose |
|--------|---------|
| `canRetreat(Player, Instant)` | Eligibility gate for command/GUI (batch 02) |
| `retreatRejection(Player, Instant)` | Returns rejection enum or `null` if eligible |
| `retreat(Player, Instant)` | Validate then end battle with opponent win |
| `opponentSideId(Battle, String)` | Maps retreating side to `attacker` / `defender` opponent |
| `remainingSecondsUntilRetreat(Battle, Instant)` | Cooldown helper for player messages |

## Eligibility

All must pass:

1. Player leads a warband (`WarbandManager.getByLeader`)
2. Not `warband.isPendingLeader()`
3. Warband is enrolled in an active campaign battle (`CampaignBattleJoinService.findCampaignBattleForWarband`)
4. War active
5. Battle `started`, `warId != null`
6. Battle type `FIELD` or `SIEGE`; not campaign raid
7. Elapsed time since `battle.startedAt` ≥ `battle.retreat_min_elapsed_seconds` (default 1200)
8. Opponent side resolves to a real `BattleSide`

## Effect

```java
BattleEndSupport.endBattle(battle, opponentSideId, BattleEndReason.RETREAT);
```

- Opponent wins; ledger casualties apply as today
- `CampaignBattleOutcomeService` handles progression with **no special cases** (ignores `endReason`)
- Final battle retreat = normal battle loss (may end war)

## Persistence

| Field | Location | Notes |
|-------|----------|-------|
| `startedAt` | `Battle`, `BattleData.startedAt` (ISO-8601) | Set in `Battle.start()`; round-trip via `BattleMapper` |
| Migration | `BattleMapper.fromData` | If `started && startedAt == null` → `Instant.now()` |

## Config

```yaml
battle:
  retreat_min_elapsed_seconds: 1200
```

Loaded to `Cache.battleRetreatMinElapsedSeconds`.

## Messages

[`BattleWarbandRetreatMessages`](../../../src/main/java/me/Plugins/SimpleFactions/War/battle/campaign/BattleWarbandRetreatMessages.java) - `messageForResult(RetreatResult, Player, Instant)` for batch 02 command wiring.

## Tests

- `BattleMapperTest.roundTrip_startedAt` (this batch)
- Full `BattleWarbandRetreatServiceTest` in batch 03 (`03-tests.md`)

## Next

Batch 02: `/warband retreat` command + confirm GUI in `BattleCommandManager` and `InventoryManager`.
