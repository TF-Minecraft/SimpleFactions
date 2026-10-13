package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import net.tfminecraft.vehicleframework.api.QuarantinedVehicle;
import net.tfminecraft.vehicleframework.api.VehicleOwnershipAuthority;
import net.tfminecraft.vehicleframework.api.VehicleQuarantineSource;

/** The two VehicleFramework services SimpleFactions provides; only loaded once the API probe passed. */
public final class VehicleOwnershipServices {
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
