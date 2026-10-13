package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** VehicleFramework's ownership store in memory, with the same whitelist rules per reason. */
public final class FakeOwnerApi implements VehicleOwnerApi {
    public boolean characters = true;
    public final Map<String, OwnerMeta> vehicles = new LinkedHashMap<>();
    public final List<String> writes = new ArrayList<>();
    /** When set, every write answers this without changing anything. */
    public Result forced;
    public RuntimeException failure;

    /** A fresh store that every SimpleFactions owner write now goes to. */
    public static FakeOwnerApi install() {
        FakeOwnerApi api = new FakeOwnerApi();
        VehicleOwnerWrites.use(api);
        return api;
    }

    public static OwnerFields fields(String owner, String... whitelist) {
        return new OwnerFields(owner, List.of(whitelist), false, false, null);
    }

    /** A stored, unloaded vehicle. */
    public FakeOwnerApi stored(String vehicle, String type, String owner, String... whitelist) {
        vehicles.put(vehicle, new OwnerMeta(vehicle, type, owner, Optional.of(fields(owner, whitelist)), Optional.empty()));
        return this;
    }

    /** A loaded vehicle whose stored row matches it. */
    public FakeOwnerApi live(String vehicle, String type, String owner, String... whitelist) {
        OwnerFields fields = fields(owner, whitelist);
        vehicles.put(vehicle, new OwnerMeta(vehicle, type, owner, Optional.of(fields), Optional.of(fields)));
        return this;
    }

    public String owner(String vehicle) {
        OwnerMeta meta = vehicles.get(vehicle);
        return meta == null ? null : meta.currentOwner();
    }

    @Override
    public boolean characters() {
        return characters;
    }

    @Override
    public List<OwnerMeta> list() {
        if (failure != null) throw failure;
        return new ArrayList<>(vehicles.values());
    }

    @Override
    public Optional<OwnerMeta> read(String vehicle) {
        if (failure != null) throw failure;
        return Optional.ofNullable(vehicles.get(vehicle));
    }

    @Override
    public Result write(String vehicle, String owner, List<String> whitelist, Reason reason) {
        writes.add(vehicle + " " + owner + " " + reason);
        if (failure != null) throw failure;
        if (forced != null) return forced;
        if (!characters) return Result.REFUSED_MODE;
        OwnerMeta meta = vehicles.get(vehicle);
        if (meta == null) return Result.NOT_FOUND;
        vehicles.put(vehicle, new OwnerMeta(vehicle, meta.typeId(), meta.hasStoredRow() ? owner : null,
                meta.stored().map(fields -> apply(fields, owner, whitelist, reason)),
                meta.live().map(fields -> apply(fields, owner, whitelist, reason))));
        return Result.OK;
    }

    static OwnerFields apply(OwnerFields fields, String owner, List<String> whitelist, Reason reason) {
        return switch (reason) {
            case MIGRATE -> fields.withOwnership(owner, whitelist == null ? fields.whitelist() : whitelist);
            case RESTAMP -> fields.withOwnership(owner, fields.whitelist());
            case TRANSFER -> fields.withOwnership(owner, List.of());
            case CONSTRUCT -> new OwnerFields(owner, List.of(), true, fields.ticketsEnabled(), fields.ticketId());
            case RELEASE, HANDOVER -> new OwnerFields(owner, List.of(), false, fields.ticketsEnabled(), fields.ticketId());
        };
    }
}
