# Phase 1: Installation transfer + empty-port navy

**Phase:** [00-index.md](./00-index.md)  
**Canonical:** [wars.md](../../wars.md), [installations.md](../../installations.md), [vehicles.md](../../vehicles.md)

Installations follow province owner. Wartime occupation transfers then always reverts at peace before land apply. Naval declare/push needs a port only. Defender ZOC port is locked in. Attacker auto-loses a naval slot with no berthed navy.

Do **not** implement civil wars (Phase 2).

## Exit (must all be true)

- Prestige steal claim moves installations to the thief; wilderness unclaim still dissolves.
- Every war end restores wartime installation snapshot, then existing apply; land apply can transfer again.
- Naval ZOC port is auto-committed for the defender and cannot be unpicked.
- Attacker can declare/push with an empty port. If they still have no berthed navy at a committed port at naval launch, they lose the slot and spend initiative.
- After vote close, no berth/unberth on in-play installs.

---

## Batch 1 - Province-owner installation transfer

**Done.** `InstallationTransferService.transfer` moves completed installs to the new owner and cancels pending construction on that tile. VF owner sync runs when a live berthed vehicle exists. Wilderness `onProvinceLost` still dissolves leftovers. Wired on prestige steal, de jure annex, `Guild.elevate`, and `Faction.dissolve` into overlord.

---

## Batch 2 - Wartime occupation transfer + snapshot + revert

**Done.** Occupation is exclusive (recapture strips the other side). `WartimeInstallationService` transfers installs on occupied tiles to the occupying war leader, except recapture which restores the snapshot original (vassal land). Snapshot `installationId -> originalFactionId` persists on war JSON. `WarManager.endWar` reverts the snapshot before `WarOutcomeService.apply`. Siege take also occupies the fort's own province.

---

## Batch 3 - Defender ZOC port auto-commit

**Done.** Current `NAVAL` / `NAVAL_INVASION` slot seeds the defender war leader's `battleInstallationPicks` with `portInstallationId`. `togglePick` cannot unpick it (`REJECTED_ZOC_PORT`). Campaign GUI marks the row as required. Other pickable ports and airports still toggle. Clear/new battle day re-seeds while the naval slot is current.

---

## Batch 4 - Pick-lock berth freeze

**Done.** After `BattleInstallationPickService.isLocked` (vote close), `VehicleInstallationLockService` blocks berth and unberth on in-play installs: committed picks, defender ZOC port, siege fort (`BattleInstallationInPlayService`). Other ports stay open. Raid repair lock and vulnerability embargo are unchanged.

---

## Batch 5 - Empty-port declare + attacker naval auto-loss + leader pings

**Done.** `CampaignNavyGate` stays operational-port-only (empty allowed). Navy copy is facility-required with source-ships-before-battle. Naval launch auto-loses when the attacker has no berthed `ships` vehicle at an in-play port, through the existing battle-end path with attacker fuel (`lastBattleOffensiveCoalition` forced to AGGRESSOR). Both-empty still auto-loses the attacker. `CampaignNavalAutoLossReminderService` pings the attacker war leader every battle-day tick while that would still fire.

---

## Batch 6 - Tests + canonical docs

**Done.** Steal-then-unclaim keeps the install on the new owner; wilderness dissolve unchanged. Wartime revert then land transfer, ZOC unpick, pick-lock berth freeze, and naval auto-loss fuel already covered. Unberth returns embargo when the install is locked. Canonical `wars.md`, `installations.md`, and `vehicles.md` patched.

---

## Out of scope

- Civil wars (Phase 2)
- Inter-vassal wars
- Border lock / claim freeze for civil war
- `BattlePoolService` using all troop types in civil war
- Gifting a coastal tile or spawning a port
- Changing campaign raid eligibility (except existing target berth embargo)
