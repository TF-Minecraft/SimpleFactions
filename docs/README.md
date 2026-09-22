# SimpleFactions documentation

**SimpleFactions** is the TFMC Paper plugin for nations, diplomacy, economy, wars, installations, settlements, and map export to [tfminecraft.net](https://www.tfminecraft.net/).

This `docs/` folder is the product and technical reference for the **SimpleFactions** repository. The website map pipeline is documented in [ProvinceSystem/docs/integrations/simplefactions.md](../../ProvinceSystem/docs/integrations/simplefactions.md).

## Reading order

1. [roadmap.md](./roadmap.md) - shipped vs planned (next: Phase 9 / [TODO.md](../TODO.md))
2. [map-export.md](./map-export.md) - HTTP upload, regen, JSON contract
3. [province-grid.md](./province-grid.md) - local province lookup
4. Product areas:
   - [fertility.md](./fertility.md) - province fertility 0-100 lookup (Cooking owns crop growth)
   - [fertility-verify.md](./fertility-verify.md) - in-game QA matrix for fertility crop growth
   - [prestige.md](./prestige.md) - what a nation's standing is made of, and the playtime term
   - [wars.md](./wars.md) - automated campaign war system (canonical spec)
   - [campaign-raids.md](./campaign-raids.md) - inter-battle installation raids
   - [mercenaries.md](./mercenaries.md) - companies for hire, contracts, wages, reputation
   - [installations.md](./installations.md) - forts, ports, airports
   - [settlements.md](./settlements.md) - named cities on the map
   - [vehicles.md](./vehicles.md) - berths, slots, VehicleFramework integration
5. [dev-config.md](./dev-config.md) - dev-only config and bypasses

## Verify (tests)

```bash
cd simplefactions && mvn test -Dtest="net.tfminecraft.simplefactions.War.**"   # war changes
cd simplefactions && mvn test -Dtest="net.tfminecraft.simplefactions.vehicles.**"  # vehicle berth changes
cd simplefactions && mvn test                                              # broad changes
```

## Related repos

| Component | Role | Docs |
|-----------|------|------|
| **ProvinceSystem** | Mapgen, web map, file serving | [ProvinceSystem/docs/](../../ProvinceSystem/docs/) |
| **TFMCWeb** | HTTP gateway to ProvinceSystem | [ProvinceSystem/docs/identity/tfmcweb.md](../../ProvinceSystem/docs/identity/tfmcweb.md) |
| **VehicleFramework** | Vehicle entities (berth integration) | VF plugin repo |
