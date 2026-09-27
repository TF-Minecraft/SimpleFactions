package net.tfminecraft.simplefactions.vehicles.fees;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * State the vehicle fees need across restarts: who last owned each personal vehicle, so
 * releasing one for a friend to claim is charged as a transfer, and the registration fees
 * paid for constructions still in progress, so a cancelled build can be refunded.
 */
public final class VehicleFeeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** A registration fee paid for a construction that has not finished yet. */
    public record PaidBuild(UUID payerUuid, String factionId, double amount) {}

    private final Map<String, String> lastOwners = new HashMap<>();
    private final Map<String, PaidBuild> paidBuilds = new HashMap<>();
    private File file;

    public String getLastOwner(String vehicleUuid) {
        return vehicleUuid == null ? null : lastOwners.get(vehicleUuid);
    }

    public void setLastOwner(String vehicleUuid, String playerName) {
        if (vehicleUuid == null || playerName == null || playerName.isBlank()) {
            return;
        }
        lastOwners.put(vehicleUuid, playerName);
    }

    public void forgetVehicle(String vehicleUuid) {
        if (vehicleUuid != null) {
            lastOwners.remove(vehicleUuid);
        }
    }

    public void putPaidBuild(String stationKey, PaidBuild build) {
        if (stationKey != null && build != null) {
            paidBuilds.put(stationKey, build);
        }
    }

    public PaidBuild takePaidBuild(String stationKey) {
        return stationKey == null ? null : paidBuilds.remove(stationKey);
    }

    // Persistence

    public void bind(File cacheFolder) {
        this.file = new File(cacheFolder, "vehicle_fees.json");
    }

    public void load() {
        if (file == null || !file.exists()) {
            return;
        }
        try (Reader reader = new FileReader(file)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) {
                return;
            }
            lastOwners.clear();
            if (data.lastOwners != null) {
                lastOwners.putAll(data.lastOwners);
            }
            paidBuilds.clear();
            if (data.paidBuilds != null) {
                for (Map.Entry<String, BuildData> entry : data.paidBuilds.entrySet()) {
                    PaidBuild build = entry.getValue() == null ? null : entry.getValue().toBuild();
                    if (build != null) {
                        paidBuilds.put(entry.getKey(), build);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        if (file == null || (lastOwners.isEmpty() && paidBuilds.isEmpty() && !file.exists())) {
            return;
        }
        Data data = new Data();
        data.lastOwners = new HashMap<>(lastOwners);
        data.paidBuilds = new HashMap<>();
        for (Map.Entry<String, PaidBuild> entry : paidBuilds.entrySet()) {
            data.paidBuilds.put(entry.getKey(), BuildData.from(entry.getValue()));
        }
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final class Data {
        Map<String, String> lastOwners;
        Map<String, BuildData> paidBuilds;
    }

    private static final class BuildData {
        String payerUuid;
        String factionId;
        double amount;

        static BuildData from(PaidBuild build) {
            BuildData data = new BuildData();
            data.payerUuid = build.payerUuid().toString();
            data.factionId = build.factionId();
            data.amount = build.amount();
            return data;
        }

        PaidBuild toBuild() {
            if (payerUuid == null || amount <= 0.0) {
                return null;
            }
            try {
                return new PaidBuild(UUID.fromString(payerUuid), factionId, amount);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
