# Batch 02: Command + confirm

**Status:** done

## Deliverable

`/warband retreat` command with confirm GUI, wired to [`BattleWarbandRetreatService`](../../../src/main/java/me/Plugins/SimpleFactions/War/battle/campaign/BattleWarbandRetreatService.java).

## Command flow

1. Player runs `/warband retreat`
2. `BattleWarbandRetreatService.retreatRejection(player, now)` - on failure, send `BattleWarbandRetreatMessages.messageForResult`
3. On success: `FactionManager.inv.confirmBattleRetreatView(player, battleId)` opens confirm GUI
4. Confirm click → `BattleWarbandRetreatConfirmHandler.handleConfirm` → `BattleWarbandRetreatService.retreat`

## Confirm GUI

| Item | Detail |
|------|--------|
| Key | `warband_battle_retreat` (NamespacedKey) |
| Data | Battle id string |
| Info lore | Side loses; ledger casualties apply |
| Handler | [`BattleWarbandRetreatConfirmHandler`](../../../src/main/java/me/Plugins/SimpleFactions/War/battle/campaign/BattleWarbandRetreatConfirmHandler.java) |

Uses `FactionManager.inv` (singleton listener) for `confirming` map - not a new `InventoryManager` instance.

## Tab completion

`BattleTabCompletion` includes `retreat` and `leave` in warband subcommands.

## Manual smoke

1. Set `battle.retreat_min_elapsed_seconds: 0` for fast testing
2. Start campaign field/siege battle as warband leader
3. `/warband retreat` → confirm GUI
4. Cancel → battle continues
5. Confirm → opponent wins, success message
6. Restore cooldown; verify too-early rejection before GUI
7. Non-leader: rejection on command, no GUI

## Next

Batch 03: `BattleWarbandRetreatServiceTest`
