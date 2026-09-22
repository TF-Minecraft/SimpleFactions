# War goals: phase plan

**Gameplay lock:** [00-index.md](./00-index.md)  
**Canonical spec:** [wars.md](../../wars.md)  
**Status:** Phases 0-8 done. Civil wars: [naval-installations/02-phase-2.md](../naval-installations/02-phase-2.md) (Done). Inter-vassal: [inter-vassal-wars/00-index.md](../inter-vassal-wars/00-index.md) (Done). Phase 9 is later.

This is the **implementation sequence**. Batches inside a phase can ship as separate PRs. Do not start the next phase until the current phase's exit criteria are met. Do not add a second diplomacy, law, tax, or government engine.

---

## Why this order

1. **Spine before goals.** Forced `setRelation` and a single war-end applicator must exist before any goal writes politics. Otherwise every goal invents its own end hook.
2. **External wars before same-realm cracks.** Tributary / subjugate / transfer only need independent (or tributary) targets. Usurp and later civil/inter-vassal work punch holes in `sameRealm`. Finish the simple declare surface first.
3. **Call existing engines before rewriting GUIs.** Relation apply and `FactionManager.usurp` are thin. De jure eligibility is a rewrite. Open market / change government are `applyLaw` plus YAML. Pillage is a different campaign shape. Movement goals need a shared apply gate that peace-time cave-in also uses.
4. **Law-on-target wars do not wait for movements.** Open market and change government are player-declared wars. Overthrow / change law / change tax are movement-origin. Split them so the movement gate is not on the critical path for diplomatic and law wars.
5. **Generic `War` is a no-op on the spine.** Players can pick it to run a campaign without an automatic political outcome. `Revolt` waits for civil wars.
6. **Civil wars, inter-vassal wars, and full movements** share realm/relation problems. Civil wars shipped as Phase 7. Inter-vassal shipped as Phase 8.

```mermaid
flowchart LR
  p0[Phase 0 Navy]
  p1[Phase 1 Spine]
  p2[Phase 2 Relations]
  p3[Phase 3 Titles]
  p4[Phase 4 Forced law]
  p5[Phase 5 Pillage]
  p6[Phase 6 Movement gate]
  p7[Phase 7 Civil wars]
  p8[Phase 8 Inter-vassal]
  p9[Phase 9 Leftovers]
  p0 --> p1 --> p2 --> p3
  p1 --> p4
  p1 --> p5
  p4 --> p6
  p3 --> p7
  p6 --> p7
  p7 --> p8
  p8 --> p9
```

Phases 4 and 5 can run after Phase 1 without waiting for titles. Prefer **2 then 3 then 4 then 5** in one line of work so declare GUI and apply stay one story. Only parallelize if two people are coding.

---

## Phase 0 - Navy gate

**Done.** Declare reject + Push/Hold/deadline when the next slot is naval and the relevant war leader has no operational port. `hasSeaConnection` exists for later pillage.

**Exit:** already shipped. See 00-index Navy section.

---

## Phase 1 - Apply spine

Make war end do one thing, and make diplomacy settable without a player clicking Accept. No goal outcomes yet except no-ops and reparations.

**Batch plan:** [02-phase-1.md](./02-phase-1.md)

### Batches

1. **Forced `setRelation`.** Boolean on the existing setter (default `false`). Forced skips opinion and mutual request; still runs loop, top-overlord, `vassalCheck`, `atLimit`, map, linked reverse. Wrapper `setRelationForced`. Tests: forced vs unforced.
2. **Declare must not start a civil war by accident.** Today `declareWar` calls `endVassalage`. Stop that. Same-realm wars are Phase 7/8. Usurp's overlord exception must not dissolve the realm at declare time.
3. **End applicator shell.** `WarManager.endWar` (or a dedicated service it calls) by `WarEndReason`:
   - `ATTACKER_VICTORY` → goal dispatcher (empty / no-op per type until later phases)
   - `DEFENDER_VICTORY` → reparations from attacker (ledger cashflows already exist)
   - `WHITE_PEACE` / `ADMIN_END` → neither
4. **Generic goal `War`.** Shown in the player declare picker. Shared rules, including vs tributaries. Apply: none.

**Done.** Forced `setRelation`, declare no longer dissolves realms, end applicator + reparations, pickable **War** with no apply.

### Exit

- Winning a war with an unimplemented goal does not throw and does not apply politics.
- Defender win applies reparations only.
- Forced relation tests pass; unforced diplomacy unchanged.
- Declaring war never calls `endVassalage`.

---

## Phase 2 - Relation wars

Independent (or tributary) targets. Campaign shape unchanged. All apply goes through Phase 1 forced diplomacy / `transferSubject`.

**Batch plan:** [03-phase-2.md](./03-phase-2.md)

### Batches

1. **YAML picker rules.** `can-pick-for-war` on relation types. Integrated subject not pickable. March / palatinate still use existing `limit`.
2. **Tributary.** Declare checks + apply `setRelationForced` tributary/suzerain types. Not a vassal relation.
3. **Subjugate.** Type picker (Subject, Mercantile, March, Palatinate). Tributary of attacker allowed. Apply chosen type forced.
4. **Transfer subject.** Layer 2: any faction in defender nested realm. War defender stays top liege. Apply `RelationManager.transferSubject` (keep type).

**Done.** Tributary, subjugate (chosen type), and transfer subject declare and apply. Generic **War** stays no-op.

### Exit

- Four diplomatic outcomes work end-to-end (declare GUI → campaign → attacker win apply).
- Shared declare blocks still stop ally / same-realm / already-at-war. NAP remains stub (always allowed).

---

## Phase 3 - Title wars

First controlled exception to "never same realm": **Usurp vs direct overlord only**. De jure stays external (you do not take the title from your liege this way).

**Batch plan:** [04-phase-3.md](./04-phase-3.md)

### Batches

1. **Usurp declare.** **Done.** Rank same or lower than target. Direct overlord allowed; other same-realm still blocked. Layer 2 none (`getHighestTitle()`). Apply is still no-op until batch 2.
2. **Usurp apply.** **Done.** `FactionManager.usurp` only (title, subjects, target becomes subject, overlord slot). War path uses forced / no-player usurp. Do not reimplement.
3. **De jure eligibility rewrite.** **Done.** Own the title, **or** unowned title and attacker owns at least one province in it. No settlements (or faction capitals) in the area. Prestige headroom. Rank gate. Layer 2 lists blocked titles with reasons (including "use subjugate instead" only when that declare would be valid).
4. **De jure apply.** **Done.** Provinces in the title owned by the **defender's realm** transfer to the attacker. **Unowned title is not granted.** Forming titles stays the existing title menu.

### Exit

- Usurp vs independent and vs direct overlord both apply via `usurp`.
- De jure victory moves land and never grants an unowned title.
- Nested vassals still cannot declare on each other (that is Phase 8).

---

## Phase 4 - Forced law wars (no movement)

Player-declared wars that only change laws on the defender. Stability modifiers as locked. No `CanHaveLaw` project required yet if these laws already exist and are selectable.

**Batch plan:** [05-phase-4.md](./05-phase-4.md)

### Batches

1. **War-goal YAML law ids** for open market (`defender_must_not_have`, `attacker_must_not_have`, `apply_defender_law`). **Done.** Ids not hardcoded in Java.
2. **Open market.** **Done.** Declare checks + apply `applyLaw` + **Forced Market Open -25%** decaying.
3. **Change government.** **Done.** Layer 2: government ± leadership, pre-filled with target's current laws. Apply changed groups + **Forced Government Change -50%** decaying.

### Exit

- These two goals declare and apply without a movement.
- Overthrow / change law / change tax still cannot be picked as a normal declare (movement-only).

---

## Phase 5 - Pillage

Different war type / campaign: one settlement, one battle, then apply and end. Uses `hasSeaConnection` from Phase 0. Distinct from campaign raids.

**Batch plan:** [06-phase-5.md](./06-phase-5.md)

### Batches

1. **Range queries.** **Done.** Settlement within X of attacker land borders, **or** within X of sea **and** `hasSeaConnection`. X is YAML `range_provinces` (default 3).
2. **Declare + populate.** **Done.** Picker is a settlement. One battle at the settlement, empty counter. Navy gate still applies if the **natural** path has a naval slot. Ids `pillage`, not `raid`.
3. **Apply.** **Done.** Snapshot 10 days of trade income for guilds with capital in that settlement. Attacker paid that gold. Those guilds get **-100% trade income** decaying over 10 days. Query existing ledgers.

### Exit

- Pillage wars cannot be used as a substitute for campaign raids.
- Airborne / inland-deep pillage stays out of scope.

---

## Phase 6 - Movement apply gate

One apply path for "caved in" and "won a movement war". Coup is a stub with documented order, not a full civil-war sequencer.

**Batch plan:** [07-phase-6.md](./07-phase-6.md)

### Batches

1. **`CanHaveLaw` / `Law.isAvailable`.** **Done.** Requirements + compatibility for change-law. Needed before that goal is honest.
2. **`MovementOutcomeService.apply(movement, ACCEPTED | WAR)`.** **Done.** Cause order: coup / leader first, then other causes. Stability name and size from source. Then apply causes, then end movement.
3. **Coup stub.** **Done.** Always change leader (wanted leader must pass `canBecomeLeader`). Council: autocracy or community unchanged; oligarchy emptied; plutocracy/democracy switch to oligarchy and empty council. Called from the gate, not copied in war code.
4. **Migrate accept-demands.** **Done.** `MovementView` uses the service (`ACCEPTED`) instead of ad hoc `proposal.apply`.
5. **Movement-origin war goals.** **Done.** Overthrow, change law, change tax exist for apply only. Declare is blocked until Phase 7. Attacker victory calls the gate with source `WAR`. Stability: coup **-75%** vs Civil War **-75%** as locked.

### Exit

- Accepting movement demands and winning the matching war run the same cause order.
- No second proposal engine.

---

## Phase 7 - Civil wars

**Done.** **Lock:** [naval-installations/02-phase-2.md](../naval-installations/02-phase-2.md). Do not implement from this file.

Dedicated start/teardown (temp rebels, snapshots, restore then Phase 6 gate). Civil-war defender win: **no** auto reparations and **no** auto imprison. Staff `/war admin reparations <from> <to>` for manual terms. `Revolt` stays a staff/no-apply label if needed for tests.

---

## Phase 8 - Inter-vassal wars

**Done.** **Lock:** [inter-vassal-wars/00-index.md](../inter-vassal-wars/00-index.md). Batches: [01-batches.md](../inter-vassal-wars/01-batches.md). Do not implement from this file.

Peer/cousin vassals under the same top liege. Liege is not a participant. CTA rules apply to **all** wars. Pathfinder: liege land is transit. `sameRealm` stays vertical-only.

---

## Phase 9 - Leftovers

NAP and occupation overlay **shipped**. Airborne pillage is **not a feature**. Chronicle is owned elsewhere. Remaining war-adjacent work is on [roadmap.md](../../roadmap.md) (diplomacy, companies, declare codes last), not a war-goal rewrite.

---

## Explicitly not in Phases 1-6

| Item | When |
|------|------|
| Ally / NAP as implemented diplomacy | NAP: Phase 9. Allies already block declare. |
| Inter-vassal wars | Phase 8 (done) |
| Temporary rebels, relation snapshots | Phase 7 (done) |
| Full coup sequencer | Phase 7 (stub is Phase 6) |
| `endVassalage` as a declare side effect | Never; Phase 1 removes it |
| Legacy YAML goals as a second runtime | Never |
| Campaign raid changes | Never in this program |

---

## Suggested coding rhythm

Ship Phase 1 as soon as possible: every later PR is "fill in this `WarGoalType` arm" plus GUI. After that, one phase at a time on `main` (or stacked PRs), each with tests at the engine boundary (`setRelationForced`, `usurp`, `applyLaw`, ledger, movement service) rather than only GUI clicks.
