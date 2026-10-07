package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.tfminecraft.simplefactions.database.JsonUtil;

public final class VehicleMaintenancePersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final VehicleMaintenanceStore store;
    private boolean saveBlocked;

    public VehicleMaintenancePersistence(File cacheFolder, VehicleMaintenanceStore store) {
        this.file = new File(cacheFolder, "vehicle_maintenance.json");
        this.store = store;
    }

    public void load() {
        if (!file.exists()) {
            saveBlocked = false;
            return;
        }
        try (Reader reader = new FileReader(file)) {
            Map<String, Long> data = GSON.fromJson(
                    reader,
                    new TypeToken<Map<String, Long>>() {}.getType());
            if (data == null) throw new JsonParseException("Maintenance data must be an object");
            store.replaceAll(data);
            saveBlocked = false;
        } catch (IOException | JsonParseException e) {
            saveBlocked = true;
            e.printStackTrace();
        }
    }

    public void save() {
        if (saveBlocked) return;
        Map<String, Long> data = store.snapshot();
        if (data.isEmpty() && !file.exists()) {
            return;
        }
        try {
            JsonUtil.writeJsonAtomic(file, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
