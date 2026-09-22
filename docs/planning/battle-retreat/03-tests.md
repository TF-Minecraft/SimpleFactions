# Batch 03: Retreat unit tests

**Status:** done

## Deliverable

[`BattleWarbandRetreatServiceTest`](../../../src/test/java/me/Plugins/SimpleFactions/War/battle/campaign/BattleWarbandRetreatServiceTest.java)

## Coverage

| Area | Tests |
|------|-------|
| `opponentSideId` | attacker/defender mapping, unknown side, missing opponent side |
| `remainingSecondsUntilRetreat` | before cooldown, after cooldown, null `startedAt` |
| Eligibility rejections | null player, not in warband, member not leader, pending leader, not in battle, war inactive (via join filter), battle not started, manual battle (via join filter), campaign raid, raid type, too early, null `startedAt`, no opponent |
| Eligibility success | field battle, siege battle |
| `retreat` apply | attacker retreat ends with defender win + `RETREAT`; defender retreat; rejected does not call `endBattle` |

## Notes

`CampaignBattleJoinService.findCampaignBattleForWarband` skips battles when `warId == null` or war is inactive, so `rejection_warInactive` and `rejection_manualBattle` assert `REJECTED_NOT_IN_BATTLE` (not the defensive `REJECTED_WAR_INACTIVE` / `REJECTED_NOT_CAMPAIGN_BATTLE` branches, which are unreachable via the join lookup).

## Related

- Service: [01-core-service](./01-core-service.md)
- Command: [02-command-confirm](./02-command-confirm.md)
- Mapper: `BattleMapperTest.roundTrip_startedAt` (batch 01)

## Next

Batch 04: `wars.md`, `dev-config.md`, manual verify matrix
