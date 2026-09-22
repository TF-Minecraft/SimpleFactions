# Phase 6: Movement apply gate (batch plan)

**Phase:** [01-phases.md](./01-phases.md) Phase 6  
**Gameplay lock:** [00-index.md](./00-index.md)  
**Canonical:** [wars.md](../../wars.md)

One apply path for "caved in" and "won a movement war". Coup is a stub with documented order, not a full civil-war sequencer.

Do **not** implement civil wars, inter-vassal wars, or a second proposal engine in this phase.

## Exit (must all be true)

- Accepting movement demands and winning the matching war run the same cause order.
- No second proposal engine.

---

## Batch 1 - CanHaveLaw / Law.isAvailable

**Done.** `CanHaveLaw` evaluates YAML `requirements` (`has_law` / `not_law`, unknown verbs fail closed) and same-group `compatibility` `<= 0` as a hard block. `Law.isAvailable` delegates. Peace-time propose/apply in `GovernmentView` refuses unavailable laws; `LawCreator` still shows the row with a `§c` reason. `Faction.applyLaw` is not gated (forced war apply still works). No production `requirements` added to `laws.yml`.

---

## Batch 2 - MovementOutcomeService

**Done.** `MovementOutcomeService.apply(movement, ACCEPTED | WAR)`: `CHANGE_LEADER` first, then other causes via existing `Proposal.apply(cause)`, then `endMovement`. Stability: cave-in `"Caved to Movement"` + `getStabilityEffect()`; war Coup **-75** if a leader cause, else Civil War **-75** for law/tax. `MovementView` accept-demands uses this service (batch 4).

---

## Batch 3 - Coup stub

**Done.** `CoupService.apply(faction, wantedLeader)`: `canBecomeLeader` then `promoteToLeader`. Autocracy/community leave council; oligarchy `clearMembers`; plutocracy/democracy `applyLaw` oligarchy then `clearMembers`. Wired from `CHANGE_LEADER` in `applyPoliticalAction` only.

---

## Batch 4 - Migrate accept-demands

**Done.** `MovementView` accept-demands calls `MovementOutcomeService.apply(movement, ACCEPTED)` instead of inline stability, `proposal.apply(null)`, and `endMovement`. Decline is unchanged (civil war still TODO).

---

## Batch 5 - Movement-origin war goals

**Done.** Overthrow, change law, change tax exist as war goals (`overthrow` / `change_law` / `change_tax`). They are not in the declare picker. `WarGoalValidator` always fails them (`This war goal cannot be declared yet`) until Phase 7 civil wars. Attacker victory with a stored `movementId` calls `MovementOutcomeService.apply(movement, WAR)`. Missing movement is a no-op. Decline still does not start a war.

---

## Out of scope

- Civil wars (Phase 7)
- Inter-vassal wars (Phase 8)
- Full coup sequencer beyond the stub
- Gating `Faction.applyLaw` for war apply
