package me.Plugins.SimpleFactions.vehicles.berth;


import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRecord;
import me.Plugins.SimpleFactions.vehicles.registry.OwnershipMode;
import java.util.Optional;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;
import net.tfminecraft.VehicleFramework.Vehicles.ActiveVehicle;

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

    public void applyLeaderOwner(net.tfminecraft.VehicleFramework.Data.OwnerData ownerData, Faction faction) {
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

    void syncIfBerthed(String vehicleUuid, net.tfminecraft.VehicleFramework.Data.OwnerData ownerData) {
        if (vehicleUuid == null || ownerData == null) {
            return;
        }

        Optional<PlayerVehicleRecord> recordOpt = registry.getByVehicleUuid(vehicleUuid);
        if (recordOpt.isEmpty() || recordOpt.get().getMode() != OwnershipMode.INSTALLATION) {
            return;
        }

        String installationId = recordOpt.get().getInstallationId();
        if (installationId == null) {
            return;
        }

        Faction faction = findFactionForInstallation(installationId);
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

    private static Faction findFactionForInstallation(String installationId) {
        for (Faction faction : FactionManager.factions) {
            InstallationHandler handler = faction.getInstallationHandler();
            if (handler == null) {
                continue;
            }
            Installation installation = handler.getById(installationId);
            if (installation != null) {
                return faction;
            }
        }
        return null;
    }
}
