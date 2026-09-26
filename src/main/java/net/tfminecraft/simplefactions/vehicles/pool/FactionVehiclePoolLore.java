package net.tfminecraft.simplefactions.vehicles.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;

public final class FactionVehiclePoolLore {
    private static final int MAX_LISTED = 10;

    private FactionVehiclePoolLore() {}

    public static List<String> lines(
            List<PlayerVehicleRecord> pool,
            int artillerySlots,
            Set<String> unpaidUuids) {
        List<PlayerVehicleRecord> vehicles = pool == null ? List.of() : pool;
        int slots = Math.max(0, artillerySlots);
        int artillery = 0;
        for (PlayerVehicleRecord record : vehicles) {
            if (FactionVehiclePoolService.isArtillery(record.getVehicleTypeId())) {
                artillery++;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Artillery: §e" + artillery + "/" + slots);
        if (vehicles.isEmpty()) {
            lore.add("§7No vehicles in the pool.");
            return lore;
        }

        int unpaid = 0;
        for (PlayerVehicleRecord record : vehicles) {
            if (unpaidUuids != null && unpaidUuids.contains(record.getVehicleUuid())) {
                unpaid++;
            }
        }
        if (unpaid > 0) {
            lore.add("§cUnpaid: " + unpaid + " of " + vehicles.size());
        }

        int listed = 0;
        for (PlayerVehicleRecord record : vehicles) {
            if (listed == MAX_LISTED) {
                break;
            }
            double upkeep = VehiclesConfigLoader.getUpkeep(record.getVehicleTypeId());
            String line = "§7- §f" + record.getVehicleTypeId()
                    + " §8#" + shortId(record.getVehicleUuid())
                    + " §8" + Formatter.formatMoney(upkeep) + "d/day";
            if (unpaidUuids != null && unpaidUuids.contains(record.getVehicleUuid())) {
                line = line + " §cunpaid";
            }
            lore.add(line);
            listed++;
        }
        int remaining = vehicles.size() - listed;
        if (remaining > 0) {
            lore.add("§7And " + remaining + " more...");
        }
        return lore;
    }

    /** Enough of the vehicle id to tell two vehicles of the same type apart. */
    private static String shortId(String vehicleUuid) {
        if (vehicleUuid == null) {
            return "?";
        }
        return vehicleUuid.length() <= 6 ? vehicleUuid : vehicleUuid.substring(0, 6);
    }
}
