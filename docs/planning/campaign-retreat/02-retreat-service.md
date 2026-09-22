# Batch 02: Retreat service

**Status:** done

## Deliverable

[`CampaignRetreatService`](../../../src/main/java/me/Plugins/SimpleFactions/War/campaign/progression/CampaignRetreatService.java) plus `concededScheduleSlots` persistence on `War` / `WarData` / `WarMapper`.

## API

| Method | Purpose |
|--------|---------|
| `pushedCoalition(War)` | Coalition being pushed (`DEFENDER` on invasion push, `AGGRESSOR` on counter-push) |
| `canRetreat(War, Faction, Instant)` | Eligibility gate for GUI (batch 03) |
| `concedeActiveSlot(War, Faction, Instant)` | Concede one active schedule slot |
| `isSlotConceded(War, ScheduleLeg, int)` | Route lore helper (batch 03) |
| `slotKey(ScheduleLeg, int)` | Stable key: `invasion:0`, `counter:1` |

## Eligibility

All must pass:

1. War active, `BattleSchedulePhase.VOTING`
2. `PostBattleChoicePhase.NONE` and no unresolved push/hold choice
3. Before vote close (`!BattleScheduleService.isVoteCloseDue`)
4. Push target is `TOWARD_OBJECTIVE` or `TOWARD_AGGRESSOR_CAPITAL` (not retake)
5. Active schedule slot exists (`slotAtActiveIndex`, not `currentSlot`)
6. Caller is war leader of the pushed coalition

## Effect (per concede)

Mirrors [`CampaignBattleOutcomeService.applyCampaignBattleOutcome`](../../../src/main/java/me/Plugins/SimpleFactions/War/battle/campaign/CampaignBattleOutcomeService.java) except:

- **No** initiative spend, casualties, `campaignBattlesFought` increment, or post-battle Hold/Push choice
- **No** vote clear or `openVote` (stays `VOTING`, votes persist)
- **Yes** siege fort flip, occupation, schedule index advance, auto-push (`advanceAlongPushTarget`), initiative holder update, war end / walkover resolution
- Records conceded slot key before index advance

## Persistence

`War.concededScheduleSlots` (`Set<String>`) round-trips via `WarData.concededScheduleSlots` (`List<String>`).

## Tests

- `WarMapperTest.roundTrip_concededScheduleSlots` (this batch)
- Full `CampaignRetreatServiceTest` deferred to batch 04

## Next

Batch 03: retreat button + confirm in `CampaignView`, **Retreated** lore in `CampaignRouteRenderer`.
