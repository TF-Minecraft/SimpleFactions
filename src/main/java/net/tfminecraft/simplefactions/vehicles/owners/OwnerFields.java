package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One copy of a vehicle's ownership fields, as VehicleFramework stores them. */
public record OwnerFields(String owner, List<String> whitelist, boolean whitelisted, boolean ticketsEnabled,
        String ticketId) {

    public OwnerFields {
        owner = owner == null || owner.isBlank() ? OwnerTags.NONE : owner;
        List<String> entries = new ArrayList<>();
        if (whitelist != null) whitelist.stream().filter(Objects::nonNull).forEach(entries::add);
        whitelist = List.copyOf(entries);
    }

    /** The fields a migration write changes: owner, whitelist entries and the whitelist flag. */
    public boolean sameOwnership(OwnerFields other) {
        return other != null && owner.equals(other.owner) && whitelist.equals(other.whitelist)
                && whitelisted == other.whitelisted;
    }

    public OwnerFields withOwnership(String newOwner, List<String> newWhitelist) {
        return new OwnerFields(newOwner, newWhitelist, whitelisted, ticketsEnabled, ticketId);
    }
}
