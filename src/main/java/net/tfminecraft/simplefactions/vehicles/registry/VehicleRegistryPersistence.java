package net.tfminecraft.simplefactions.vehicles.registry;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.tfminecraft.simplefactions.database.DurableJson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public final class VehicleRegistryPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String UPKEEP_FILE = "vehicles_upkeep.json";

    private final File file;
    private final PlayerVehicleRegistry registry;
    private boolean saveBlocked;

    public VehicleRegistryPersistence(File cacheFolder, PlayerVehicleRegistry registry) {
        this.file = new File(cacheFolder, "vehicles_registry.json");
        this.registry = registry;
    }

    public void load() {
        Path upkeep = upkeepFile(file.toPath());
        if (!file.exists() && !Files.exists(upkeep)) {
            saveBlocked = false;
            return;
        }
        try {
            List<PlayerVehicleRecord> records = file.exists() ? read(file.toPath(), false) : new ArrayList<>();
            if (Files.exists(upkeep)) {
                java.util.Set<String> listed = new java.util.HashSet<>();
                records.forEach(record -> listed.add(record.getVehicleUuid()));
                // A save interrupted between the two files leaves the main file's row in charge.
                for (PlayerVehicleRecord record : read(upkeep, true)) {
                    if (listed.add(record.getVehicleUuid())) records.add(record);
                }
            }
            registry.replaceAll(records);
            saveBlocked = false;
        } catch (IOException | JsonParseException e) {
            saveBlocked = true;
            throw new IllegalStateException("Cannot load vehicle payer registry", e);
        }
    }

    private static List<PlayerVehicleRecord> read(Path path, boolean upkeepOnly) throws IOException {
        try (Reader reader = new FileReader(path.toFile())) {
            List<VehicleRecordData> data = GSON.fromJson(
                reader,
                new TypeToken<List<VehicleRecordData>>() {}.getType());
            if (data == null) throw new JsonParseException("Vehicle registry must be a list");
            List<PlayerVehicleRecord> records = new ArrayList<>();
            for (VehicleRecordData row : data) {
                PlayerVehicleRecord record = row == null ? null : row.toRecord();
                if (record == null || (upkeepOnly && record.getMode() != OwnershipMode.UPKEEP)) {
                    throw new JsonParseException("Invalid vehicle registry row in " + path.getFileName());
                }
                records.add(record);
            }
            return records;
        }
    }

    /**
     * Upkeep-only rows live in their own file next to the registry, so an older plugin that
     * does not know the mode still loads the registry; it then sees those vehicles as their
     * leader's own.
     */
    static Path upkeepFile(Path registryFile) {
        return registryFile.resolveSibling(UPKEEP_FILE);
    }

    /** Writes a temp file and moves it into place, so a failed save never leaves a half-written registry. */
    public boolean save() {
        if (saveBlocked) return false;
        if (registry.getAll().isEmpty() && !file.exists()) return true;
        try { write(file.toPath(), registry.getAll()); return true; }
        catch (IOException error) { error.printStackTrace(); return false; }
    }

    /**
     * Writes the registry and its upkeep file so that a crash between the files never loses a
     * row: the upkeep file is first written with the rows leaving it still in, then the registry,
     * then the upkeep file exactly. On load the registry's row wins where both files have one.
     */
    public static void write(Path file, java.util.Collection<PlayerVehicleRecord> records) throws IOException {
        List<VehicleRecordData> data = new ArrayList<>();
        List<VehicleRecordData> upkeep = new ArrayList<>();
        java.util.Set<String> staying = new java.util.HashSet<>();
        for (PlayerVehicleRecord record : records) {
            if (record.getMode() == OwnershipMode.UPKEEP) staying.add(record.getVehicleUuid());
            (record.getMode() == OwnershipMode.UPKEEP ? upkeep : data).add(VehicleRecordData.from(record));
        }
        Path upkeepFile = upkeepFile(file);
        boolean hasUpkeepFile = Files.exists(upkeepFile);
        List<VehicleRecordData> leaving = hasUpkeepFile ? leaving(upkeepFile, staying) : List.of();
        if (!leaving.isEmpty()) {
            List<VehicleRecordData> superset = new ArrayList<>(upkeep);
            superset.addAll(leaving);
            DurableJson.write(upkeepFile, superset);
        } else if (!upkeep.isEmpty() || hasUpkeepFile) {
            DurableJson.write(upkeepFile, upkeep);
        }
        DurableJson.write(file, data);
        if (!leaving.isEmpty()) DurableJson.write(upkeepFile, upkeep);
    }

    /** Rows of the saved upkeep file that are no longer upkeep-only; none when it cannot be read. */
    private static List<VehicleRecordData> leaving(Path upkeepFile, java.util.Set<String> staying) {
        try (Reader reader = new FileReader(upkeepFile.toFile())) {
            List<VehicleRecordData> saved = GSON.fromJson(reader, new TypeToken<List<VehicleRecordData>>() {}.getType());
            List<VehicleRecordData> out = new ArrayList<>();
            if (saved == null) return out;
            for (VehicleRecordData row : saved) {
                if (row != null && row.vehicleUuid != null && !staying.contains(row.vehicleUuid)) out.add(row);
            }
            return out;
        } catch (IOException | JsonParseException unreadable) {
            return List.of();
        }
    }

    /**
     * A confirmed personal owner becomes the payer; save before replacing the live row.
     * {@code account} is the account playing the character now, kept for slot limits.
     */
    public static void personal(Path folder, PlayerVehicleRegistry registry, String vehicle, String type, UUID character,
            UUID account) throws IOException {
        if (character == null) throw new IOException("Missing personal vehicle payer");
        replace(folder, registry, vehicle,
                new PlayerVehicleRecord(character, vehicle, type, OwnershipMode.PERSONAL, null, null, account));
    }

    /** Durably puts back a vehicle's earlier row, or removes it when there was none. */
    public static void replace(Path folder, PlayerVehicleRegistry registry, String vehicle, PlayerVehicleRecord record)
            throws IOException {
        List<PlayerVehicleRecord> records = new ArrayList<>(registry.getAll());
        records.removeIf(value -> value.getVehicleUuid().equals(vehicle));
        if (record != null) records.add(record);
        write(folder.resolve("Cache/vehicles_registry.json"), records);
        registry.unregister(vehicle);
        registry.register(record);
    }

    static final class VehicleRecordData {
        String playerUuid;
        String vehicleUuid;
        String vehicleTypeId;
        String mode;
        String installationId;
        String factionId;
        String accountUuid;
        String guildId;

        static VehicleRecordData from(PlayerVehicleRecord record) {
            VehicleRecordData data = new VehicleRecordData();
            data.playerUuid = record.getPlayerUuid() == null ? null : record.getPlayerUuid().toString();
            data.vehicleUuid = record.getVehicleUuid();
            data.vehicleTypeId = record.getVehicleTypeId();
            data.mode = record.getMode().name();
            data.installationId = record.getInstallationId();
            data.factionId = record.getFactionId();
            data.accountUuid = record.getAccountUuid() == null ? null : record.getAccountUuid().toString();
            data.guildId = record.getGuildId();
            return data;
        }

        PlayerVehicleRecord toRecord() {
            if (vehicleUuid == null || vehicleTypeId == null || mode == null) {
                return null;
            }
            try {
                OwnershipMode ownershipMode = OwnershipMode.valueOf(mode);
                return new PlayerVehicleRecord(
                    playerUuid == null ? null : UUID.fromString(playerUuid),
                    vehicleUuid,
                    vehicleTypeId,
                    ownershipMode,
                    installationId,
                    factionId,
                    accountUuid == null ? null : UUID.fromString(accountUuid),
                    guildId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
