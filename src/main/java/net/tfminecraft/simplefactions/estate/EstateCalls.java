package net.tfminecraft.simplefactions.estate;

import java.nio.file.Path;
import java.util.UUID;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.estate.EstateFallback;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.EstateState;
import net.tfminecraft.tlibs.character.estate.Estates;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

/** Touches TLibs' estate types; loaded only after {@link EstateBridge} has probed them. */
final class EstateCalls {
    /** The participant's boot reconcile waits until every plugin has enabled, then it runs hourly. */
    static final long FIRST_RECONCILE_TICKS = 200L;
    static final long RECONCILE_TICKS = 20L * 60L * 60L;

    private EstateCalls() {}

    static boolean vehiclesAwaitEstate(UUID characterId) {
        return Estates.stateOf(characterId)
                .map(state -> state == EstateState.OPEN || state == EstateState.HELD || state == EstateState.DECIDED)
                .orElse(false);
    }

    static boolean heirMayInherit(UUID characterId) {
        return Estates.stateOf(characterId)
                .map(state -> state == EstateState.OPEN || state == EstateState.DECIDED)
                .orElse(false);
    }

    static void registerFallback(Plugin plugin) {
        plugin.getServer().getServicesManager().register(EstateFallback.class, new GuildEstateFallback(), plugin,
                ServicePriority.Normal);
    }

    static void registerVehicles(Plugin plugin, Path dataFolder, PlayerVehicleRegistry registry, VehicleFeeStore feeStore) {
        VehicleEstateParticipant participant = new VehicleEstateParticipant(dataFolder, registry, feeStore,
                plugin.getLogger());
        plugin.getServer().getServicesManager().register(EstateParticipant.class, participant, plugin,
                ServicePriority.Normal);
        plugin.getServer().getScheduler().runTaskTimer(plugin, participant::reconcile, FIRST_RECONCILE_TICKS,
                RECONCILE_TICKS);
    }
}
