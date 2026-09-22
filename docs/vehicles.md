# Vehicles

Player-owned **VehicleFramework** entities integrate with SimpleFactions through `vehicles/` subpackages: `registry/` (berthed records), `berth/` (slots, transfer, category rules), `maintenance/` (upkeep, unpaid decay, pouch pay), and `battle/` (campaign eligibility).

**Installations:** berth radius, operational state, and campaign picks are in [installations.md](./installations.md).

---

## Config (`vehicles.yml`)

Loaded by `VehiclesConfigLoader` at plugin enable.

| Key | Default | Role |
|-----|---------|------|
| `personal-slot-limit` | `3` | Max vehicles per player (unless type overrides) |
| `default-upkeep` | `4` | Denars per upkeep tick when type omits `upkeep` |
| `default-per-person` | `1` | Default `per-person` cap per type |
| `categories.*` | See file | Nested vehicle types by category |

Per-type keys (under each category):

| Key | Role |
|-----|------|
| `upkeep` | Denar cost per upkeep cycle |
| `size` | Slot weight (cruiser/behemoth use 2+) |
| `per-person` | Override personal cap for this type |
| `ignore-limit` | When `true`, does not count toward `personal-slot-limit` (trains) |

Categories in shipped config: `land_vehicles`, `train`, `ships`, `static_emplacements`, `aircraft`.

---

## Package layout

| Class | Role |
|-------|------|
| `registry/PlayerVehicleRegistry` / `VehicleRegistryPersistence` | Berthed (`INSTALLATION`) vehicle records only |
| `registry/VehicleOwnershipQueries` | Personal vehicles from VF owner minus berthed UUIDs |
| `berth/VehicleSlotGuard` | Personal limit + `ignore-limit` checks at claim/build |
| `berth/InstallationVehicleService` | Berth at port/airport/fort installations |
| `berth/VehicleTransferConsentService` / `VehicleTransferListener` | Two-party transfer flow |
| `VehicleSpawnListener` | Re-apply faction-leader VF owner on spawn for berthed vehicles |
| `VehicleIntegrationListener` | VF construction events (sets VF owner; no personal registry row) |
| `maintenance/VehicleUpkeepService` | Periodic denar upkeep for unberthed VF-owned vehicles |
| `battle/BattleVehicleEligibilityService` | Campaign battle in-play checks |
| `berth/VehicleInstallationLockService` | Berth embargo during battles and raids |
| `berth/VehicleCategoryRules` | Berthable vs train/static categories |

VF-specific logic stays in `vehicles/`; installation bounds and handler state stay in `installation/`.

---

## Personal slots

Personal ownership is the VehicleFramework owner (`player_<name>`). SimpleFactions does not store a second personal row.

When a player claims or builds a vehicle:

1. Resolve type config from `vehicles.yml`.
2. Count VF-owned vehicles for that player, **excluding** any UUID currently berthed at an installation.
3. If `ignore-limit: true`, skip the total cap (locomotives, rail cars).
4. Else apply `personal-slot-limit` and type `per-person`.

Unowned (`none`) interact is cancelled when the claim would exceed those limits.

`VehicleConstructionMessages` and `VehicleSlotGuard` surface player-facing errors.

---

## Installation berths

Operational **ports** and **airports** accept berthable vehicles within installation bounds (`InstallationBounds` + config radius).

| Flow | Detail |
|------|--------|
| Berth | Player brings vehicle into installation radius; VF transfer hooks |
| Unberth | `InstallationVehicleUnberthService` |
| Owner sync | `InstallationVehicleOwnerSync` on faction/installation changes |

Forts do not berth vehicles. Campaign **installation picks** choose which port/airport vehicles are in-play for a battle day; see [installations.md](./installations.md#campaign-installation-picks).

---

## Campaign battle eligibility

`BattleVehicleEligibilityService` checks:

- Vehicle is at a **committed** installation for the current battle day, **or**
- Siege fort from the active schedule slot (`fortInstallationId`) for the owning faction.

Trains and non-berthable categories follow `VehicleCategoryRules`. Listener blocks ineligible spawns during active campaign battles.

---

## Locks during battles and raids

| Lock | Service |
|------|---------|
| Berth / unberth embargo | `VehicleInstallationLockService` |
| Installation damage gating | `InstallationVulnerabilityService` (see [campaign-raids.md](./campaign-raids.md)) |

After `BattleInstallationPickService.isLocked` (vote close), berth **and** unberth are blocked on **in-play** installs: committed picks, defender ZOC port, siege fort. Ports not in play stay open for the next battle day.

Vehicle repair is always allowed. Raid **target** keeps the post-raid berth lock (`war.campaign_raid.repair_lock_hours`, default 48h). Raid/battle vulnerability embargo is unchanged.

### Official navy at naval launch

A war attacker contests a naval slot only if some attacker-side participating faction has an in-play **port** with a berthed vehicle whose type maps to category `ships` (`PlayerVehicleRegistry` `INSTALLATION` row). Personal unberthed ships do not count. See [wars.md](./wars.md#attacker-naval-launch).

---

## Economy

`DenarEconomyPlayerBank` implements `PlayerBank` for vehicle upkeep charges against faction/player denars.

---

## Tests

```bash
cd simplefactions && mvn test -Dtest="me.Plugins.SimpleFactions.vehicles.**"
```

Key tests: `VehicleSlotGuard`, `BattleVehicleEligibilityService`, `VehicleInstallationLockService`, `VehicleOwnershipQueries`.

---

## Related docs

- [installations.md](./installations.md) - ports, airports, construction
- [wars.md](./wars.md) - installation picks and vehicle in-play
- [campaign-raids.md](./campaign-raids.md) - installation repair embargo on raid targets
- [dev-config.md](./dev-config.md) - construction timing on test server
