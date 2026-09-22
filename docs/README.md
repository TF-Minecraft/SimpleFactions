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

## Planning (in-repo batches)

- [campaign-time-dev](./planning/campaign-time-dev/00-index.md) - spoof campaign schedule clock for E2E QA (`campaigntime`, route countdown)
- [campaign-retreat](./planning/campaign-retreat/00-index.md) - strategic retreat during voting, live GUI refresh
- [battle-retreat](./planning/battle-retreat/00-index.md) - mid-fight `/warband retreat` during campaign battles
- [war-goals-apply](./planning/war-goals-apply/00-index.md) - gameplay lock; [01-phases.md](./planning/war-goals-apply/01-phases.md) - phases; [02-phase-1.md](./planning/war-goals-apply/02-phase-1.md) - Phase 1 batches
- [naval-installations](./planning/naval-installations/00-index.md) - installation transfer + empty-port navy (Phase 1); civil wars spec (Phase 2)
- [inter-vassal-wars](./planning/inter-vassal-wars/00-index.md) - Phase 8 lock (shipped); [01-batches.md](./planning/inter-vassal-wars/01-batches.md)
- [war-companies](./planning/war-companies/00-index.md) - gameplay lock (shipped); [01-phases.md](./planning/war-companies/01-phases.md) - phases; [08-verify.md](./planning/war-companies/08-verify.md) - in-game matrix

## Agent / contributor guide

Code layout and naming rules: [../AGENTS.md](../AGENTS.md)

## Verify (tests)

```bash
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.War.**"   # war changes
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.vehicles.**"  # vehicle berth changes
cd simplefactions && mvn test                                              # broad changes
```

## Related repos

| Component | Role | Docs |
|-----------|------|------|
| **ProvinceSystem** | Mapgen, web map, file serving | [ProvinceSystem/docs/](../../ProvinceSystem/docs/) |
| **TFMCWeb** | HTTP gateway to ProvinceSystem | [ProvinceSystem/docs/identity/tfmcweb.md](../../ProvinceSystem/docs/identity/tfmcweb.md) |
| **VehicleFramework** | Vehicle entities (berth integration) | VF plugin repo |
