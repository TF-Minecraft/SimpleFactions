package net.tfminecraft.simplefactions.vehicles.owners;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Listener;

import net.tfminecraft.simplefactions.utils.Permissions;

/** Selects a claim guard without loading listener types absent from the installed provider. */
public final class VehicleClaimGuards {
    public static final String MESSAGE = "§cClaiming vehicles is off until VehicleFramework is updated.";

    private VehicleClaimGuards() {}

    public static Listener create() {
        return create("net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent");
    }

    static Listener create(String claimEvent) {
        if (present(claimEvent)) return new VehicleClaimsOff();
        throw new IllegalStateException("VehicleFramework has no supported claim event");
    }

    private static boolean present(String eventClass) {
        try {
            Class.forName(eventClass);
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    static void refuse(Player player, Cancellable event) {
        if (player == null || Permissions.isAdmin(player)) return;
        event.setCancelled(true);
        player.sendMessage(MESSAGE);
    }
}
