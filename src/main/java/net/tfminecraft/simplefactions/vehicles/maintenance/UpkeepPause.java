package net.tfminecraft.simplefactions.vehicles.maintenance;

import net.tfminecraft.simplefactions.estate.EstateBridge;
import net.tfminecraft.simplefactions.guild.HeldRealms;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterStatus;

/**
 * A dead payer's vehicles take no new unpaid days and no upkeep damage while they wait for
 * the estate: an unpaid marker set before the death stays until the recipient pays. A held
 * realm's pool and installation vehicles are paused the same way.
 */
final class UpkeepPause {
    private UpkeepPause() {}

    static boolean paused(String payer) {
        var info = Members.info(payer);
        return info.isPresent() && info.get().status() == CharacterStatus.DEAD
                && EstateBridge.vehiclesAwaitEstate(info.get().ref().characterId());
    }

    static boolean paused(PlayerVehicleRegistry registry, String vehicle) {
        return registry.getByVehicleUuid(vehicle)
                .map(row -> row.getMode() == OwnershipMode.PERSONAL
                        ? row.getPlayerUuid() != null && paused(row.getPlayerUuid().toString())
                        : HeldRealms.isHeld(FactionVehiclePoolService.payingFaction(row)))
                .orElse(false);
    }
}
