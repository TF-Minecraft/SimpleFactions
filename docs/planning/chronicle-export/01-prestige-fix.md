# Batch 01: Prestige fix

**Status:** done

## Deliverable

`Objects/PrestigeBreakdown.java` (pure assembly + summation) and a rewritten [`Faction.updatePrestige()`](../../../src/main/java/me/Plugins/SimpleFactions/Objects/Faction.java) that is idempotent and applies the bonus to subject prestige.

## The two defects

Current code at `Faction.java:734-754`:

```java
if(getModifier(FactionModifiers.PRESTIGE_BONUS).getAmount() > 0.0) {
    double multiplier = getModifier(FactionModifiers.PRESTIGE_BONUS).getAmount()/100.0;
    double extra = 0.0;
    for(Modifier p : prestigeModifiers) {   // <- includes the PREVIOUS bonus line
        extra += p.getAmount();
    }
    extra = Formatter.formatDouble(extra*multiplier);
    addPrestigeModifier(new Modifier(getModifier(...).getAmount()+"% Bonus", extra, false));
}
```

**Self-inflation.** `addPrestigeModifier` replaces in place, but the base is recomputed over a list that already contains the last bonus. The recurrence `B(n+1) = m * (Base + Subjects + B(n))` converges to `m * (Base + Subjects) / (1 - m)`, so a 10% bonus settles at ~11.1%. Because `Faction.updateWealth()` calls `FactionManager.updateAllPrestige()` on every bank mutation server-wide, it converges within minutes of uptime and then looks stable, which is why it reads as "weird prestige behaviour" rather than an obvious bug.

**Stale lines.** The type string is `"<pct>% Bonus"`. `addPrestigeModifier` matches on exact type, so moving 10% to 20% adds a second line and keeps the first. Dropping to 0% skips the block entirely and orphans the line permanently. Both survive until restart (bonus lines are non-persistent so they are never saved).

## Fix

Rebuild the computed section from scratch on every call. Persistent modifiers are carried over, everything else is discarded and recomputed, which removes both defects structurally rather than patching the arithmetic.

New order, with `Subjects` moved ahead of the bonus:

1. Retain persistent modifiers
2. `Members` - `pow(memberCount + 4, 1.8) + 5`
3. `Wealth` - `(wealth / globalWealth) * maxWealthPrestige`, capped at `wealth`
4. `Provinces` - `provincePrestige * provinceCount`
5. `Titles` - highest title tier prestige
6. `Subjects` - `sum(subject.getPrestige() * givePercent / 100)`
7. `<pct>% Bonus` - `sum(all above) * pct / 100`
8. `prestige = sum(all)`

**Gameplay change:** step 6 now precedes step 7, so `PRESTIGE_BONUS` covers subject prestige where it previously did not. Confirmed as wanted for this season - the map is larger and land costs more prestige, so a palatinate overlord getting a real cut of its subjects' standing is the intended direction. Only relations with `prestige_bonus()` are affected, currently just `palatinate` at `diplomacy.yml:80`.

## API

`Objects/PrestigeBreakdown.java`:

| Method | Purpose |
|--------|---------|
| `build(List<Modifier> persistent, double members, double wealth, double provinces, double titles, double subjects, double bonusPercent)` | Assemble the full ordered modifier list |
| `total(List<Modifier> modifiers)` | Sum, formatted via `Formatter.formatDouble` |

Pure and Bukkit-free so batch 05 can test it directly. `Faction.updatePrestige()` gathers the inputs (which need managers and loaders) and delegates assembly and summation.

`Faction.addPrestigeModifier` becomes unused by the compute path and should be removed unless something else calls it. `addPersistentPrestigeModifier` stays as is - it is the admin entry point and its accumulate-then-drop-on-zero behaviour is correct.

## Zero and skip semantics

Preserve the existing suppression rules so the GUI breakdown does not gain empty rows: `Provinces` only when the count is above zero, `Titles` only when the faction holds one, `Subjects` only when the sum is positive, `Bonus` only when the percentage is positive. `Wealth` keeps its explicit zero line (`Faction.java:717-718`) because the GUI relies on it.

## Overlord recursion

Leave the tail recursion at `Faction.java:773-776` in place. It stays necessary because `Subjects` reads `subject.getPrestige()`, and batch 02's convergence loop handles the remaining ordering across the faction graph.

## Verify

Call `updatePrestige()` ten times on a faction with a palatinate subject and assert a constant total. Flip the bonus percentage and assert exactly one `% Bonus` line survives. Drop the bonus to zero and assert no bonus line remains.

## Next

Batch 02: persist `PrestigeRank` and `founded_at`, suppress recompute during load, add the convergence loop.
