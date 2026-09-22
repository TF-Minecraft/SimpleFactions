# Phase 3: Title wars (batch plan)

**Phase:** [01-phases.md](./01-phases.md) Phase 3  
**Gameplay lock:** [00-index.md](./00-index.md)  
**Canonical:** [wars.md](../../wars.md)

First controlled exception to "never same realm": **Usurp vs direct overlord only**. De jure stays external.

Do **not** implement pillage, forced law wars, or the movement gate in this phase.

## Exit (must all be true)

- Usurp vs independent and vs direct overlord both apply via `FactionManager.usurp`.
- De jure victory moves land and never grants an unowned title.
- Nested vassals still cannot declare on each other (that is Phase 8).

---

## Batch 1 - Usurp declare

**Done.** Player-facing `Usurp` in the declare picker when the defender has a primary title and the attacker rank is the same or lower. Direct overlord is allowed (same-realm exception). Other same-realm targets still blocked. Layer 2 none. Campaign populate uses defender provinces (same as subjugate). Apply remains a no-op until batch 2.

### Tests

- `canUsurpByRank`: attacker may not strictly outrank the defender.
- Direct overlord allowed; overlord vs subject still same-realm blocked.
- Independent target with title allowed; missing title and higher attacker rank rejected.

---

## Batch 2 - Usurp apply

**Done.** Attacker victory calls `FactionManager.usurp(null, attacker, defender)`. `usurp` sets the loser as `subject` via `setRelationForced`. Title, subjects, and former overlord slot stay in the existing engine. Defender victory / white peace / admin end do not usurp.

---

## Batch 3 - De jure eligibility rewrite

**Done.** Attacker must own the title, or the title is unowned and the attacker owns a province in it. Incoming land must be in the defender's realm. Rank, settlements/capitals, and prestige headroom apply. Layer 2 lists candidates including blocked rows with reasons ("use subjugate instead" only when subjugate vs the defender would validate). Apply still no-op until batch 4.

---

## Batch 4 - De jure apply

**Done.** Provinces in the title owned by the **defender's realm** transfer to the attacker. **Unowned title is not granted.** Forming titles stays the existing title menu.

---

## Out of scope

- Pillage, open market, change government
- Movement apply gate, civil wars, inter-vassal wars
- NAP as a real declare block
