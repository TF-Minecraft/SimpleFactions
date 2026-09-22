# Campaign raids

**Shipped.** Between scheduled **campaign** battles, faction leaders may launch **installation assaults** during a fixed daily window. Raids are not a separate war type; they run inside an active automated campaign war.

**Canonical war context:** [wars.md](./wars.md#campaign-raids) · **Installations:** [installations.md](./installations.md) · **Vehicles:** [vehicles.md](./vehicles.md)

---

## What it is

| Term | Meaning |
|------|---------|
| **Campaign raid** | Timed assault from own port/airport to enemy port/airport/fort |
| **Pillage war** | Planned one-battle settlement war type (not this feature) |
| **Staff `BattleType.RAID`** | Manual template battle with capture points (dev tool) |

One raid per **coalition side** per battle day. One active raid per war at a time.

---

## Battle day timeline (Europe/Paris)

Config keys under `war.battle_schedule`:

| Phase | Default hours | Rule |
|-------|---------------|------|
| Defender choice deadline | 12 | Hold / counter-push / white peace |
| Vote close + pick lock | 16 | Hour vote tally; installation picks freeze |
| **Raid window** | 19-20 | Leaders may **launch** a raid |
| Warband signup blocked | 19-20 | `CampaignWarbandSignupService` |
| Warband signup open | 20-21 | Pre-battle muster |
| Battle window | 21-24 | Scheduled campaign fights |

In-flight raids may run past 20:00 (60s muster + 10 min fight timer).

---

## Launch flow

1. Faction **leader** on a belligerent side opens campaign GUI → **Start raid**.
2. Page 1: pick **source** (own operational port or airport).
3. Page 2: pick **target** (enemy operational port, airport, or fort).
4. **60s muster** (`war.campaign_raid.muster_seconds`); broadcast + `/raid join <raid>` (slug from raid name, e.g. `harbor_raid`).
5. **10 min fight** (`duration_seconds`); timer-only outcome.

**Eligibility:** `CampaignRaidEligibilityService` (not installation picks).

| Role | Rule |
|------|------|
| Source | Launching faction's operational `port` or `airport` |
| Target | Any enemy operational `port`, `airport`, or `fort` |
| Raid kind | `NAVAL` (port→port), `AIR` (airport→airport), `FORT` (port/airport→fort) |

Quota: first confirming leader spends the side's daily raid on that `battleDay`.

---

## Join rules

| Rule | Detail |
|------|--------|
| Who may join | **Attacker coalition** only via `/raid join` |
| Exclusion | Cannot join if already in **any** warband |
| Defenders | Online at fight start + logins during raid auto-join defender warband if warband-free |
| Attacker TP | Source installation center at fight start |
| Defender TP | None at start; respawn at **target** center |

Warband ids: `{raid_slug}_attacker` / `{raid_slug}_defender` (e.g. `harbor_raid_attacker`).

Raid display names follow battle naming: `Harbor Raid`, `Second Harbor Raid`, etc. Join id is the slugified name (`harbor_raid`).

---

## Fight rules (`campaign_raid_template`)

| Rule | Value |
|------|-------|
| Capture points | None |
| Win condition | Timer or all raiders eliminated (defender win); no early end from defender logout |
| Attacker lives | One each; death or disconnect = out |
| Defender respawn | Infinite at target center |
| `keep_inventory` | `true` for participants |
| Boss bars | Blue: raid name + time remaining. Red: raiders remaining (attackers). Side life bars hidden. |
| Province fence | None |
| Intruders | Attacker-coalition players in target province who are not raid participants take periodic damage |

---

## Damage and repair embargo

On the **target installation** during and after a raid:

| Effect | Detail |
|--------|--------|
| Damage gating | `InstallationVulnerabilityService` - blocks most block damage except configured exceptions |
| Installation repair embargo | `InstallationRepairEmbargoService` (toggle: `installation_repair_embargo_enabled`) |
| Berth embargo | `VehicleInstallationLockService` |
| Post-raid lock | `repair_lock_hours` (default **48**) from fight start |

Berthed vehicles at the target cannot be newly berthed while locked. Vehicle repair is always allowed. See [installations.md](./installations.md#campaign-raid-damage-and-repair).

---

## Config

```yaml
war:
  campaign_raid:
    muster_seconds: 60
    duration_seconds: 600
    repair_lock_hours: 48
    installation_repair_embargo_enabled: true
    intruder_damage_interval_ticks: 10
    intruder_damage_amount: 4
```

Battle template: `battle-templates.yml` → `campaign_raid_template` (`campaign_raid: true`).

---

## Code map

| Area | Classes |
|------|---------|
| State / quota | `CampaignRaidService` |
| Eligibility | `CampaignRaidEligibilityService` |
| Launch GUI | `CampaignRaidLaunchView` |
| Muster / join | `CampaignRaidJoinService`, `CampaignRaidMusterScheduler` |
| Warbands | `CampaignRaidWarbandService`, `CampaignRaidWarbandListener` |
| Fight | `CampaignRaidLaunchService`, `CampaignRaidBattleService`, `CampaignRaidFightScheduler`, `CampaignRaidBossBarService` |
| End | `CampaignRaidBattleEndService` |
| Intruders | `CampaignRaidIntruderService`, `CampaignRaidIntruderListener`, `CampaignRaidIntruderTickService` |
| Command | `RaidCommandManager` |

---

## Related docs

- [wars.md](./wars.md) - full campaign system
- [roadmap.md](./roadmap.md) - shipped feature list
- [dev-config.md](./dev-config.md) - shortened schedules on test server
