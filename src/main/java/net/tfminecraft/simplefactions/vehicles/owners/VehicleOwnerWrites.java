package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Reason;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi.Result;

/**
 * Every owner value SimpleFactions writes goes through here, and so through
 * VehicleFramework's {@code setOwnership}. Without a usable VehicleFramework
 * every write answers {@link Result#UNAVAILABLE}, so callers fail closed.
 */
public final class VehicleOwnerWrites {
    private static volatile VehicleOwnerApi api;

    private VehicleOwnerWrites() {}

    /** The VehicleFramework 2.13 API, or null for an older VehicleFramework (rule 11 probe). */
    public static VehicleOwnerApi probe() {
        return probe("net.tfminecraft.vehicleframework.api.VehicleFrameworkApi");
    }

    static VehicleOwnerApi probe(String apiClass) {
        try {
            Class<?> type = Class.forName(apiClass);
            type.getMethod("ownershipIdentity");
            type.getMethod("listOwnershipMeta");
            type.getMethod("readOwnershipMeta", UUID.class);
            type.getMethod("setOwnership", UUID.class,
                    Class.forName("net.tfminecraft.vehicleframework.api.OwnerChange"),
                    Class.forName("net.tfminecraft.vehicleframework.api.SystemReason"));
            return new VfOwnerApi();
        } catch (ReflectiveOperationException | LinkageError missing) {
            return null;
        }
    }

    public static void use(VehicleOwnerApi next) {
        api = next;
    }

    public static VehicleOwnerApi api() {
        return api;
    }

    /** Whether owner writes can succeed: the API is present and in characters mode. */
    public static boolean ready() {
        VehicleOwnerApi current = api;
        if (current == null) return false;
        try {
            return current.characters();
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    public static Result write(String vehicle, String owner, Reason reason) {
        return write(vehicle, owner, null, reason);
    }

    public static Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
        VehicleOwnerApi current = api;
        if (current == null) return Result.UNAVAILABLE;
        try {
            return current.write(vehicle, owner, whitelist, reason);
        } catch (RuntimeException | LinkageError error) {
            log("Could not set the owner of vehicle " + vehicle + " to " + owner + " (" + reason + ")", error);
            return Result.SAVE_FAILED;
        }
    }

    /** The vehicle's ownership fields; empty when it is gone or cannot be read. */
    public static Optional<OwnerMeta> read(String vehicle) {
        VehicleOwnerApi current = api;
        if (current == null || vehicle == null) return Optional.empty();
        try {
            return current.read(vehicle);
        } catch (RuntimeException | LinkageError error) {
            log("Could not read the owner of vehicle " + vehicle, error);
            return Optional.empty();
        }
    }

    /** The current owner value, or null when the vehicle is gone or cannot be read. */
    public static String currentOwner(String vehicle) {
        return read(vehicle).map(OwnerMeta::currentOwner).orElse(null);
    }

    static void log(String message, Throwable error) {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) plugin.getLogger().log(Level.WARNING, message, error);
    }
}
