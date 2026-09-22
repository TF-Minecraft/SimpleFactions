# Batch 05: Tests

**Status:** done

## Deliverable

Unit coverage for the prestige fix, rank persistence mapping and the snapshot builder. JUnit 5 and Mockito are already on the `pom` (`junit-jupiter` 5.10.2, `mockito-core` 5.11.0, `mockito-inline` 5.2.0).

## `PrestigeBreakdownTest`

The reason batch 01 extracts a pure assembler: these run with no Bukkit at all.

| Test | Assertion |
|------|-----------|
| `build_isIdempotent` | Ten successive builds from the same inputs give an identical total |
| `build_bonusIncludesSubjects` | With subjects 100 and bonus 10%, the bonus line covers the subject contribution |
| `build_bonusDoesNotCompound` | Feeding a prior result back in does not grow the bonus |
| `build_bonusPercentChange_singleLine` | Moving 10% to 20% leaves exactly one `% Bonus` line |
| `build_bonusRemoved_noLine` | Dropping to 0% leaves no bonus line |
| `build_retainsPersistent` | Persistent modifiers survive and are inside the bonus base |
| `build_zeroSuppression` | No `Provinces`, `Titles` or `Subjects` line when those are zero, but `Wealth` keeps its explicit zero |

`build_isIdempotent` is the regression guard for the reported behaviour. Without it the bug reappears the moment someone reintroduces list-based accumulation.

## `FactionDataRankTest`

Round-trip `rank` and `founded at` through a `FactionData` instance and Gson. Assert an unknown rank id resolves to `RankLoader.getLowest()` and that an absent `rank` key does the same. `Database` itself needs a live Bukkit world for the guild bank chunk lookup, so test the DTO mapping rather than the file path.

## `ChronicleSnapshotTest`

Build a snapshot from mocked `Faction` and `Guild` objects.

| Test | Assertion |
|------|-----------|
| `snapshot_hasRequiredKeys` | `captured_at`, `factions`, `complete`, `schema_version` present |
| `snapshot_breakdownsAreMaps` | `wealth_breakdown` and `prestige_breakdown` are objects keyed by modifier type |
| `snapshot_rankThresholds` | `rank_down_at` is `rank_up_at` for the current level times 0.95 |
| `snapshot_globalsNotPreSummed` | `faction_wealth`, `pouch_wealth` and `player_bank_wealth` are separate keys |
| `snapshot_eventsEmpty` | `events` is present and empty |

Assert against the built `JsonObject`, not the file, so the test stays off disk.

## `RestServerValidationTest`

The `chronicle` guard rejects a JSON array, an object with no `captured_at`, and an object whose `factions` is not an array. If the validation block cannot be reached without the static `GatewayClient` call, extract the per-mode checks into a package-private static `validate(String mode, JsonElement payload)` and test that.

## Not covered

`MapSystem.tick()` branching needs the Bukkit scheduler, so batch 04 is verified in game per its own checklist rather than by unit test.

## Run

```bash
cd simplefactions && mvn test
```

## Next

Batch 06: docs and the ProvinceSystem handoff.
