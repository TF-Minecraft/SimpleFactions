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
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public final class VehicleRegistryPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String UPKEEP_FILE = "vehicles_upkeep.json";
    private static final java.lang.reflect.Type ROWS = new TypeToken<List<VehicleRecordData>>() {}.getType();

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
            List<VehicleRecordData> saved = file.exists() ? readRows(file.toPath()) : new ArrayList<>();
            List<PlayerVehicleRecord> records = records(saved, false, file.toPath());
            if (Files.exists(upkeep)) {
                java.util.Set<String> listed = new java.util.HashSet<>();
                records.forEach(record -> listed.add(record.getVehicleUuid()));
                // Where both files name a vehicle (the registry was changed by an older plugin), the registry wins.
                for (PlayerVehicleRecord record : records(committed(readState(upkeep), digest(saved)), true, upkeep)) {
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

    private static List<VehicleRecordData> readRows(Path path) throws IOException {
        try (Reader reader = new FileReader(path.toFile())) {
            List<VehicleRecordData> data = GSON.fromJson(reader, ROWS);
            if (data == null) throw new JsonParseException("Vehicle registry must be a list");
            return data;
        }
    }

    private static List<PlayerVehicleRecord> records(List<VehicleRecordData> data, boolean upkeepOnly, Path path) {
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

    /**
     * The upkeep file: the upkeep-only rows, the digest of the registry rows they were written
     * with, and (while that registry write is pending) the rows the registry had before.
     */
    static final class UpkeepState {
        String registry;
        List<VehicleRecordData> rows = new ArrayList<>();
        UpkeepState previous;
    }

    private static UpkeepState readState(Path path) throws IOException {
        try (Reader reader = new FileReader(path.toFile())) {
            JsonElement json = JsonParser.parseReader(reader);
            UpkeepState state;
            if (json.isJsonArray()) {
                // A plain list of rows: written before the upkeep file named its registry.
                state = new UpkeepState();
                state.rows = GSON.fromJson(json, ROWS);
            } else {
                state = GSON.fromJson(json, UpkeepState.class);
            }
            if (state == null || state.rows == null) throw new JsonParseException("Invalid vehicle upkeep file");
            return state;
        }
    }

    /** The rows that belong to the registry as saved: those written for it, else those it still had before. */
    static List<VehicleRecordData> committed(UpkeepState state, String registryDigest) {
        if (state.previous != null && state.previous.rows != null && !registryDigest.equals(state.registry)
                && registryDigest.equals(state.previous.registry)) {
            return state.previous.rows;
        }
        return state.rows;
    }

    /** Identifies a list of registry rows by content (a 128-bit name UUID), however the file was formatted. */
    static String digest(List<VehicleRecordData> rows) {
        return UUID.nameUUIDFromBytes(GSON.toJson(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
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
     * Saves both files with one commit point, so a save that reports failure changed nothing
     * that a reload would see. When the registry rows change, the registry write commits: the
     * upkeep file is written first with the new rows and the digest of the new registry, and
     * keeps the rows the saved registry had; a reload picks the set whose digest matches the
     * registry on disk. When only upkeep rows change, the upkeep write commits on its own.
     */
    public static void write(Path file, java.util.Collection<PlayerVehicleRecord> records) throws IOException {
        List<VehicleRecordData> data = new ArrayList<>();
        List<VehicleRecordData> upkeep = new ArrayList<>();
        for (PlayerVehicleRecord record : records) {
            (record.getMode() == OwnershipMode.UPKEEP ? upkeep : data).add(VehicleRecordData.from(record));
        }
        Path upkeepFile = upkeepFile(file);
        String digest = digest(data);
        String saved = digest(Files.exists(file) ? quietly(() -> readRows(file), List.of()) : List.of());
        boolean registryChanges = !digest.equals(saved);
        // Same rows as before: writing the file commits nothing, so it may go first.
        if (!registryChanges && !Files.exists(file)) DurableJson.write(file, data);
        if (!upkeep.isEmpty() || Files.exists(upkeepFile)) {
            UpkeepState state = new UpkeepState();
            state.registry = digest;
            state.rows = upkeep;
            if (registryChanges) {
                UpkeepState before = new UpkeepState();
                before.registry = saved;
                before.rows = Files.exists(upkeepFile)
                        ? quietly(() -> committed(readState(upkeepFile), saved), List.of()) : List.of();
                state.previous = before;
            }
            if (registryChanges) {
                DurableJson.write(upkeepFile, state);
            } else {
                commit(upkeepFile, state, () -> {
                    UpkeepState written = readState(upkeepFile);
                    return digest.equals(written.registry) && digest(upkeep).equals(digest(written.rows));
                });
            }
        }
        if (registryChanges) commit(file, data, () -> digest.equals(digest(readRows(file))));
    }

    /**
     * Writes the file whose rename is the commit point. Once the rename has landed the save is
     * done, so a failure after it (syncing the folder entry) is only logged: reporting it would
     * make callers roll memory back while the files already hold the new rows.
     */
    private static void commit(Path target, Object content, Read<Boolean> landed) throws IOException {
        try {
            DurableJson.write(target, content);
        } catch (IOException error) {
            if (!quietly(landed, false)) throw error;
            java.util.logging.Logger.getLogger("SimpleFactions").warning("Saved " + target.getFileName()
                    + " but could not sync its folder (" + error.getMessage() + "); a power loss now could undo this save");
        }
    }

    private interface Read<T> {
        T get() throws IOException;
    }

    /** Unreadable saved files count as empty here; {@link #load()} reports them. */
    private static <T> T quietly(Read<T> read, T fallback) {
        try {
            return read.get();
        } catch (IOException | RuntimeException unreadable) {
            return fallback;
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
