package net.tfminecraft.simplefactions.vehicles.fees;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.tfminecraft.simplefactions.database.DurableJson;
import net.tfminecraft.simplefactions.vehicles.fees.VehiclePayments.Receipt;

/**
 * State the vehicle fees need across restarts: who last owned each personal vehicle, so
 * releasing one for a friend to claim is charged as a transfer, and the registration fees
 * paid for constructions still in progress, so a cancelled build can be refunded.
 */
public final class VehicleFeeStore {

    /** A registration fee paid for a construction that has not finished yet. */
    public record PaidBuild(UUID payerUuid, String factionId, double amount) {}

    private final Map<String, String> lastOwners = new HashMap<>();
    private final Map<String, PaidBuild> paidBuilds = new HashMap<>();
    private final Map<String, String> buildOwners = new HashMap<>();
    public String buildOwner(String station) { return buildOwners.get(station); }
    public void beginBuild(String station, String character) { buildOwners.put(station, character); }
    public void endBuild(String station) { buildOwners.remove(station); }

    private final Map<String, Receipt> receipts = new HashMap<>();
    private File file;
    private boolean loadFailed;

    public Receipt receipt(String scope) { return receipts.get(scope); }
    public void putReceipt(String scope, Receipt receipt) { receipts.put(scope, receipt); }
    public void removeReceipt(String scope) { receipts.remove(scope); }
    public Map<String, PaidBuild> pendingBuilds() { return Map.copyOf(paidBuilds); }
    public PaidBuild getPaidBuild(String stationKey) { return paidBuilds.get(stationKey); }

    public String getLastOwner(String vehicleUuid) {
        return vehicleUuid == null ? null : lastOwners.get(vehicleUuid);
    }

    /** {@code owner} is the member key of the character that last owned the vehicle. */
    public void setLastOwner(String vehicleUuid, String owner) {
        if (vehicleUuid == null || owner == null || owner.isBlank()) {
            return;
        }
        lastOwners.put(vehicleUuid, owner);
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
        if (file == null || !file.exists()) return;
        try {
            Data data = DurableJson.read(file.toPath(), Data.class);
            if (data == null) throw new IOException("Vehicle fee state must be an object");
            Map<String, PaidBuild> loadedBuilds = new HashMap<>();
            if (data.paidBuilds != null) {
                for (var entry : data.paidBuilds.entrySet()) {
                    PaidBuild build = entry.getValue() == null ? null : entry.getValue().toBuild();
                    if (build == null) throw new IOException("Invalid pending vehicle build: " + entry.getKey());
                    loadedBuilds.put(entry.getKey(), build);
                }
            }
            Map<String, Receipt> loadedReceipts = new HashMap<>();
            if (data.receipts != null) {
                for (var entry : data.receipts.entrySet()) {
                    Receipt receipt = entry.getValue();
                    if (receipt == null || receipt.id() == null || receipt.payer() == null
                            || receipt.guildIncarnation() == null || receipt.cents() <= 0 || receipt.kind() == null)
                        throw new IOException("Invalid vehicle payment receipt: " + entry.getKey());
                    loadedReceipts.put(entry.getKey(), receipt);
                }
            }
            lastOwners.clear();
            if (data.lastOwners != null) lastOwners.putAll(data.lastOwners);
            buildOwners.clear();
            if (data.buildOwners != null) buildOwners.putAll(data.buildOwners);
            paidBuilds.clear(); paidBuilds.putAll(loadedBuilds);
            receipts.clear(); receipts.putAll(loadedReceipts);
            loadFailed = false;
        } catch (IOException | RuntimeException error) {
            loadFailed = true;
            throw new IllegalStateException("Cannot load vehicle fees", error);
        }
    }

    public void saveDurably() throws IOException {
        if (loadFailed || file == null) throw new IOException("Vehicle fee store is unavailable");
        Data data = new Data();
        data.lastOwners = new HashMap<>(lastOwners);
        data.paidBuilds = new HashMap<>();
        paidBuilds.forEach((key, value) -> data.paidBuilds.put(key, BuildData.from(value)));
        data.receipts = new HashMap<>(receipts);
        data.buildOwners = new HashMap<>(buildOwners);
        DurableJson.write(file.toPath(), data);
    }

    public void save() {
        if (file == null || (lastOwners.isEmpty() && paidBuilds.isEmpty() && receipts.isEmpty() && buildOwners.isEmpty() && !file.exists())) return;
        try { saveDurably(); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static final class Data {
        Map<String, String> lastOwners;
        Map<String, BuildData> paidBuilds;
        Map<String, Receipt> receipts;
        Map<String, String> buildOwners;
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
            if (payerUuid == null || !Double.isFinite(amount) || amount <= 0.0) {
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
