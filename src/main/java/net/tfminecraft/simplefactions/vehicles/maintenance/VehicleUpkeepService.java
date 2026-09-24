package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.vehicleframework.data.OwnedVehicleSummary;

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
            UUID playerUuid = playerBank.resolve(playerName);
            if (playerUuid == null) {
                markUnpaid(vehicle.getUuid(), vehicle.getTypeId(), null, upkeep, now);
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
            markUnpaid(vehicleUuid, vehicleTypeId, playerUuid, upkeep, nowMillis);
            return;
        }
        economyManager.getLedger(playerUuid).add(PlayerCashflow.VEHICLE_UPKEEP, -upkeep);
        maintenanceStore.clearUnpaid(vehicleUuid);
        persistMaintenance();
    }

    private void markUnpaid(
            String vehicleUuid,
            String vehicleTypeId,
            UUID playerUuid,
            double upkeep,
            long nowMillis) {
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
        if (playerUuid == null || Bukkit.getServer() == null) {
            return;
        }
        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null && online.isOnline()) {
            online.sendMessage(
                "§cCould not pay vehicle upkeep ("
                + vehicleTypeId
                + "): insufficient bank balance."
            );
        }
    }

    private void persistMaintenance() {
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.saveVehicleRegistry();
        }
    }
}
