package net.tfminecraft.simplefactions.vehicles.berth;


import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import java.util.Optional;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.vehicleframework.vehicles.ActiveVehicle;

public final class InstallationVehicleOwnerSync {
    private final PlayerVehicleRegistry registry;

    public InstallationVehicleOwnerSync(PlayerVehicleRegistry registry) {
        this.registry = registry;
    }

    public static String expectedOwner(Faction faction) {
        if (faction == null || faction.getLeader() == null) {
            return "player_none";
        }
        return "player_" + faction.getLeader();
    }

    public void applyLeaderOwner(ActiveVehicle vehicle, Faction faction) {
        if (vehicle == null || faction == null) {
            return;
        }
        applyLeaderOwner(vehicle.getOwnerData(), faction);
    }

    public void applyLeaderOwner(net.tfminecraft.vehicleframework.data.OwnerData ownerData, Faction faction) {
        if (ownerData == null || faction == null) {
            return;
        }
        ownerData.setOwner(expectedOwner(faction));
    }

    public void syncIfBerthed(ActiveVehicle vehicle) {
        if (vehicle == null || vehicle.getUUID() == null) {
            return;
        }
        syncIfBerthed(vehicle.getUUID(), vehicle.getOwnerData());
    }

    void syncIfBerthed(String vehicleUuid, net.tfminecraft.vehicleframework.data.OwnerData ownerData) {
        if (vehicleUuid == null || ownerData == null) {
            return;
        }

        Optional<PlayerVehicleRecord> recordOpt = registry.getByVehicleUuid(vehicleUuid);
        if (recordOpt.isEmpty()) {
            return;
        }
        PlayerVehicleRecord record = recordOpt.get();
        if (record.getMode() == OwnershipMode.POOL) {
            Faction faction = FactionManager.getByString(record.getFactionId());
            applyExpectedOwner(ownerData, faction);
            return;
        }
        if (record.getMode() != OwnershipMode.INSTALLATION) {
            return;
        }

        String installationId = record.getInstallationId();
        if (installationId == null) {
            return;
        }

        // Resolves by the record's faction id, since two factions can reuse an installation id.
        Faction faction = net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService.payingFaction(record);
        if (faction == null) {
            return;
        }

        applyExpectedOwner(ownerData, faction);
    }

    private static void applyExpectedOwner(
            net.tfminecraft.vehicleframework.data.OwnerData ownerData,
            Faction faction) {
        if (faction == null) {
            return;
        }
        String expected = expectedOwner(faction);
        String current = ownerData.getOwner();
        if (!expected.equalsIgnoreCase(current) || isLegacyFactionOwner(current)) {
            ownerData.setOwner(expected);
        }
    }

    private static boolean isLegacyFactionOwner(String owner) {
        return owner != null && owner.startsWith("faction_");
    }
}
