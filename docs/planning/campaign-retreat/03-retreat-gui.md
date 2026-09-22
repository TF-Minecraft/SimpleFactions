# Batch 03: Retreat GUI

**Status:** done

## Deliverable

Retreat button, confirm dialog, and route **Retreated** lore.

## Changes

| File | Change |
|------|--------|
| `CampaignUiCopy.java` | `RETREATED_LABEL` |
| `CampaignRouteRenderer.java` | `isSlotConceded` before `isFoughtSlot` in `buildRouteLore` |
| `CampaignCreator.java` | `createRetreatButton` (slot **46**) |
| `CampaignView.java` | `canRetreat`, button, click + `campaign_retreat` confirm |
| `InventoryManager.java` | Confirm handler for `campaign_retreat` |

## Tests

- `CampaignRouteRendererTest.buildRouteLore_retreatedSlot_showsRetreatedNotFought`

## Acceptance

- Pushed coalition war leader sees Retreat during `VOTING` before vote close
- Confirm calls `CampaignRetreatService.concedeActiveSlot`
- Conceded slots show **Retreated** lore (not **Fought**)
