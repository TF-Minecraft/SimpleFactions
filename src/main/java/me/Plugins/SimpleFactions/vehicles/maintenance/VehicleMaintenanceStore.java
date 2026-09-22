package me.Plugins.SimpleFactions.vehicles.maintenance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleMaintenanceStore {
    private final Map<String, Long> unpaidSinceMillis = new ConcurrentHashMap<>();

    public void markUnpaid(String vehicleUuid, long nowMillis) {
        if (vehicleUuid == null || vehicleUuid.isBlank()) {
            return;
        }
        unpaidSinceMillis.putIfAbsent(vehicleUuid, nowMillis);
    }

    public void clearUnpaid(String vehicleUuid) {
        if (vehicleUuid == null) {
            return;
        }
        unpaidSinceMillis.remove(vehicleUuid);
    }

    public boolean isUnpaid(String vehicleUuid) {
        return vehicleUuid != null && unpaidSinceMillis.containsKey(vehicleUuid);
    }

    public Set<String> unpaidUuids() {
        return Set.copyOf(unpaidSinceMillis.keySet());
    }

    public void replaceAll(Map<String, Long> unpaid) {
        unpaidSinceMillis.clear();
        if (unpaid == null) {
            return;
        }
        for (Map.Entry<String, Long> entry : unpaid.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            unpaidSinceMillis.put(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> copy = new LinkedHashMap<>();
        copy.putAll(unpaidSinceMillis);
        return Collections.unmodifiableMap(copy);
    }
}
