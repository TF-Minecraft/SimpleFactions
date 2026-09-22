# Phase 8: Inter-vassal wars (batch plan)

**Lock:** [00-index.md](./00-index.md)  
**Phase:** [war-goals-apply/01-phases.md](../war-goals-apply/01-phases.md) Phase 8  
**Canonical:** [wars.md](../../wars.md)

Do not start a later batch until the current batch's tests pass. Do not implement from [01-phases.md](../war-goals-apply/01-phases.md).

## Exit (must all be true)

- Peer/cousin vassals under the same king can declare (clicked defender, not the king). Vertical liege/vassal still blocked except usurp vs direct overlord.
- CTA eligibility matches the lock for **all** wars; `canBeCalled` is `!isParticipating` plus those rules.
- Internal campaign can path through non-belligerent liege land; that land is not occupied.
- Subjugate vs a peer applies via `transferSubject` (nested under the king). Civil-war start is unused.
- `mvn test -Dtest="me.Plugins.SimpleFactions.War.**"` passes.

---

## Batch 1 - Queries

**Done.** `InterVassalQueries` in `War/declare`: `topLiegeId`, `sharesTopLiege`, `isInternalPeer`, `isNestedUnder`, `isOverlordOfMain`. Wraps `RelationManager`; `sameRealm` stays vertical. Tests: siblings, cousin count vs other duke, vassal vs liege, independents, king is overlord of mains.

---

## Batch 2 - Declare

**Done.** `WarGoalValidator`: `sameRealm` still blocks vertical except usurp vs direct overlord; `isInternalPeer` skipped on that block. Internal peer rejects Usurp (`Usurp can only target your direct overlord.`). Subjugate allows a peer who already has the king as overlord. `WarDeclareHelper.canDeclareUsurp` false for peers. No defender retarget. Tests: sibling War/subjugate, duke vs king War, usurp vs peer vs king.

---

## Batch 3 - Call to arms (all wars)

**Done.** `CallToArmsEligibility` + `War.canBeCalled(caller, target)`. Participating, overlord-of-main, nested-under-enemy, and top-liege-is-main deny before the ally snapshot. `WarManager.sendRequest` uses `Result.message()`. `WarView` passes the caller. Tests: sibling ally, king, enemy count, foreign vassal, liege-as-main, already participating.

---

## Batch 4 - Pathfinder liege transit

**Done.** Internal wars: non-belligerent land that is the top liege or shares that top liege is liege transit (`BelligerentTerritory.isLiegeTransit`). Land pass treats it like wilderness (`!isForeignNation`); campaign route paints it gray (`isNeutral`). King/sibling ids stay out of attacker/defender sets. External third-nation land still blocks.

---

## Batch 5 - Occupation + subjugate apply

**Done.** Liege transit stays out of `occupied_by_*` (enemy-owned filter; king-adjacent battle regression). Internal subjugate: snapshot `internalWar` + `internalTopLiegeId` at construct/persist; apply uses `transferSubject` then chosen type if it differs. External subjugate unchanged.

---

## Batch 6 - Docs + verify

**Done.** [wars.md](../../wars.md) Participants, pathfinder, and open items. [roadmap.md](../../roadmap.md) Inter-vassal wars under Shipped; next is Phase 9. In-game check: declare vs sibling, CTA a sibling ally, path through king land, occupy only the enemy duchy.

---

## Out of scope

- NAP, tickets, chronicle, occupation overlay, council peace
- External declare retarget to top liege
- Civil-war temp rebels
- Changing levy/fighter rules for called allies' subjects
