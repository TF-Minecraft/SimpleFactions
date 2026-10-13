package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.tfminecraft.vehicleframework.api.OwnerChange;
import net.tfminecraft.vehicleframework.api.OwnershipIdentity;
import net.tfminecraft.vehicleframework.api.OwnershipMeta;
import net.tfminecraft.vehicleframework.api.OwnershipValues;
import net.tfminecraft.vehicleframework.api.SystemReason;
import net.tfminecraft.vehicleframework.api.VehicleFrameworkApi;

/** The only class that calls VehicleFrameworkApi; loaded once the probe has found it. */
final class VfOwnerApi implements VehicleOwnerApi {

    @Override
    public boolean characters() {
        return VehicleFrameworkApi.ownershipIdentity() == OwnershipIdentity.CHARACTERS;
    }

    @Override
    public List<OwnerMeta> list() {
        return VehicleFrameworkApi.listOwnershipMeta().stream().map(VfOwnerApi::meta).toList();
    }

    @Override
    public Optional<OwnerMeta> read(String vehicle) {
        UUID id = uuid(vehicle);
        return id == null ? Optional.empty() : VehicleFrameworkApi.readOwnershipMeta(id).map(VfOwnerApi::meta);
    }

    @Override
    public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
        UUID id = uuid(vehicle);
        if (id == null) return Result.NOT_FOUND;
        OwnerChange change = whitelist == null
                ? new OwnerChange(owner, null) : OwnerChange.seeded(owner, whitelist);
        return Result.valueOf(VehicleFrameworkApi.setOwnership(id, change, SystemReason.valueOf(reason.name())).name());
    }

    static OwnerMeta meta(OwnershipMeta meta) {
        return new OwnerMeta(meta.vehicleId().toString(), meta.typeId(), meta.storedOwnerColumn(),
                meta.stored().map(VfOwnerApi::fields), meta.live().map(VfOwnerApi::fields));
    }

    static OwnerFields fields(OwnershipValues values) {
        return new OwnerFields(values.owner(), values.whitelist(), values.whitelisted(), values.ticketsEnabled(),
                values.ticketId());
    }

    private static UUID uuid(String vehicle) {
        try {
            return vehicle == null ? null : UUID.fromString(vehicle);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
