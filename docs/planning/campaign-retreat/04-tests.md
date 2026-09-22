# Batch 04: Retreat unit tests

**Status:** done

## Deliverable

[`CampaignRetreatServiceTest`](../../../src/test/java/me/Plugins/SimpleFactions/War/campaign/progression/CampaignRetreatServiceTest.java)

## Coverage

| Area | Tests |
|------|-------|
| Helpers | `pushedCoalition` (3 push targets), `slotKey` formatting |
| Eligibility rejections | wrong phase, retake phase, post-battle choice, vote closed, wrong leader |
| Eligibility success | defender before vote close |
| Invasion concede | index/cursor advance, no battles fought, no fuel spend, votes persist, conceded key, no post-battle choice |
| Siege concede | fort flip + index advance without battles fought |
| Counter-push | counter index only; attacker is pushed leader |
| Multi-click | two slots in same voting window |

## Out of scope

Route renderer **Retreated** lore: `CampaignRouteRendererTest.buildRouteLore_retreatedSlot_showsRetreatedNotFought` (batch 03).

## Related

- Service: [02-retreat-service](./02-retreat-service.md)
- Mapper round-trip: `WarMapperTest.roundTrip_concededScheduleSlots` (batch 02)
