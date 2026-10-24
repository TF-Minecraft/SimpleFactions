package net.tfminecraft.simplefactions.estate;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import org.bukkit.plugin.Plugin;

/**
 * Optional access to TLibs' estate API (2.6.0), probed once. API, probe and registration
 * failures are contained here; vehicle registry writes still follow the ordinary
 * financial fail-closed contract.
 */
public final class EstateBridge {
    static final String[] API = {
            "net.tfminecraft.tlibs.character.estate.Estates",
            "net.tfminecraft.tlibs.character.estate.EstateFallback",
            "net.tfminecraft.tlibs.character.estate.EstateParticipant",
            "net.tfminecraft.tlibs.character.estate.EstateState",
            "net.tfminecraft.tlibs.character.estate.GuildRef"};
    private static volatile Boolean available;

    private EstateBridge() {}

    /** Whether TLibs has the estate API. */
    public static boolean available() {
        Boolean known = available;
        if (known == null) {
            known = probe(API);
            available = known;
        }
        return known;
    }

    static boolean probe(String... classes) {
        try {
            for (String type : classes) Class.forName(type);
            return true;
        } catch (ReflectiveOperationException | LinkageError missing) {
            return false;
        }
    }

    /** Sets the cached probe result, or null to probe again; for tests. */
    static void useProbe(Boolean result) {
        available = result;
    }

    /**
     * Whether a dead payer's personal vehicles still wait for their estate: its latest death's
     * estate is OPEN, HELD or DECIDED. False when that cannot be told.
     */
    public static boolean vehiclesAwaitEstate(UUID characterId) {
        if (characterId == null || !available()) return false;
        try {
            return EstateCalls.vehiclesAwaitEstate(characterId);
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /**
     * Whether a will heir may take on what a dead character left: its latest death's estate is OPEN
     * or DECIDED, so staff have not held or reverted it. False when that cannot be told.
     */
    public static boolean heirMayInherit(UUID characterId) {
        if (characterId == null || !available()) return false;
        try {
            return EstateCalls.heirMayInherit(characterId);
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /** Offers the death-guild answers once factions have started. */
    public static void registerFallback(Plugin plugin) {
        if (!available()) {
            plugin.getLogger().info("TLibs has no estate API, so SimpleFactions takes no part in estates");
            return;
        }
        try {
            EstateCalls.registerFallback(plugin);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "Could not offer guild answers for estates", error);
        }
    }

    /** Registers the vehicles participant once the vehicle stage has loaded its stores. */
    public static void registerVehicles(Plugin plugin, Path dataFolder, PlayerVehicleRegistry registry,
            VehicleFeeStore feeStore) {
        if (!available()) return;
        try {
            EstateCalls.registerVehicles(plugin, dataFolder, registry, feeStore);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "Could not register the vehicles estate participant", error);
        }
    }
}
