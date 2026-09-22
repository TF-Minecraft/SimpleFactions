# Phase 2: Relation wars (batch plan)

**Phase:** [01-phases.md](./01-phases.md) Phase 2  
**Gameplay lock:** [00-index.md](./00-index.md)  
**Canonical:** [wars.md](../../wars.md)

Independent (or tributary) targets. Campaign shape unchanged. Apply goes through Phase 1 forced diplomacy / `transferSubject`.

Do **not** implement usurp, de jure rewrite, pillage, or the movement gate.

## Exit (must all be true)

- Four diplomatic outcomes work end-to-end (declare GUI → campaign → attacker win apply): tributary, subjugate (chosen type), transfer subject. (Generic **War** stays no-op.)
- Shared declare blocks still stop ally / same-realm / already-at-war. NAP remains stub (always allowed).
- Subjugate types come from YAML (`vassal: true` and `can-pick-for-war` is not `false`). Integrated is not pickable. March / palatinate use existing `limit`.

---

## Batch 1 - YAML picker rules

**Done.** `can-pick-for-war` on relation types (default true). `integrated_subject` is `false`. [`RelationLoader.getWarPickableVassalTypes`](../../../src/main/java/me/Plugins/SimpleFactions/Loaders/RelationLoader.java) returns vassal types that may appear in the subjugate picker. March / palatinate keep existing `limit`. Peace-time diplomacy GUI unchanged. Subjugate picker is batch 3.

### Tests

- Missing YAML key → `canPickForWar()` true.
- `can-pick-for-war: false` → false.
- Query includes subject / mercantile / march / palatinate; excludes integrated, ally, tributary.

---

## Batch 2 - Tributary

**Done.** Player-facing `Tributary` in the declare picker (War, Tributary, Subjugate, then conditional de jure / transfer). Shared rules still block declaring on your own tributary. Apply: `setRelationForced` tributary type (suzerain reverse via existing link). Not vassalage. Missing diplomacy type: declare fails, apply no-ops.

---

## Batch 3 - Subjugate

**Done.** Type picker from `getWarPickableVassalTypes`. Chosen `relationTypeId` persists on the war. Tributary of attacker allowed. Apply chosen type via `setRelationForced`. `atLimit` for march / palatinate. Integrated is not pickable.

---

## Batch 4 - Transfer subject

**Done.** Layer 2 is any faction on the defender overlord path (direct or nested). War defender stays the clicked liege. Declare checks `atLimit` on the subject's current vassal type. Apply: `transferSubject` with `setRelationForced`.

---

## Out of scope

- Usurp, de jure apply rewrite
- `Revolt`, NAP real relation
- Movement apply gate, pillage
- Declare-code goal whitelist
