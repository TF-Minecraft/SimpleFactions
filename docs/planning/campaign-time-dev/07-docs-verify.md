# Batch 07 — Documentation + verify matrix

## Doc updates (SF repo only)

### [dev-config.md](../../dev-config.md)

Add section **Campaign time dev mode** after Battle dev mode:

| Mechanism | Detail |
|-----------|--------|
| `/faction campaigntime add 1h 31m` | Advance spoofed Paris schedule clock |
| `/faction campaigntime reset` | Restore real time |
| `/faction campaigntime status` | Show offset + Paris date/hour |
| `/faction campaigntime skip-to-battle-day <warId>` | Jump to war's battle day (fixes tomorrow-default) |
| Route GUI | Gray **Starts in 1h 32m** under **Next battle** when scheduled |

Update **Campaign UX (test server E2E)** workflow to prefer `campaigntime` over manual `warschedule setscheduled` where possible.

### [wars.md](../../wars.md)

Short subsection under dev/admin tools (near battle dev mode): link to `dev-config.md` campaign time commands. One paragraph, no duplicate full spec.

### [docs/README.md](../../README.md)

Optional link under dev-config:

```markdown
- [planning/campaign-time-dev/](./planning/campaign-time-dev/00-index.md) - campaign time spoof batch (in progress)
```

### [AGENTS.md](../../../AGENTS.md)

Add row to Related docs table:

`| [docs/planning/campaign-time-dev/](docs/planning/campaign-time-dev/00-index.md) | Campaign clock dev batches |`

### [roadmap.md](../../roadmap.md)

When shipped, move bullet under **Shipped**:

- Campaign time dev mode (`campaigntime`, route countdown)

---

## Manual verify matrix (test server)

| # | Step | Expected |
|---|------|----------|
| 1 | Declare war | `battleDay` tomorrow if `first_battle_day_after_declare: true` |
| 2 | `campaigntime skip-to-battle-day <id>` | `isOnBattleDay` true; vote UI active |
| 3 | `campaigntime add` until before vote close | Hour votes clickable |
| 4 | `add` past `vote_close_hour` | Vote closes; phase → SCHEDULED or postpone |
| 5 | After schedule | Route shows **Starts in X**; decreases after more `add` |
| 6 | `add` past `scheduledBattleAt` | Battle auto-starts (tick or immediate post-add tick) |
| 7 | Post-battle hold/push | Choice deadline via `add` to `defender_choice_deadline_hour` |
| 8 | Raid window hours | `canLaunch` succeeds; muster → fight without long real wait |
| 9 | `campaigntime reset` | Status shows zero offset; real schedule restored |
| 10 | Restart server | Offset cleared (volatile); no persistence bleed |

---

## Acceptance

- [x] All doc links resolve within `simplefactions/docs/`
- [ ] Manual matrix executed once on test server (staff checklist below)
- [x] `00-index.md` status table marked complete
