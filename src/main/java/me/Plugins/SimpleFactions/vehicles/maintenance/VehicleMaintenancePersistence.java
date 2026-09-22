package me.Plugins.SimpleFactions.vehicles.maintenance;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class VehicleMaintenancePersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final VehicleMaintenanceStore store;

    public VehicleMaintenancePersistence(File cacheFolder, VehicleMaintenanceStore store) {
        this.file = new File(cacheFolder, "vehicle_maintenance.json");
        this.store = store;
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new FileReader(file)) {
            Map<String, Long> data = GSON.fromJson(
                    reader,
                    new TypeToken<Map<String, Long>>() {}.getType());
            store.replaceAll(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        Map<String, Long> data = store.snapshot();
        if (data.isEmpty() && !file.exists()) {
            return;
        }
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
