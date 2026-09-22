# Province grid

> **Status:** Shipped (local O(1) province lookup).

SimpleFactions loads a prebuilt **province ID grid** at enable time. Block coordinates map 1:1 to grid indices, returning a province id in O(1). Used by claim, setcapital, construct, and port proximity checks.

---

## Purpose

| Before | After |
|------------------|-------|
| `RestServer.getProvince()` HTTP to ProvinceSystem | Local `ProvinceGrid.getAt(x, z)` |
| Port sea proximity impractical at scale | `ProvinceSpatial` scans nearby grid cells |

---

## Paths

| Artifact | Location |
|----------|----------|
| PS source PNG | `ProvinceSystem/backend/src/input/{map}/provinces.png` |
| PS RGB → id map | `defines/{map}/provinces.txt` |
| **PS grid output** | `defines/{map}/province_id_grid.bin.gz` |
| **SF input** | `plugins/SimpleFactions/Input/province_id_grid.bin.gz` |
| SF MapAPI | **Output only** — grid is **not** loaded from MapAPI |

**No `assets/` folder.** Copy grid manually from PS defines to SF Input after rebuilding.

---

## Binary format

Gzip-compressed:

1. `width` — int32 little-endian
2. `height` — int32 little-endian
3. `width × height` uint16 values, row-major
4. `0` = no province

Block X/Z ↔ grid index 1:1 (same as PS `find_province`). Example: 6400×6400 map ≈ 82 MB uncompressed array in memory.

---

## SF classes

| Class | Role |
|-------|------|
| `Map.ProvinceGrid` | Load gzip; `getAt(x, z)` → province id or 0 |
| `Map.ProvinceSpatial` | `isSeaAt`, `withinBlocksOfSea`, `withinConfiguredPortSeaProximity` |
| `REST.RestServer` | `getProvince(Player)` → grid lookup (no HTTP) |
| `SimpleFactions` | Load grid on enable; `getProvinceGrid()` |

### Fail loud

If `Input/province_id_grid.bin.gz` is missing on enable, the plugin **disables** (same severity as missing `provinces.txt`).

---

## Build workflow

Grid is **not** auto-generated on regen. Run manually when map geometry changes.

**1. Build grid (ProvinceSystem):**

```bash
cd ProvinceSystem/backend/src
python -m scripts.tools.build_province_id_grid --map main
```

Output: `defines/main/province_id_grid.bin.gz`

**2. Copy to SimpleFactions:**

```text
defines/main/province_id_grid.bin.gz
 → plugins/SimpleFactions/Input/province_id_grid.bin.gz
```

Dev template copy lives at `simplefactions/src/main/resources/Input/province_id_grid.bin.gz`.

---

## Port proximity

Ports require the construct location to be within **N** blocks of a sea/water province cell. **N** = `port-sea-proximity-blocks` in `config.yml` (default `20`).

`ProvinceSpatial.withinConfiguredPortSeaProximity(x, z)` scans the grid using this config value.

---

## Config

```yaml
port-sea-proximity-blocks: 20
```

Loaded via `ConfigLoader` into `Cache.portSeaProximityBlocks`.

---

## Package layout

```text
Map/
 ProvinceGrid.java
 ProvinceSpatial.java
```

---
