package me.Plugins.SimpleFactions.vehicles.registry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class VehicleRegistryPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final PlayerVehicleRegistry registry;

    public VehicleRegistryPersistence(File cacheFolder, PlayerVehicleRegistry registry) {
        this.file = new File(cacheFolder, "vehicles_registry.json");
        this.registry = registry;
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new FileReader(file)) {
            List<VehicleRecordData> data = GSON.fromJson(
                reader,
                new TypeToken<List<VehicleRecordData>>() {}.getType());
            if (data == null) {
                return;
            }
            List<PlayerVehicleRecord> records = new ArrayList<>();
            for (VehicleRecordData row : data) {
                PlayerVehicleRecord record = row.toRecord();
                if (record != null && record.getMode() == OwnershipMode.INSTALLATION) {
                    records.add(record);
                }
            }
            registry.replaceAll(records);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        List<VehicleRecordData> data = new ArrayList<>();
        for (PlayerVehicleRecord record : registry.getAll()) {
            data.add(VehicleRecordData.from(record));
        }
        if (data.isEmpty() && !file.exists()) {
            return;
        }
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static final class VehicleRecordData {
        String playerUuid;
        String vehicleUuid;
        String vehicleTypeId;
        String mode;
        String installationId;

        static VehicleRecordData from(PlayerVehicleRecord record) {
            VehicleRecordData data = new VehicleRecordData();
            data.playerUuid = record.getPlayerUuid() == null ? null : record.getPlayerUuid().toString();
            data.vehicleUuid = record.getVehicleUuid();
            data.vehicleTypeId = record.getVehicleTypeId();
            data.mode = record.getMode().name();
            data.installationId = record.getInstallationId();
            return data;
        }

        PlayerVehicleRecord toRecord() {
            if (playerUuid == null || vehicleUuid == null || vehicleTypeId == null || mode == null) {
                return null;
            }
            try {
                OwnershipMode ownershipMode = OwnershipMode.valueOf(mode);
                return new PlayerVehicleRecord(
                    UUID.fromString(playerUuid),
                    vehicleUuid,
                    vehicleTypeId,
                    ownershipMode,
                    installationId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
