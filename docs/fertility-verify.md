# Province fertility lookup - in-game verification

**Reference:** [fertility.md](./fertility.md). Crop **growth** and harvest QA is in Cooking [docs/crops.md](../../cooking/docs/crops.md).

Manual checks for the SimpleFactions fertility **score**, not growth ticks.

---

## Prerequisites

- Staging world with `Input/province_id_grid.bin.gz` and `provinces.txt` loaded (see [province-grid.md](./province-grid.md)).
- `config.yml`: `enable-map: true`, `enable-provinces: true`.
- Web map **Fertility** mode (or `provinces.txt`) to pick plots at known fertility **0**, **~40**, and **100**.

---

## Lookup

| # | Step | Expected |
|---|------|----------|
| 1 | Stand in a fertility 100 province with map enabled. Cooking harvest/growth uses 100. | Harvest is not barren-weighted; growth ticks are not fertility-0 blocked. |
| 2 | Stand in a fertility 0 province. | Harvest hamper; governed crops do not grow (Cooking gate). |
| 3 | Set `enable-provinces: false`, reload. | Cooking growth **allows** ticks. Harvest still sees fertility 0 from `CropFertility.at`. Restore flags after. |

Bone meal is TFMCCore, not SimpleFactions.

---

## Regression sweep

```bash
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.Map.fertility.FertilityProvinceResolverTest"
```

Cooking: `mvn test` (includes `CropGrowthChanceTest`, `CropGrowthGateTest`).
