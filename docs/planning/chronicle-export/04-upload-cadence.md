# Batch 04: Upload cadence

**Status:** done

## Deliverable

A restructured [`MapSystem.tick()`](../../../src/main/java/me/Plugins/SimpleFactions/Map/MapSystem.java) that uploads trade and chronicle data every 300s regardless of map activity, and a `trade` regen type for the quiet path.

## Current behaviour

```java
lastUpdate++;
fullUpdate++;
if(lastUpdate > 300 && !queues.isEmpty()) {
    updateMap();
} else if(fullUpdate > 3600) {
    queueAllNations();
}
```

Two problems. The 300s upload requires a non-empty queue, so a server with no claims, no diplomacy and no war uploads nothing at all. And because the hourly branch is an `else if`, a non-empty queue on the tick where `fullUpdate` crosses 3600 skips the full cycle entirely while the counter keeps climbing, so even the hourly floor is not guaranteed.

Trade and prosperity shift continuously from province data with no map queue involvement, so the same gap already suppresses trade map freshness today.

## New tick

```java
public void tick() {
    if (!Cache.provincesEnabled) return;
    lastUpdate++;
    fullUpdate++;
    if (fullUpdate > 3600) {
        queueAllNations();
        return;
    }
    if (lastUpdate > 300) {
        if (!queues.isEmpty()) {
            updateMap();
        } else {
            updateLiveData();
        }
    }
}
```

The hourly cycle is now checked first and unconditionally, so it can no longer be starved. `queueAllNations()` already resets `fullUpdate` and calls `updateMap()`, which resets `lastUpdate`.

## Payload split

`prepareUploadFiles()` splits in two so the quiet path can ship the cheap, always-changing data without regenerating nation geometry:

| Method | Exports |
|--------|---------|
| `prepareLiveFiles()` | `province_data`, `guilds`, `chronicle` |
| `prepareMapFiles()` | `map_markers`, `nation` |
| `prepareUploadFiles()` | both, retained for `fullRegen()` and `uploadAll()` |

`updateLiveData()` is the quiet path:

```java
public void updateLiveData() {
    lastUpdate = 0;
    prepareLiveFiles();
    SimpleFactions plugin = SimpleFactions.getInstance();
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        uploadLiveFiles();
        RestServer.commenceRegen("trade");
    });
}
```

`updateMap()` keeps its existing shape and gains the chronicle through `prepareLiveFiles()`, so the snapshot lands every 300s on both paths. It continues to save every faction to disk first, because `exportAllFactionsToNationJson` reads `Data/*.json` rather than live objects. The quiet path deliberately skips that save: nothing on it reads the save files.

## Trade regen

`commenceRegen("trade")` is a new regen type on ProvinceSystem that redraws trade and prosperity overlays without touching nation borders or title geometry. This is our own PS task, separate from the chronicle work handed to the other dev.

Until that endpoint exists the call returns a failure that `RestServer.commenceRegen` logs and swallows, so shipping the SF side first is safe. The uploads still land and the chronicle is unaffected.

## Cost

The quiet path writes three files and posts three payloads every 5 minutes. Compared to `updateMap()`, which serialises every faction to disk and re-reads the whole `Data/` folder, it is far cheaper than what already runs on an active server.

## Verify

On a test server with no map activity, confirm `chronicle.json` and `province_data.json` upload every 300s and that `nation` and `map_markers` do not. Make one claim and confirm the next tick takes the queued path with the full payload set and `commenceRegen("queued")`. Let it idle an hour and confirm the full nation cycle still fires.

## Next

Batch 05: tests.
