package net.tfminecraft.simplefactions.vehicles.owners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

import net.tfminecraft.simplefactions.vehicles.berth.VehicleSlotGuard;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;

/**
 * Keeps VehicleFramework's owners and the payer registry in step, at boot and daily. A
 * character-owned vehicle without a row gets a personal row, so its owner pays upkeep;
 * everything else that disagrees is listed for staff and left alone.
 */
public final class VehicleUpkeepReconcile {
    /** Writes a personal row; false when it could not be saved. */
    public interface Rows {
        boolean personal(String vehicle, String type, UUID character, UUID account);
    }

    private final VehicleOwnerApi api;
    private final PlayerVehicleRegistry registry;
    private final Rows rows;
    private final Predicate<UUID> isLeader;
    private final Function<UUID, Optional<UUID>> accountOf;
    private final Logger logger;

    public VehicleUpkeepReconcile(VehicleOwnerApi api, PlayerVehicleRegistry registry, Rows rows,
            Predicate<UUID> isLeader, Function<UUID, Optional<UUID>> accountOf, Logger logger) {
        this.api = api;
        this.registry = registry;
        this.rows = rows;
        this.isLeader = isLeader;
        this.accountOf = accountOf;
        this.logger = logger;
    }

    /** Runs once; returns what staff should look at. Throws when VehicleFramework cannot be read. */
    public List<VehicleQuarantine.Entry> run() {
        List<VehicleQuarantine.Entry> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OwnerMeta meta : api.list()) {
            seen.add(meta.vehicle());
            String owner = meta.currentOwner();
            PlayerVehicleRecord record = registry.getByVehicleUuid(meta.vehicle()).orElse(null);
            UUID character = OwnerTags.characterId(owner).orElse(null);
            if (record == null) {
                if (character != null) unregistered(meta, owner, character, findings);
            } else if (record.getMode() == OwnershipMode.PERSONAL) {
                if (!OwnerTags.names(owner, record.getPlayerUuid())) {
                    findings.add(finding(meta.vehicle(), owner, "personal registry row names character "
                            + record.getPlayerUuid() + ", the vehicle names another owner"));
                }
            } else if (!LeaderStamps.expected(record).equals(owner)) {
                findings.add(finding(meta.vehicle(), owner,
                        "faction vehicle should carry " + LeaderStamps.expected(record)));
            }
        }
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (!seen.contains(record.getVehicleUuid())) {
                findings.add(finding(record.getVehicleUuid(), null, "registry row has no vehicle"));
            } else if (record.getMode() == OwnershipMode.PERSONAL && VehicleSlotGuard.accountOf(record) == null) {
                findings.add(finding(record.getVehicleUuid(), null,
                        "personal row's account is unknown, so it counts against every claimer"));
            }
        }
        return findings;
    }

    private void unregistered(OwnerMeta meta, String owner, UUID character, List<VehicleQuarantine.Entry> findings) {
        if (isLeader.test(character)) {
            findings.add(finding(meta.vehicle(), owner, "owner is a faction leader but the vehicle has no registry row"));
            return;
        }
        UUID account = accountOf.apply(character).orElse(null);
        if (account == null) {
            findings.add(finding(meta.vehicle(), owner, "owner character is unknown and the vehicle has no registry row"));
            return;
        }
        if (rows.personal(meta.vehicle(), meta.typeId(), character, account)) {
            logger.info("Vehicle " + meta.vehicle() + " (" + meta.typeId() + ") had no payer row; "
                    + "its owner character " + character + " now pays its upkeep");
        } else {
            findings.add(finding(meta.vehicle(), owner, "could not save a payer row for its owner"));
        }
    }

    private VehicleQuarantine.Entry finding(String vehicle, String owner, String reason) {
        logger.warning("Vehicle " + vehicle + ": " + reason);
        return new VehicleQuarantine.Entry(vehicle, owner, "reconcile: " + reason);
    }
}
