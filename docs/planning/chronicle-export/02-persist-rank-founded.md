# Batch 02: Rank and founding persistence

**Status:** done

## Deliverable

`FactionData.rank` and `FactionData.foundedAt` round-tripping through [`Database`](../../../src/main/java/me/Plugins/SimpleFactions/Database/Database.java), a `FactionManager.loading` guard, and a converging `updateAllPrestige()`.

## Why rank must be stored

`PrestigeRank` is derived state that never reaches disk. Every `Faction` constructor sets `this.rank = RankLoader.getLowest()` (`Faction.java:137`, `:169`, `:225`), and rank only moves one level per `updatePrestige()`:

```java
if(this.rank.getLevel() < RankLoader.getRanks().size()) {
    Double rankUpAmount = FactionManager.getRankUpAmount(RankLoader.getByLevel(this.rank.getLevel()+1));
    if(prestige >= rankUpAmount) { this.rank = RankLoader.getByLevel(this.rank.getLevel()+1); }
}
```

So a Legendary faction needs four passes to climb back after a restart. Worse, `getRankUpAmount` is competitive: it takes the top prestige among factions **currently holding** the target rank and only falls back to `ranks.yml` `minimum-prestige` when nobody does. Immediately after load nobody holds anything above level 1, so the whole ladder re-derives against the static minimums and can land somewhere different from where it was.

## Changes

### `Database/FactionData.java`

```java
public String rank;

@SerializedName("founded at")
public Long foundedAt;
```

### `Database.saveFaction` (near the modifier block at `:321`)

```java
data.rank = f.getRank() != null ? f.getRank().getId() : null;
data.foundedAt = f.getFoundedAt();
```

### `Database.loadFaction` (after the `Faction` constructor at `:121-144`)

```java
if (data.rank != null) {
    PrestigeRank r = RankLoader.getByString(data.rank);
    if (r != null) f.setRank(r);
}
if (data.foundedAt != null) f.setFoundedAt(data.foundedAt);
```

`setRank` already exists at `Faction.java:497`. Omitted or unknown rank leaves the constructor default of `RankLoader.getLowest()`, which is the obscure faction, so no legacy handling is needed. There are no live factions this season, but the fallback keeps a hand-edited or truncated save file from crashing the load.

### `Faction` founding stamp

Add `private long foundedAt` with a getter and setter. Both creating constructors (`Faction(String, String)` and `Faction(Guild)`) stamp `System.currentTimeMillis() / 1000`. The load constructor leaves it at zero until `setFoundedAt` runs.

This exists purely for the chronicle. Faction ids come from `Formatter.formatId(name)` (`Faction.java:131`), not UUIDs, and `deleteFaction` removes the save file, so a new faction founded under a recycled name gets a byte-identical id. `founded_at` is what lets ProvinceSystem tell a reincarnation apart from a continuous nation instead of splicing two unrelated histories into one line. `Faction.setId` is never called anywhere, so renames are not a concern.

## Load suppression

`Database.loadFaction` calls `g.updateWealth()` per guild (`:214`) and `f.updateWealth()` per faction (`:249`), and `Faction.updateWealth()` ends with `FactionManager.updateAllPrestige()`. Loading n factions therefore triggers n full-server prestige recomputes, each recursing up overlord chains, for O(n squared) work before the server is even up. With rank now restored, those mid-load passes are also computed against a partial faction set and a partial global wealth, and the rank-down branch can demote a faction on the spot.

Add a static flag:

```java
public static boolean loading = false;

public static void updateAllPrestige() {
    if (loading) return;
    for (Faction f : factions) f.updatePrestige();
}
```

`Database.loadFactions` sets it true before the loop and false in a `finally`. `FactionManager.run()` already calls `f.updatePrestige()` for every faction after relations load (`:497`); replace that with the convergence call below.

## Convergence

Rank climbs one level per pass and the thresholds shift as factions occupy ranks, so one pass is not enough after a cold start.

```java
public static void updateAllPrestigeConverged() {
    int maxPasses = Math.max(1, RankLoader.getRanks().size());
    for (int i = 0; i < maxPasses; i++) {
        Map<Faction, PrestigeRank> before = snapshotRanks();
        updateAllPrestige();
        if (ranksUnchanged(before)) return;
    }
}
```

Called from `FactionManager.run()` after load and from `ChronicleExport` before each snapshot (batch 03). Capped at the rank count (5) so it cannot spin.

**Depends on batch 01.** Looping `updateAllPrestige()` against the current non-idempotent `updatePrestige()` would actively inflate every bonused faction, so the fix has to land first.

## Verify

Restart with a Legendary faction on disk and confirm it is Legendary on the first converged pass rather than climbing over the following minutes. Confirm load time does not regress on a populated `Data/` folder.

## Next

Batch 03: the chronicle payload and exporter.
