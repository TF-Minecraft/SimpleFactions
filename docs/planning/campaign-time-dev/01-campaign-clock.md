# Batch 01 — Campaign clock + duration parser

## Deliverables

### `War/campaign/runtime/CampaignClock.java`

```java
public final class CampaignClock {
    public static Instant now();
    public static Duration getOffset();
    public static boolean isSpoofed();
    public static void add(Duration delta);
    public static void reset();
    static void resetForTests();
}
```

- `now()` = `Instant.now().plus(offset)` (offset zero → real time).
- Volatile in-memory only (like `BattleDevMode`); lost on restart.
- **Do not** use for writing `war.startedAt` / `endedAt` / commitment timestamps.

### `War/campaign/runtime/CampaignDurationParser.java`

Parse admin command tokens into `Duration`:

| Input | Result |
|-------|--------|
| `1h 31m` | 1h + 31m |
| `1h31m` | same (concatenated) |
| `45m` | 45 minutes |
| `1d` | 24 hours |
| `90s` | 90 seconds |

- Regex per token: `(\d+)(s|m|h|d)`.
- Reject empty / unknown units with clear error string for command feedback.
- Do **not** use `TimeFormatter.StringTimeToMillis` (single unit only).

## Files

| Action | Path |
|--------|------|
| Add | `War/campaign/runtime/CampaignClock.java` |
| Add | `War/campaign/runtime/CampaignDurationParser.java` |

## Tests (batch 06, stub here)

- `CampaignClockTest` — add/reset/now offset
- `CampaignDurationParserTest` — compound durations, invalid input

## Acceptance

- [x] `CampaignClock.now()` testable with `resetForTests()`
- [x] Parser accepts `1h 31m` and `1h31m` equivalently
