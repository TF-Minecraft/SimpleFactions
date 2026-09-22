# Batch 01 — Campaign GUI refresh + vote close lock

## Goal

- Campaign view updates every second while open (countdown, schedule info, vote lock state).
- Hour votes cannot be toggled at or after `vote_close_hour` on battle day (UI + server).

## Changes

| File | Change |
|------|--------|
| `War/campaign/vote/BattleVoterEligibility.java` | `canToggleVote(war, faction, now)` |
| `War/campaign/vote/VoteResults.java` | `REJECTED_VOTE_CLOSED` |
| `War/campaign/vote/BattleVoteService.java` | Reject toggle after vote close |
| `Managers/Inventory/CampaignView.java` | In-place refresh; `isViewingCampaign` |
| `War/campaign/ui/CampaignViewRefreshService.java` | 1s tick + viewer registry |
| `SimpleFactions.java` | Start/stop refresh service |

## Tests

- `BattleVoteServiceTest` — before/after vote close
- `BattleVoterEligibilityTest` — `canToggleVote`

```bash
mvn test -Dtest="BattleVoteServiceTest,BattleVoterEligibilityTest"
mvn test -Dtest="me.Plugins.SimpleFactions.War.**"
```

## Acceptance

- [x] Campaign view refreshes in-place every 1s for open viewers
- [x] Hour votes not clickable after vote close (UI + `toggleVote`)
- [x] `REJECTED_VOTE_CLOSED` tested
- [x] `War.**` suite green

## Manual verify

1. Open campaign GUI during `VOTING` - countdown updates without clicking.
2. Before vote close - hour toggles work.
3. `campaigntime add` past vote close with GUI open - toggles lock within ~1s.
4. Installation pick lock unchanged.
