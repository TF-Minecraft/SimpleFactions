# Phase 4: Forced law wars (batch plan)

**Phase:** [01-phases.md](./01-phases.md) Phase 4  
**Gameplay lock:** [00-index.md](./00-index.md)  
**Canonical:** [wars.md](../../wars.md)

Player-declared wars that only change laws on the defender. Stability modifiers as locked. No `CanHaveLaw` project required yet if these laws already exist and are selectable.

Do **not** implement pillage or the movement apply gate in this phase.

## Exit (must all be true)

- Open market and change government declare and apply without a movement.
- Overthrow / change law / change tax still cannot be picked as a normal declare (movement-only).

---

## Batch 1 - War-goal YAML law ids

**Done.** Open-market law ids live on `war.goals.OPEN_MARKET` (`defender_must_not_have`, `attacker_must_not_have`, `apply_defender_law`). Loaded into Cache as strings. Not hardcoded in Java. Declare and apply remain batch 2.

---

## Batch 2 - Open market

**Done.** Player-facing `Open Market` with no layer 2. Declare checks Cache law ids. Attacker victory calls `applyLaw` on the defender and adds **Forced Market Open -25%** decaying. White peace / admin end do not apply.

---

## Batch 3 - Change government

**Done.** Layer 2: government ± leadership, pre-filled with the defender's current laws. May change one axis. Attacker victory applies `applyLaw` on changed groups and adds **Forced Government Change -50%** decaying.

---

## Out of scope

- Pillage, movement apply gate, civil wars
- `CanHaveLaw` / `Law.isAvailable` rewrite
