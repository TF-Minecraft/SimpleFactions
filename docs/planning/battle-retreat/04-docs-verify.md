# Batch 04: Documentation + verify

**Status:** done

## Doc updates

| File | Change |
|------|--------|
| [wars.md](../../wars.md) | Battle retreat subsection; strategic retreat cross-link; `startedAt` persistence; config key |
| [dev-config.md](../../dev-config.md) | Battle retreat E2E workflow |
| [README.md](../../README.md) | Planning link |
| [AGENTS.md](../../../AGENTS.md) | Related docs row |
| [roadmap.md](../../roadmap.md) | Shipped bullet |

## Manual verify matrix (test server)

| # | Step | Expected |
|---|------|----------|
| 1 | Start campaign field battle | Battle `started`, warbands enrolled |
| 2 | Warband leader `/warband retreat` before cooldown (config 1200) | Rejection with minutes remaining |
| 3 | Set `retreat_min_elapsed_seconds: 0`, reload config | Cooldown bypassed |
| 4 | `/warband retreat` | Confirm GUI opens |
| 5 | Cancel | Battle continues |
| 6 | Confirm | Opponent wins; battle ends; normal post-battle flow |
| 7 | Partial casualties before retreat | Ledger counts apply; not full wipe |
| 8 | Final battle retreat (optional) | War ends per normal outcome rules |
| 9 | Non-leader runs command | Rejected; no GUI |
| 10 | Campaign raid | Rejected |

## Acceptance

- [x] Doc links resolve within `simplefactions/docs/`
- [ ] Manual matrix executed once on test server (staff checklist)
- [x] `BattleWarbandRetreatServiceTest` + `War.**` suite green
- [x] `00-index.md` batch 04 marked complete

## Verification commands

```bash
mvn test -Dtest=BattleWarbandRetreatServiceTest,BattleMapperTest
mvn test -Dtest=me.Plugins.SimpleFactions.War.**
```

## Distinction checklist

| Feature | Strategic retreat | Battle retreat |
|---------|-------------------|----------------|
| Phase | `VOTING` (map) | Started field/siege battle |
| Who | Pushed coalition war leader | Warband leader |
| Command / UI | Campaign GUI slot 46 | `/warband retreat` |
| Initiative cost | None | Normal battle loss (fuel spent) |
| Casualties | None | Ledger only |
