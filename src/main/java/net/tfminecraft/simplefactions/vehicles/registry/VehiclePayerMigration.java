package net.tfminecraft.simplefactions.vehicles.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.vehicles.owners.OwnerTags;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.Characters;
import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.data.StoredVehicleMeta;

/** One-time bridge from persisted account ownership to reviewed heirs; runtime never repeats this lookup. */
public final class VehiclePayerMigration {
    private record Row(String vehicle, String type, String originalOwner, UUID account, UUID character) {}
    private static final class Plan {
        List<Row> rows = new ArrayList<>();
        boolean completed;
    }
    private VehiclePayerMigration() {}

    /** The accounts the stage-5 payer plan resolved owner names to, by vehicle; empty without a plan. */
    public static java.util.Map<String, UUID> accounts(Path folder) throws IOException {
        Path file = folder.resolve("identity/vehicle-payers.json");
        java.util.Map<String, UUID> out = new java.util.HashMap<>();
        if (!Files.exists(file)) return out;
        Plan plan = DurableJson.read(file, Plan.class);
        if (plan.rows != null) {
            for (Row row : plan.rows) if (row != null && row.vehicle() != null && row.account() != null) out.put(row.vehicle(), row.account());
        }
        return out;
    }

    public static void startup(Path folder, PlayerVehicleRegistry registry) throws IOException {
        Path file = folder.resolve("identity/vehicle-payers.json");
        if (Files.exists(file)) { replay(folder, file, registry, DurableJson.read(file, Plan.class)); return; }
        if (!Characters.legacyHeirsReady()) throw new IOException("Legacy vehicle heirs are not ready");
        var repository = VehicleFramework.getVehicleRepository();
        if (repository == null) throw new IOException("Vehicle persistence is unavailable for payer migration");
        List<StoredVehicleMeta> source;
        try { source = repository.listPlayerOwnedMeta(); }
        catch (RuntimeException error) { throw new IOException("Could not read all persisted vehicle owners", error); }
        migrate(folder, registry, source, VehiclePayerMigration::account,
                id -> Characters.legacyHeir(id).filter(ref -> ref.ownerId().equals(id)).orElse(null));
    }

    static void migrate(Path folder, PlayerVehicleRegistry registry, List<StoredVehicleMeta> source,
            Function<String, UUID> accounts, Function<UUID, CharacterRef> heirs) throws IOException {
        Path file = folder.resolve("identity/vehicle-payers.json");
        if (Files.exists(file)) { replay(folder, file, registry, DurableJson.read(file, Plan.class)); return; }
        Plan plan = new Plan();
        for (var vehicle : source) {
            String name = OwnerTags.legacyName(vehicle.getOwner());
            // Character owners are already past this bridge; VehicleFramework 2.13 lists them too.
            if (name == null || registry.getByVehicleUuid(vehicle.getUuid()).isPresent()) continue;
            UUID owner = accounts.apply(name);
            CharacterRef heir = owner == null ? null : heirs.apply(owner);
            UUID character = heir != null && heir.ownerId().equals(owner) ? heir.characterId() : null;
            plan.rows.add(new Row(vehicle.getUuid(), vehicle.getTypeId(), vehicle.getOwner(), owner, character));
        }
        DurableJson.write(file, plan);
        replay(folder, file, registry, plan);
    }

    private static void replay(Path folder, Path file, PlayerVehicleRegistry registry, Plan plan) throws IOException {
        if (plan == null || plan.rows == null) throw new IOException("Invalid vehicle payer migration");
        var seen = new HashSet<String>();
        for (Row row : plan.rows) {
            if (row == null || row.vehicle() == null || row.type() == null || row.originalOwner() == null || !seen.add(row.vehicle()))
                throw new IOException("Invalid vehicle payer migration row");
        }
        if (plan.completed) return;
        var records = new ArrayList<>(registry.getAll());
        for (Row row : plan.rows) {
            if (registry.getByVehicleUuid(row.vehicle()).isEmpty())
                records.add(new PlayerVehicleRecord(row.character(), row.vehicle(), row.type(), OwnershipMode.PERSONAL, null));
        }
        VehicleRegistryPersistence.write(folder.resolve("Cache/vehicles_registry.json"), records);
        records.forEach(registry::register);
        plan.completed = true;
        DurableJson.write(file, plan);
    }

    private static UUID account(String value) {
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return VehicleOwnershipQueries.resolvePlayerUuid(value); }
    }
}
