# Settlements

> **Status:** Shipped (map markers, one province per settlement).

Settlements are **named cities** on the political map. A faction owns zero or more settlements. Each settlement occupies **exactly one province** — the province where it was founded — with a display name and map marker coordinates (`centerX` / `centerZ`).

Guild and faction **capitals** are separate: they point at a province. A guild counts toward a settlement’s population when its capital province **is** that settlement’s province.

---

## Concepts

| Term | Meaning |
|------|---------|
| **Settlement** | Named city on a single province |
| **Centre province** | The only province in the settlement (`centerProvince`) |
| **Capital (faction/guild)** | Province id on `Faction` / `Guild` — seat of government or guild HQ |
| **Population** | Guilds whose `capital` equals the settlement’s `centerProvince` |

**Invariant:** At most one settlement per province per faction. Faction land and settlement territory are independent — claiming a province does not add it to any settlement.

---

## Data model

### `settlement.Settlement`

| Field | Type | Notes |
|-------|------|--------|
| `id` | `String` | From `Formatter.formatId` |
| `name` | `String` | Display name |
| `centerProvince` | `int` | Sole province; always the only entry in `provinces` |
| `centerX` / `centerZ` | `int` | Block coords at founding (map marker) |
| `provinces` | `Set<Integer>` | Always `{ centerProvince }` after load/validate |

### `settlement.handler.SettlementHandler` (per `Faction`)

| Responsibility | Notes |
|----------------|--------|
| `byId` / `provinceIndex` | Lookup; index maps centre province → settlement |
| `found` | Create settlement on one province |
| `resolveCapital` | Set capital in existing city or found new with name |
| `onProvinceLost` | Lose settlement province → dissolve |
| `validate()` | Normalize to single province; dissolve if centre not owned |

---

## Commands

Player stands in the target province. Block coords taken from player location when **founding**.

### `/faction setcapital [name]`

| Situation | Behaviour |
|-----------|-----------|
| Faction has **0 provinces** | **Require** `name` → claim + found settlement + set capital |
| Province **has** a settlement | Set faction capital (no name) |
| Province **has no** settlement | **Require** `name` → found new settlement |

### `/guild setcapital [name]`

Same as faction, except base guild must use `/faction setcapital`.

### `/faction claim`

Adds faction territory only. **Does not** create or expand settlements.

---

## Founding

`/setcapital <name>` on a province without a settlement:

1. Create `Settlement` with `centerProvince = P`, coords from player.
2. `provinces = { P }` only.
3. Set guild or faction capital to `P`.

Adjacent provinces may have **separate** settlements — no distance rule.

---

## Relocate

Last guild leaving a settlement (relocate, capital clear, guild remove) → **dissolve**. Destination uses the same `/setcapital` rules: existing city if the province has one, otherwise require a name and found.

---

## Territory loss

When the faction **loses** the settlement’s province → **dissolve** the settlement (clear capitals in that province, remove from handler).

---

## Dissolve

When centre province is lost or last guild leaves the city:

1. Clear guild/faction capitals on that province.
2. Remove settlement from handler.
3. Enqueue map update.

---

## Population

```text
population(S) = { guild g in faction | g.capital == S.centerProvince }
```

---

## Map export

See `Map/export/Markers.java` — `map_markers.json` per settlement:

| Field | Source |
|-------|--------|
| `province_id` | `centerProvince` |
| `center_x` / `center_z` | founding coords |
| `provinces` | `[centerProvince]` |
| `kind` | `faction_capital` if faction capital == centre |
| `population` / `marker_size` | guild count vs threshold |

---

## Package layout

```text
settlement/
 Settlement.java
 handler/
 SettlementHandler.java
 CapitalResult.java
```

---
