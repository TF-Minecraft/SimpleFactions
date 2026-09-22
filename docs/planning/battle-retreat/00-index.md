# Battle retreat — Batch index

**Repo:** `simplefactions`  
**Goal:** Mid-fight retreat from live campaign field/siege battles via `/warband retreat`.

**Canonical gameplay:** [wars.md](../../wars.md) (batch 04)

**Distinct from:** [campaign-retreat](../campaign-retreat/00-index.md) (strategic map voting retreat).

---

## Batches

| # | Doc | Deliverable |
|---|-----|-------------|
| 1 | [01-core-service](./01-core-service.md) | `BattleWarbandRetreatService`, `startedAt`, config, `BattleEndReason.RETREAT` |
| 2 | [02-command-confirm](./02-command-confirm.md) | `/warband retreat` + confirm GUI |
| 3 | [03-tests](./03-tests.md) | `BattleWarbandRetreatServiceTest` |
| 4 | [04-docs-verify](./04-docs-verify.md) | `wars.md`, `dev-config.md`, manual matrix |

---

## Status

| Batch | Status |
|-------|--------|
| 01 Core service | done |
| 02 Command + confirm | done |
| 03 Tests | done |
| 04 Docs + verify | done |
