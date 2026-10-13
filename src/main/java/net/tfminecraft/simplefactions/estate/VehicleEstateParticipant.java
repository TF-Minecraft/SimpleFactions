package net.tfminecraft.simplefactions.estate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.vehicles.fees.VehicleFeeStore;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerMeta;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerApi;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleOwnerWrites;
import net.tfminecraft.simplefactions.vehicles.owners.VehicleQuarantine;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.EstateParticipant;
import net.tfminecraft.tlibs.character.estate.Estates;
import net.tfminecraft.tlibs.character.estate.Holdings;
import net.tfminecraft.tlibs.character.estate.Outcome;

/**
 * Hands a dead character's personal vehicles to the estate's property recipient (design R8, N3).
 * Each vehicle moves in three durable steps, payer row, VehicleFramework owner, then last owner,
 * and replay follows what the stores show now. No fee is charged and slot limits are not
 * checked; HANDOVER empties the whitelist and turns it off.
 */
final class VehicleEstateParticipant implements EstateParticipant {
    static final String ID = "simplefactions-vehicles";
    /** Reads that found the vehicle gone or unreadable before it is listed for staff. */
    static final int ATTEMPTS = 3;
    static final long ATTEMPT_SPACING_MS = 60L * 60L * 1000L;

    private enum Progress { MOVED, SUPERSEDED, RETRY, BLOCKED }

    private record Step(Progress progress, String reason) {}

    private final Path dataFolder;
    private final PlayerVehicleRegistry registry;
    private final VehicleFeeStore feeStore;
    private final Logger logger;
    /** The last outcome reported per death and revision, so the hourly reconcile reports only changes. */
    private final Map<UUID, String> reported = new HashMap<>();
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    VehicleEstateParticipant(Path dataFolder, PlayerVehicleRegistry registry, VehicleFeeStore feeStore, Logger logger) {
        this.dataFolder = dataFolder;
        this.registry = registry;
        this.feeStore = feeStore;
        this.logger = logger;
    }

    @Override
    public String id() {
        return ID;
    }

    /**
     * Called by the estate provider: at the decision, while the last answer was RETRY, and when
     * staff retry a BLOCKED answer. Only these calls check a blocked vehicle again.
     */
    @Override
    public Outcome settle(EstateDecision decision) {
        return settle(decision, true);
    }

    private Outcome settle(EstateDecision decision, boolean recheckBlocked) {
        if (!Estates.guardHolds(decision)) return Outcome.retry("The estate is not settling this revision");
        Optional<CharacterRef> recipient = decision.property().character();
        // WAITING and FROZEN property stays with the dead owner at this revision, whatever the vehicle stage's state.
        if (recipient.isEmpty()) return Outcome.done();
        if (!SimpleFactionsApi.ready() || !VehicleOwnerWrites.ready()) {
            return Outcome.retry("SimpleFactions vehicles are not ready");
        }
        try {
            return settle(decision.deathId(), decision.testator().characterId(), recipient.get(), recheckBlocked);
        } catch (IOException error) {
            logger.log(Level.WARNING, "Could not save a vehicle estate step for death " + decision.deathId(), error);
            return Outcome.retry("Could not save a vehicle estate step");
        }
    }

    private Outcome settle(UUID deathId, UUID testator, CharacterRef to, boolean recheckBlocked) throws IOException {
        Path file = VehicleEstatePlan.file(dataFolder, deathId);
        VehicleEstatePlan plan = VehicleEstatePlan.load(file, deathId, testator);
        List<OwnerMeta> tagged;
        try {
            tagged = VehicleOwnerWrites.api().list();
        } catch (RuntimeException | LinkageError error) {
            return Outcome.retry("Could not list vehicle owners");
        }
        List<String> retry = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (String vehicle : owned(testator, tagged)) {
            if (plan.entry(vehicle) != null) continue;
            Optional<OwnerMeta> meta = VehicleOwnerWrites.read(vehicle);
            // A tag naming someone else means the payer row is stale; the upkeep reconcile repairs it.
            if (meta.isPresent() && !OwnerTags.names(meta.get().currentOwner(), testator)
                    && !OwnerTags.names(meta.get().currentOwner(), to.characterId())) continue;
            PlayerVehicleRecord row = registry.getByVehicleUuid(vehicle).orElse(null);
            String refusal = gate(vehicle, type(meta, row));
            if (refusal != null) {
                retry.add(vehicle + ": " + refusal);
                continue;
            }
            VehicleEstatePlan.Entry entry = new VehicleEstatePlan.Entry();
            entry.vehicle = vehicle;
            entry.from = testator;
            entry.to = to.characterId();
            entry.toAccount = to.ownerId();
            entry.previousRow = VehicleEstatePlan.Row.of(row);
            entry.step = VehicleEstatePlan.PLANNED;
            plan.entries.add(entry);
            plan.save(file);
        }
        List<String> moved = new ArrayList<>();
        for (VehicleEstatePlan.Entry entry : plan.entries) {
            Step step = step(plan, file, entry, to.characterId(), recheckBlocked);
            switch (step.progress()) {
                case MOVED -> moved.add(entry.vehicle);
                case RETRY -> retry.add(entry.vehicle + ": " + step.reason());
                case BLOCKED -> blocked.add(entry.vehicle + ": " + step.reason());
                case SUPERSEDED -> { }
            }
        }
        if (!retry.isEmpty()) return Outcome.retry(String.join("; ", retry));
        if (!blocked.isEmpty()) return Outcome.blocked(String.join("; ", blocked));
        return Outcome.done(Map.of("vehicles", moved.size()), moved);
    }

    /** Every personal vehicle whose payer row or VehicleFramework owner names the character. */
    private Set<String> owned(UUID character, List<OwnerMeta> tagged) {
        Set<String> vehicles = new LinkedHashSet<>();
        for (PlayerVehicleRecord row : registry.getAll()) {
            if (row.getMode() == OwnershipMode.PERSONAL && character.equals(row.getPlayerUuid())) {
                vehicles.add(row.getVehicleUuid());
            }
        }
        for (OwnerMeta meta : tagged) {
            // Faction vehicles carry their leader's stamp but are never estate property.
            if (OwnerTags.names(meta.currentOwner(), character) && !registry.isFactionOwned(meta.vehicle())) {
                vehicles.add(meta.vehicle());
            }
        }
        return vehicles;
    }

    private Step step(VehicleEstatePlan plan, Path file, VehicleEstatePlan.Entry entry, UUID recipient,
            boolean recheckBlocked) throws IOException {
        if (VehicleEstatePlan.SUPERSEDED.equals(entry.step)) return new Step(Progress.SUPERSEDED, null);
        // Recipients never change once a part names a character; a mismatch is listed rather than reported moved.
        if (!entry.to.equals(recipient)) return new Step(Progress.BLOCKED, "planned for another recipient");
        if (VehicleEstatePlan.LAST_OWNER.equals(entry.step)) return new Step(Progress.MOVED, null);
        if (entry.blocked != null) {
            if (!recheckBlocked) return new Step(Progress.BLOCKED, entry.blocked);
            // Staff asked again: one fresh read decides.
            entry.blocked = null;
            entry.attempts = ATTEMPTS - 1;
            entry.lastAttemptAt = 0;
            plan.save(file);
        }
        return advance(plan, file, entry);
    }

    /** One vehicle, from the state the registry and VehicleFramework show now. */
    private Step advance(VehicleEstatePlan plan, Path file, VehicleEstatePlan.Entry entry) throws IOException {
        Optional<OwnerMeta> meta = VehicleOwnerWrites.read(entry.vehicle);
        String tag = meta.map(OwnerMeta::currentOwner).orElse(null);
        if (meta.isPresent() && !OwnerTags.names(tag, entry.from) && !OwnerTags.names(tag, entry.to)) {
            // Handed on since: never rewritten.
            entry.step = VehicleEstatePlan.SUPERSEDED;
            plan.save(file);
            return new Step(Progress.SUPERSEDED, null);
        }
        PlayerVehicleRecord row = registry.getByVehicleUuid(entry.vehicle).orElse(null);
        if (!payer(row, entry.to)) {
            String type = type(meta, row);
            String refusal = gate(entry.vehicle, type);
            if (refusal != null) return new Step(Progress.RETRY, refusal);
            if (!VehiclePayments.current().personal(entry.vehicle, type, entry.to, entry.toAccount)) {
                return new Step(Progress.RETRY, "the payer row could not be saved");
            }
            entry.step = VehicleEstatePlan.ROW;
            plan.save(file);
        }
        if (meta.isEmpty()) return unreadable(plan, file, entry, "the vehicle is gone or cannot be read");
        if (OwnerTags.names(tag, entry.from)) {
            VehicleOwnerApi.Result result = VehicleOwnerWrites.write(entry.vehicle, OwnerTags.character(entry.to),
                    VehicleOwnerApi.Reason.HANDOVER);
            if (result == VehicleOwnerApi.Result.NOT_FOUND) return unreadable(plan, file, entry, "the vehicle is gone");
            if (result != VehicleOwnerApi.Result.OK) {
                // The recipient stops being the payer until the owner change succeeds.
                if (!VehiclePayments.current().replaceRow(entry.vehicle,
                        entry.previousRow == null ? null : entry.previousRow.toRecord(entry.vehicle))) {
                    return new Step(Progress.RETRY, "the previous payer row could not be restored");
                }
                entry.step = VehicleEstatePlan.PLANNED;
                plan.save(file);
                return new Step(Progress.RETRY, "the owner change failed (" + result + ")");
            }
            entry.step = VehicleEstatePlan.DONE;
            plan.save(file);
        }
        feeStore.setLastOwner(entry.vehicle, Members.key(entry.to));
        feeStore.saveDurably();
        entry.step = VehicleEstatePlan.LAST_OWNER;
        plan.save(file);
        return new Step(Progress.MOVED, null);
    }

    /**
     * Gone or unreadable: retried, then listed for staff. Attempts count at most once an hour,
     * so a briefly unreadable vehicle is not listed by calls close together. The payer row is not put back.
     */
    private Step unreadable(VehicleEstatePlan plan, Path file, VehicleEstatePlan.Entry entry, String reason)
            throws IOException {
        long now = clock.getAsLong();
        if (entry.lastAttemptAt != 0 && now - entry.lastAttemptAt < ATTEMPT_SPACING_MS) {
            return new Step(Progress.RETRY, reason);
        }
        entry.attempts++;
        entry.lastAttemptAt = now;
        if (entry.attempts >= ATTEMPTS) entry.blocked = reason;
        plan.save(file);
        return new Step(entry.blocked != null ? Progress.BLOCKED : Progress.RETRY, reason);
    }

    private static boolean payer(PlayerVehicleRecord row, UUID character) {
        return row != null && row.getMode() == OwnershipMode.PERSONAL && character.equals(row.getPlayerUuid());
    }

    private static String type(Optional<OwnerMeta> meta, PlayerVehicleRecord row) {
        String type = meta.map(OwnerMeta::typeId).orElse(null);
        return type != null || row == null ? type : row.getVehicleTypeId();
    }

    /**
     * The estate gate (M5, P3, P4): no owner tag, slot or battle-lock check, since the owner is
     * dead and personal rows carry no faction. Null when the vehicle may move now.
     */
    private String gate(String vehicle, String type) {
        if (type == null || !VehiclesConfigLoader.isKnownType(type)) return "unknown vehicle type";
        if (registry.isFactionOwned(vehicle)) return "a faction vehicle";
        VehiclePayments payments = VehiclePayments.current();
        if (payments == null || !payments.ready() || payments.blockedVehicle(vehicle)) {
            return "a vehicle payment awaits recovery or staff";
        }
        if (VehicleQuarantine.held(vehicle)) return "held back for staff";
        return null;
    }

    @Override
    public Holdings describe(UUID characterId) {
        List<OwnerMeta> tagged = listedOrNone();
        Set<String> vehicles = owned(characterId, tagged);
        List<String> labels = new ArrayList<>();
        for (String vehicle : vehicles) {
            String type = registry.getByVehicleUuid(vehicle).map(PlayerVehicleRecord::getVehicleTypeId)
                    .orElseGet(() -> tagged.stream().filter(meta -> meta.vehicle().equals(vehicle))
                            .map(OwnerMeta::typeId).filter(Objects::nonNull).findFirst().orElse("vehicle"));
            labels.add(type + " " + vehicle.substring(0, Math.min(8, vehicle.length())));
        }
        return new Holdings(Map.of("vehicles", vehicles.size()), labels);
    }

    private static List<OwnerMeta> listedOrNone() {
        try {
            VehicleOwnerApi api = VehicleOwnerWrites.api();
            return api == null ? List.of() : api.list();
        } catch (RuntimeException | LinkageError error) {
            return List.of();
        }
    }

    /**
     * Boot and hourly: every decided estate whose guard holds is settled again, which moves
     * vehicles that appeared since (a build that finished for the dead payer).
     */
    void reconcile() {
        List<EstateDecision> decided;
        try {
            if (!SimpleFactionsApi.ready()) return;
            decided = Estates.decided();
        } catch (RuntimeException | LinkageError error) {
            logger.log(Level.WARNING, "Vehicle estate reconcile failed; it runs again within the hour", error);
            return;
        }
        Map<UUID, String> seen = new LinkedHashMap<>();
        for (EstateDecision decision : decided) {
            // One estate failing must not hold back the others for an hour.
            try {
                if (!Estates.guardHolds(decision)) continue;
                Outcome outcome = settle(decision, false);
                String key = decision.revision() + ":" + outcome;
                seen.put(decision.deathId(), key);
                if (key.equals(reported.get(decision.deathId()))) continue;
                if (Estates.report(decision.deathId(), ID, decision.revision(), outcome)) {
                    reported.put(decision.deathId(), key);
                }
            } catch (RuntimeException | LinkageError error) {
                logger.log(Level.WARNING, "Vehicle estate reconcile failed for death " + decision.deathId()
                        + "; it runs again within the hour", error);
            }
        }
        reported.keySet().retainAll(seen.keySet());
    }
}
