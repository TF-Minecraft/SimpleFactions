package net.tfminecraft.simplefactions.vehicles.owners;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import net.tfminecraft.simplefactions.utils.Permissions;

/** Registers a claim guard without loading listener types absent from the installed provider. */
public final class VehicleClaimGuards {
    public static final String MESSAGE = "§cClaiming vehicles is off until VehicleFramework is updated.";
    static final String CLAIM_EVENT = "net.tfminecraft.vehicleframework.events.VehicleOwnershipChangeEvent";
    /** The claim event of VehicleFramework before 2.13; 2.14 removed it, so it is only named, never linked. */
    static final String LEGACY_CLAIM_EVENT = "net.tfminecraft.vehicleframework.events.VehicleOwnerClaimedEvent";

    private VehicleClaimGuards() {}

    public static void register(PluginManager plugins, Plugin plugin) {
        register(plugins, plugin, CLAIM_EVENT, LEGACY_CLAIM_EVENT);
    }

    static void register(PluginManager plugins, Plugin plugin, String claimEvent, String legacyEvent) {
        if (eventClass(claimEvent) != null) {
            plugins.registerEvents(new VehicleClaimsOff(), plugin);
            return;
        }
        Class<? extends Event> legacy = eventClass(legacyEvent);
        if (legacy == null) throw new IllegalStateException("VehicleFramework has no supported claim event");
        plugins.registerEvent(legacy, new Listener() {}, EventPriority.LOWEST,
                (listener, event) -> refuseLegacy(event), plugin, true);
    }

    private static Class<? extends Event> eventClass(String name) {
        try {
            return Class.forName(name).asSubclass(Event.class);
        } catch (ClassNotFoundException | ClassCastException | LinkageError missing) {
            return null;
        }
    }

    /** The legacy event is only a claim; its player is read by name. */
    static void refuseLegacy(Event event) {
        if (!(event instanceof Cancellable cancellable)) return;
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            refuse(player instanceof Player p ? p : null, cancellable);
        } catch (ReflectiveOperationException | LinkageError unreadable) {
            // Without a readable player the claim cannot be judged; VehicleFramework keeps its own rules.
        }
    }

    static void refuse(Player player, Cancellable event) {
        if (player == null || Permissions.isAdmin(player)) return;
        event.setCancelled(true);
        player.sendMessage(MESSAGE);
    }
}
