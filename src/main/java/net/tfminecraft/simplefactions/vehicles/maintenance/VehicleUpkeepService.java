package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.pool.FactionVehiclePoolService;
import net.tfminecraft.simplefactions.vehicles.registry.OwnershipMode;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRecord;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.objects.Faction;
import java.util.Collection;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.player.income.PlayerCashflow;
import net.tfminecraft.simplefactions.utils.Formatter;
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
        chargeFactionVehicles(now);
    }

    /**
     * Pool and installation vehicles are owned by the faction leader in VehicleFramework,
     * but the faction bank pays their upkeep. The faction ledger shows it as Vehicle Upkeep.
     */
    private void chargeFactionVehicles(long nowMillis) {
        if (registry == null || FactionManager.factions == null) {
            return;
        }
        for (PlayerVehicleRecord record : registry.getAll()) {
            if (record.getMode() != OwnershipMode.POOL && record.getMode() != OwnershipMode.INSTALLATION) {
                continue;
            }
            double upkeep = VehiclesConfigLoader.getUpkeep(record.getVehicleTypeId());
            if (upkeep <= 0.0) {
                maintenanceStore.clearUnpaid(record.getVehicleUuid());
                persistMaintenance();
                continue;
            }
            Faction faction = FactionVehiclePoolService.payingFaction(record);
            if (faction == null && record.getMode() == OwnershipMode.INSTALLATION) {
                // An installation no faction holds any more has nobody to bill, so it
                // must not keep decaying for a debt nobody can pay.
                if (maintenanceStore.isUnpaid(record.getVehicleUuid())) {
                    maintenanceStore.clearUnpaid(record.getVehicleUuid());
                    persistMaintenance();
                }
                continue;
            }
            Bank bank = faction == null ? null : faction.getBank();
            Double wealth = bank == null ? null : bank.getWealth();
            if (wealth == null || wealth < upkeep) {
                markFactionUnpaid(record, faction, upkeep, nowMillis);
                continue;
            }
            bank.withdraw(upkeep);
            maintenanceStore.clearUnpaid(record.getVehicleUuid());
            persistMaintenance();
        }
    }

    /** Warns each player whose bank cannot cover their vehicle upkeep at the next new day. */
    public void warnBankShortfalls(Collection<? extends Player> players, int secondsUntilCharge) {
        if (secondsUntilCharge <= 0) {
            return;
        }
        for (Player player : players) {
            double upkeep = VehicleUpkeepProjection.projectedDailyUpkeep(player.getName(), registry);
            if (upkeep <= 0.0) {
                continue;
            }
            UUID playerUuid = playerBank.resolve(player.getName());
            if (playerUuid == null) {
                continue;
            }
            // Round to cents so a float residue never reads as "lack 0.00".
            double shortfall = Formatter.formatDouble(upkeep - playerBank.getBankBalance(playerUuid));
            if (shortfall > 0.0) {
                player.sendMessage(VehicleMaintenanceMessages.bankShortfall(shortfall, secondsUntilCharge));
            }
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

    private void markFactionUnpaid(
            PlayerVehicleRecord record,
            Faction faction,
            double upkeep,
            long nowMillis) {
        maintenanceStore.markUnpaid(record.getVehicleUuid(), nowMillis);
        persistMaintenance();
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin != null) {
            plugin.getLogger().info(
                "Faction vehicle upkeep unpaid for faction "
                + (faction != null ? faction.getId() : record.getFactionId())
                + " vehicle "
                + record.getVehicleTypeId()
                + " amount "
                + upkeep
            );
        }
        if (faction == null || faction.getLeader() == null || Bukkit.getServer() == null) {
            return;
        }
        Player leader = Bukkit.getPlayerExact(faction.getLeader());
        if (leader != null && leader.isOnline()) {
            leader.sendMessage(
                "§cCould not pay faction vehicle upkeep ("
                + record.getVehicleTypeId()
                + "): insufficient faction bank."
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
