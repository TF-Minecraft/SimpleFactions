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

    private final File file;
    private final PlayerVehicleRegistry registry;
    private boolean saveBlocked;

    public VehicleRegistryPersistence(File cacheFolder, PlayerVehicleRegistry registry) {
        this.file = new File(cacheFolder, "vehicles_registry.json");
        this.registry = registry;
    }

    public void load() {
        if (!file.exists()) {
            saveBlocked = false;
            return;
        }
        try (Reader reader = new FileReader(file)) {
            List<VehicleRecordData> data = GSON.fromJson(
                reader,
                new TypeToken<List<VehicleRecordData>>() {}.getType());
            if (data == null) throw new JsonParseException("Vehicle registry must be a list");
            List<PlayerVehicleRecord> records = new ArrayList<>();
            for (VehicleRecordData row : data) {
                PlayerVehicleRecord record = row == null ? null : row.toRecord();
                if (record == null) throw new JsonParseException("Invalid vehicle registry row");
                records.add(record);
            }
            registry.replaceAll(records);
            saveBlocked = false;
        } catch (IOException | JsonParseException e) {
            saveBlocked = true;
            throw new IllegalStateException("Cannot load vehicle payer registry", e);
        }
    }

    /** Writes a temp file and moves it into place, so a failed save never leaves a half-written registry. */
    public boolean save() {
        if (saveBlocked) return false;
        if (registry.getAll().isEmpty() && !file.exists()) return true;
        try { write(file.toPath(), registry.getAll()); return true; }
        catch (IOException error) { error.printStackTrace(); return false; }
    }

    public static void write(Path file, java.util.Collection<PlayerVehicleRecord> records) throws IOException {
        List<VehicleRecordData> data = new ArrayList<>();
        for (PlayerVehicleRecord record : records) data.add(VehicleRecordData.from(record));
        DurableJson.write(file, data);
    }

    /** A confirmed personal owner becomes the payer; save before replacing the live row. */
    public static void personal(Path folder, PlayerVehicleRegistry registry, String vehicle, String type, UUID character) throws IOException {
        if (character == null) throw new IOException("Missing personal vehicle payer");
        PlayerVehicleRecord record = new PlayerVehicleRecord(character, vehicle, type, OwnershipMode.PERSONAL, null);
        List<PlayerVehicleRecord> records = new ArrayList<>(registry.getAll());
        records.removeIf(value -> value.getVehicleUuid().equals(vehicle));
        records.add(record);
        write(folder.resolve("Cache/vehicles_registry.json"), records);
        registry.register(record);
    }

    static final class VehicleRecordData {
        String playerUuid;
        String vehicleUuid;
        String vehicleTypeId;
        String mode;
        String installationId;
        String factionId;

        static VehicleRecordData from(PlayerVehicleRecord record) {
            VehicleRecordData data = new VehicleRecordData();
            data.playerUuid = record.getPlayerUuid() == null ? null : record.getPlayerUuid().toString();
            data.vehicleUuid = record.getVehicleUuid();
            data.vehicleTypeId = record.getVehicleTypeId();
            data.mode = record.getMode().name();
            data.installationId = record.getInstallationId();
            data.factionId = record.getFactionId();
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
                    factionId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
