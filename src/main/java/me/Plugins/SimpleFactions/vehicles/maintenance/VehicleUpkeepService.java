package me.Plugins.SimpleFactions.vehicles.maintenance;


import me.Plugins.SimpleFactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import me.Plugins.SimpleFactions.vehicles.registry.PlayerVehicleRegistry;
import me.Plugins.SimpleFactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.player.PlayerEconomyManager;
import me.Plugins.SimpleFactions.player.income.PlayerCashflow;
import net.tfminecraft.VehicleFramework.Data.OwnedVehicleSummary;

public final class VehicleUpkeepService {
    private final PlayerVehicleRegistry registry;
    private final PlayerEconomyManager economyManager;
    private final PlayerBank playerBank;
    private final VehicleMaintenanceStore maintenanceStore;
    private final VehicleHealthDecayApi decayApi;

    public VehicleUpkeepService(
            PlayerVehicleRegistry registry,
            PlayerEconomyManager economyManager) {
        this(
                registry,
                economyManager,
                DenarEconomyPlayerBank.INSTANCE,
                new VehicleMaintenanceStore(),
                VehicleHealthDecayApi.Vf.INSTANCE);
    }

    public VehicleUpkeepService(
            PlayerVehicleRegistry registry,
            PlayerEconomyManager economyManager,
            PlayerBank playerBank,
            VehicleMaintenanceStore maintenanceStore,
            VehicleHealthDecayApi decayApi) {
        this.registry = registry;
        this.economyManager = economyManager;
        this.playerBank = playerBank;
        this.maintenanceStore = maintenanceStore;
        this.decayApi = decayApi;
    }

    public void processDailyUpkeep() {
        long now = System.currentTimeMillis();
        for (OwnedVehicleSummary vehicle : VehicleOwnershipQueries.allPersonalVehicles(registry)) {
            double upkeep = VehiclesConfigLoader.getUpkeep(vehicle.getTypeId());
            if (upkeep <= 0.0) {
                continue;
            }
            String playerName = VehicleOwnershipQueries.playerNameFromOwner(vehicle.getOwner());
            UUID playerUuid = VehicleOwnershipQueries.resolvePlayerUuid(playerName);
            if (playerUuid == null) {
                continue;
            }
            chargePlayer(playerUuid, upkeep, vehicle.getTypeId(), vehicle.getUuid(), now);
        }
    }

    public void tickHourlyDecay() {
        double fraction = VehiclesConfigLoader.getMaintenanceHourlyDamageFraction();
        double minHealth = VehiclesConfigLoader.getMaintenanceMinHealthFraction();
        for (String uuid : maintenanceStore.unpaidUuids()) {
            decayApi.unloadedDamage(uuid, fraction, minHealth);
        }
    }

    private void chargePlayer(
            UUID playerUuid,
            double upkeep,
            String vehicleTypeId,
            String vehicleUuid,
            long nowMillis) {
        if (playerUuid == null || upkeep <= 0.0) {
            return;
        }
        if (!playerBank.withdrawFromBank(playerUuid, upkeep)) {
            maintenanceStore.markUnpaid(vehicleUuid, nowMillis);
            persistMaintenance();
            SimpleFactions plugin = SimpleFactions.getInstance();
            if (plugin != null) {
                plugin.getLogger().info(
                    "Vehicle upkeep unpaid for player "
                    + playerUuid
                    + " vehicle "
                    + vehicleTypeId
                    + " amount "
                    + upkeep
                );
            }
            Player online = null;
            if (Bukkit.getServer() != null) {
                online = Bukkit.getPlayer(playerUuid);
            }
            if (online != null && online.isOnline()) {
                online.sendMessage(
                    "§cCould not pay vehicle upkeep ("
                    + vehicleTypeId
                    + "): insufficient bank balance."
                );
            }
            return;
        }
        economyManager.getLedger(playerUuid).add(PlayerCashflow.VEHICLE_UPKEEP, -upkeep);
        maintenanceStore.clearUnpaid(vehicleUuid);
        persistMaintenance();
    }

    private void persistMaintenance() {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.saveVehicleRegistry();
        }
    }
}
