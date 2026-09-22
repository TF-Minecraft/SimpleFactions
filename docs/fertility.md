# Province fertility

Each province has a fertility score **0-100** stored in `provinces.txt` and looked up on the province grid. SimpleFactions exposes that score. **Crop growth cancel and harvest stars live in Cooking.**

**Province grid:** [province-grid.md](./province-grid.md) · **Map fertility layer:** [ProvinceSystem map overview](../../ProvinceSystem/docs/map/overview.md) · **Cooking crops:** [cooking/docs/crops.md](../../cooking/docs/crops.md)

---

## What SimpleFactions owns

`FertilityProvinceResolver`:

- `isActive()` is true only when `Cache.provincesEnabled` and `Cache.mapEnabled` are both true.
- `fertilityAt(x, z)` / `fertilityAt(Location)` reads `ProvinceGrid.getAt` then `ProvinceManager.get(id).getFertility()`.
- Unmapped or invalid province id → **0**.
- Missing plugin / inactive map: Cooking harvest treats fertility as **0**; Cooking **does not cancel growth** when the map is off.

Cooking calls this API through `CropFertility`. There is no `fertility-crops.yml` and no `BlockGrowEvent` listener in SimpleFactions.

---

## Fertility data source

Per-province fertility comes from `provinces.txt` field 3:

```
id = R,G,B;terrain;fertility
```

Mapgen writes the fertility layer; the web map viewer fertility mode reads the same source. See [ProvinceSystem title-editor](../../ProvinceSystem/docs/map/title-editor.md).

---

## Class map

| Class | Role |
|-------|------|
| `Map/fertility/FertilityProvinceResolver` | Grid + `ProvinceManager` fertility |

---

## Tests

```bash
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.Map.fertility.FertilityProvinceResolverTest"
```

Growth formula, vanilla cancel, and CustomCrops `province-fertility` tests are in the Cooking plugin.
