package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.Optional;

/**
 * A vehicle's stored and live ownership fields, kept apart.
 *
 * @param vehicle      the vehicle ID in VehicleFramework's lower-case form
 * @param storedColumn the SQLite owner column, or null without a stored row
 */
public record OwnerMeta(String vehicle, String typeId, String storedColumn, Optional<OwnerFields> stored,
        Optional<OwnerFields> live) {

    public OwnerMeta {
        stored = stored == null ? Optional.empty() : stored;
        live = live == null ? Optional.empty() : live;
    }

    public boolean hasStoredRow() {
        return storedColumn != null;
    }

    /** The live fields when loaded, otherwise the stored ones. */
    public Optional<OwnerFields> current() {
        return live.isPresent() ? live : stored;
    }

    public String currentOwner() {
        return current().map(OwnerFields::owner).orElse(null);
    }
}
