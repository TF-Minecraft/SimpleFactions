# Batch 05: Documentation + verify

**Status:** done

## Doc updates

| File | Change |
|------|--------|
| [wars.md](../../wars.md) | Strategic retreat subsection; GUI legend + leader interactions; persistence field |
| [dev-config.md](../../dev-config.md) | Strategic retreat E2E workflow |
| [README.md](../../README.md) | Planning link |
| [AGENTS.md](../../../AGENTS.md) | Related docs row |
| [roadmap.md](../../roadmap.md) | Shipped bullet |

## Manual verify matrix (test server)

| # | Step | Expected |
|---|------|----------|
| 1 | `campaigntime skip-to-battle-day <id>`; open campaign GUI | Countdown / schedule info updates every ~1s |
| 2 | Before vote close | Hour toggles work |
| 3 | `add` past vote close with GUI open | Hour toggles lock within ~1s |
| 4 | Pushed leader sees Retreat; pusher does not | Button visibility (slot 46) |
| 5 | Confirm retreat | Slot **Retreated**; index + cursor advance; no Hold prompt |
| 6 | Check initiative fuel | Unchanged vs pre-retreat |
| 7 | Check hour vote selections | Still present |
| 8 | Second retreat same window | Next slot **Retreated**; still `VOTING` |
| 9 | Siege slot retreat | Fort flips; no battle fought |
| 10 | Counter-push war | Attacker (pushed) retreats; counter index advances |
| 11 | Retake phase | Retreat hidden / rejected |
| 12 | `campaigntime reset` | Cleanup |

## Acceptance

- [x] Doc links resolve within `simplefactions/docs/`
- [ ] Manual matrix executed once on test server (staff checklist)
- [x] `War.**` suite green
- [x] `00-index.md` batch 05 marked complete

## Verification commands

```bash
mvn test -Dtest=CampaignRetreatServiceTest,CampaignRouteRendererTest
mvn test -Dtest=me.Plugins.SimpleFactions.War.**
```
