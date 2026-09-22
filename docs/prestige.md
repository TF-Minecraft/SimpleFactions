# Prestige

**Prestige** is a faction's standing. It gates rank, province cap, de jure annexation headroom and diplomatic capacity, and it is shown as a breakdown so a leader can see where the number comes from.

Two pieces exist per faction: the cached scalar `Faction.prestige`, and the `List<Modifier>` breakdown that produced it. Both are rebuilt together by `Faction.updatePrestige()`.

**Assembly:** `Objects/PrestigeBreakdown.java` - **Input gathering:** `Faction.updatePrestige()` - **Playtime / trade curves:** `prestige/`

---

## The terms

Rebuilt from scratch on every recompute, in this order. The order matters because the bonus multiplies only the lines above it.

| Line | Formula |
|------|---------|
| Persistent modifiers | Carried over untouched. Admin grants via `/faction addprestigemodifier`, war outcomes, and anything else flagged persistent |
| **Members** | `pow(memberCount + 4, 1.8) + 5`, **plus** the playtime sum below |
| **Wealth** | `(wealth / globalWealth) * max-prestige-from-wealth`, capped at the faction's own wealth |
| **Trade** | Linear `tradePower * prestige-per-trade-power` up to `prestige-from-trade-soft-cap` (default 2000). Past that the marginal rate decays toward `prestige-from-trade-falloff` (default 0.1) over each further cap-sized band, so it never hard-caps. Own guilds only; overlords still get a cut of subject prestige via **Subjects** |
| **Provinces** | `province tier prestige * provinceCount` |
| **Titles** | Prestige of the highest title tier held |
| **Subjects** | `sum(subject.getPrestige() * givePercent / 100)` over vassals |
| **`<pct>% Bonus`** | `sum(everything above) * pct / 100` |

`prestige` is the sum of all lines. Rebuilding is idempotent: a recompute never sees its own previous bonus line, which is what the old code got wrong.

## The Members term

Headcount alone rewards mass recruitment, so **Members is two halves added together**: the headcount curve, plus what each member's time on the server is worth.

```
Members = pow(memberCount + 4, 1.8) + 5  +  sum over members of 2^(online hours / 100)
```

The per-member half doubles every 100 online hours and flattens at `max-prestige-playtime-exponent`:

| Online hours | Per member |
|--------------|-----------|
| 0 | 1 |
| 100 | 2 |
| 200 | 4 |
| 300 | 8 |
| 400 | 16 |
| 500 and up | 32 |

Both halves land in the single `Members` line rather than a separate line, so the GUI and the chronicle export keys are unchanged.

Notes on the edges:

- A member the playtime index has never seen is **skipped**, not counted as a fresh character. A roster of names that never held a character earns nothing.
- Playtime is **per character**, not per account, and only the **active** character's time counts. A permakilled character stops earning the moment it dies.
- Without RPCharacters the whole playtime half is 0 and prestige behaves exactly as it did before this term existed.

## Where playtime comes from

RPCharacters owns the counter. See [rpcharacters/docs/playtime-tracking.md](../../rpcharacters/docs/playtime-tracking.md).

Do **not** reach for `RPCharacter.getAgeSeconds()`. That is wall-clock time since the character was created and keeps climbing while the player is offline, so it would cap out in about three weeks regardless of whether anyone logged in.

SimpleFactions reads it through a probe seam, the same pattern as `MercenaryEligibility`:

| Piece | Role |
|-------|------|
| `prestige/PlaytimePrestige` | The curve. Pure math, no Bukkit |
| `prestige/MemberPlaytime` | The seam and the roster sum. Default probe knows nobody |
| `prestige/RpCharactersPlaytimeProbe` | Reads the RPCharacters playtime index. Installed in `SimpleFactions.onEnable` only when that plugin is enabled |

## Performance

`updatePrestige()` is a **hot path**. `Bank` deposits and withdrawals call `Guild.updateWealth()`, which reaches `Faction.updateWealth()`, which ends in `FactionManager.updateAllPrestige()` - a recompute for **every** faction on **every** bank mutation. Each recompute may also walk up the overlord chain.

So a `MemberPlaytime.Probe` must answer from memory. The RPCharacters index is an in-memory map that also covers offline players, which is why the probe does no disk or network work. Never add I/O behind that interface, and never loop `updateWealth()` over factions to refresh before reading.

## Config

`config.yml`, read into `Cache` by `ConfigLoader`:

| Key | Default | Meaning |
|-----|---------|---------|
| `max-prestige-from-wealth` | `800` | Ceiling on the Wealth line, shared out by share of global wealth |
| `prestige-per-trade-power` | `0.1` | Prestige per point of the faction's own guilds' trade power, before the soft cap. `0` hides the Trade line |
| `prestige-from-trade-soft-cap` | `2000` | Linear Trade prestige up to this value. `0` disables the soft cap |
| `prestige-from-trade-falloff` | `0.1` | Marginal rate after one more cap-sized band. `1` is no falloff; `0` hard-caps at the soft cap |
| `max-prestige-playtime-exponent` | `5` | Ceiling on the per-member playtime exponent. `5` means a member tops out at `2^5` = 32, reached at 500 online hours |

Other prestige inputs are not `Cache` keys: per-tier prestige in `tiers.yml`, `minimum-prestige` per rank in `ranks.yml`, and the `prestige` / `prestige_bonus` modifiers plus `scale: relative_prestige` in `diplomacy.yml`.

## Tests

`PrestigeBreakdownTest` covers assembly and idempotency. `PlaytimePrestigeTest` covers the curve and the cap. `TradePrestigeTest` covers the trade soft cap and falloff. `MemberPlaytimeTest` covers the roster sum, unknown members, and that the default probe leaves prestige untouched.
