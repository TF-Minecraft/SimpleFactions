package net.tfminecraft.simplefactions.vehicles.maintenance;

import net.tfminecraft.simplefactions.estate.EstateBridge;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterStatus;

/**
 * A dead payer's vehicles take no new unpaid days and no upkeep damage while they wait for
 * the estate: an unpaid marker set before the death stays until the recipient pays.
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
                .filter(row -> row.getMode() == OwnershipMode.PERSONAL && row.getPlayerUuid() != null)
                .map(row -> paused(row.getPlayerUuid().toString()))
                .orElse(false);
    }
}
