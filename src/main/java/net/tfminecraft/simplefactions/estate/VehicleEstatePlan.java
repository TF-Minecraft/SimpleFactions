package net.tfminecraft.simplefactions.estate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;

/**
 * One estate's vehicle moves, {@code vehicles/estates/<deathId>.json}, written durably before
 * each store changes. Replay reads the vehicles' state, not the step, so the step is a record
 * of progress; {@code previousRow} is what staff restore when rolling back by hand.
 */
final class VehicleEstatePlan {
    static final String PLANNED = "planned";
    static final String ROW = "row";
    static final String DONE = "done";
    static final String LAST_OWNER = "lastOwner";
    static final String SUPERSEDED = "superseded";

    UUID deathId;
    UUID testator;
    List<Entry> entries = new ArrayList<>();

    static final class Entry {
        String vehicle;
        UUID from;
        UUID to;
        UUID toAccount;
        Row previousRow;
        String step;
        /** Reads that found the vehicle gone or unreadable, counted at most once an hour. */
        int attempts;
        long lastAttemptAt;
        /** Set when listed for staff; only a call from the estate provider (staff retry) checks it again. */
        String blocked;
    }

    /** A registry row as it was before the move; null fields as stored. */
    record Row(UUID payer, String type, String mode, String installation, String faction, UUID account) {
        static Row of(PlayerVehicleRecord record) {
            return record == null ? null : new Row(record.getPlayerUuid(), record.getVehicleTypeId(),
                    record.getMode().name(), record.getInstallationId(), record.getFactionId(), record.getAccountUuid());
        }

        PlayerVehicleRecord toRecord(String vehicle) {
            return new PlayerVehicleRecord(payer, vehicle, type, OwnershipMode.valueOf(mode), installation, faction,
                    account);
        }
    }

    static Path file(Path dataFolder, UUID deathId) {
        return dataFolder.resolve("vehicles/estates/" + deathId + ".json");
    }

    /** The saved plan, or a new empty one; a plan for another death or character is refused. */
    static VehicleEstatePlan load(Path file, UUID deathId, UUID testator) throws IOException {
        if (!Files.exists(file)) {
            VehicleEstatePlan plan = new VehicleEstatePlan();
            plan.deathId = deathId;
            plan.testator = testator;
            return plan;
        }
        VehicleEstatePlan plan = DurableJson.read(file, VehicleEstatePlan.class);
        if (!deathId.equals(plan.deathId) || !testator.equals(plan.testator) || plan.entries == null) {
            throw new IOException("Vehicle estate plan does not match its death: " + file);
        }
        for (Entry entry : plan.entries) {
            if (entry == null || entry.vehicle == null || entry.from == null || entry.to == null || entry.step == null) {
                throw new IOException("Invalid vehicle estate plan entry in " + file);
            }
        }
        return plan;
    }

    void save(Path file) throws IOException {
        DurableJson.write(file, this);
    }

    Entry entry(String vehicle) {
        return entries.stream().filter(entry -> entry.vehicle.equals(vehicle)).findFirst().orElse(null);
    }
}
