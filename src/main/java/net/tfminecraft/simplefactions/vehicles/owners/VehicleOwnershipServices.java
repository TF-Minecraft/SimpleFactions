package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.vehicleframework.api.QuarantinedVehicle;
import net.tfminecraft.vehicleframework.api.VehicleAccessGroups;
import net.tfminecraft.vehicleframework.api.VehicleOwnershipAuthority;
import net.tfminecraft.vehicleframework.api.VehicleQuarantineSource;

/** The VehicleFramework services SimpleFactions provides; only loaded once the API probe passed. */
public final class VehicleOwnershipServices {
    static final String GROUP_ACCESS_API = "net.tfminecraft.vehicleframework.api.VehicleAccessGroups";

    private VehicleOwnershipServices() {}

    /** Lists held-back vehicles for {@code /vf admin quarantine}, even when the vehicle stage stopped early. */
    public static void registerQuarantine(ServicesManager services, Plugin plugin, VehicleQuarantine quarantine) {
        services.register(VehicleQuarantineSource.class, () -> quarantined(quarantine), plugin, ServicePriority.Normal);
    }

    /** Registered last in the vehicle stage; VehicleFramework refuses non-staff changes until then. */
    public static void registerAuthority(ServicesManager services, Plugin plugin, BooleanSupplier ready) {
        services.register(VehicleOwnershipAuthority.class, new VehicleOwnershipAuthority() {
            @Override
            public boolean ready() {
                return ready.getAsBoolean();
            }
        }, plugin, ServicePriority.Normal);
    }

    /**
     * Answers the vehicle group switches. VehicleFramework before 2.15 has no such service, so
     * nothing is registered and vehicles keep their whitelists only.
     */
    public static boolean registerGroupAccess(ServicesManager services, Plugin plugin, PlayerVehicleRegistry registry,
            BooleanSupplier ready) {
        return registerGroupAccess(services, plugin, registry, ready, GROUP_ACCESS_API);
    }

    static boolean registerGroupAccess(ServicesManager services, Plugin plugin, PlayerVehicleRegistry registry,
            BooleanSupplier ready, String apiClass) {
        try {
            Class.forName(apiClass);
        } catch (ClassNotFoundException | LinkageError missing) {
            plugin.getLogger().warning("VehicleFramework 2.15 or newer is needed for faction, guild, ally and vassal "
                    + "vehicle access; vehicles keep their whitelists only");
            return false;
        }
        services.register(VehicleAccessGroups.class, new VehicleGroupAccess(registry, ready), plugin, ServicePriority.Normal);
        return true;
    }

    static List<QuarantinedVehicle> quarantined(VehicleQuarantine quarantine) {
        List<QuarantinedVehicle> out = new ArrayList<>();
        for (VehicleQuarantine.Entry entry : quarantine.list()) {
            try {
                out.add(new QuarantinedVehicle(UUID.fromString(entry.vehicle()), entry.owner(), entry.reason()));
            } catch (IllegalArgumentException notUuid) {
                // VehicleFramework IDs are UUIDs; a stray registry key cannot be shown by ID.
            }
        }
        return out;
    }
}
