package net.tfminecraft.simplefactions.vehicles.maintenance;


import net.tfminecraft.simplefactions.vehicles.maintenance.DenarEconomyPlayerBank.PlayerBank;
import net.tfminecraft.simplefactions.vehicles.registry.PlayerVehicleRegistry;
import net.tfminecraft.simplefactions.vehicles.registry.VehicleOwnershipQueries;
import java.util.Collection;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.player.PlayerEconomyManager;
import net.tfminecraft.simplefactions.utils.Formatter;
import net.tfminecraft.simplefactions.identity.Members;

public final class VehicleUpkeepService {
    private final PlayerVehicleRegistry registry;
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
        this.playerBank = playerBank;
        this.maintenanceStore = maintenanceStore;
        this.decayApi = decayApi;
    }

    /** Warns each player whose bank cannot cover their vehicle upkeep at the next new day. */
    public void warnBankShortfalls(Collection<? extends Player> players, int secondsUntilCharge) {
        if (secondsUntilCharge <= 0) {
            return;
        }
        for (Player player : players) {
            var session = Members.session(player).orElse(null);
            if (session == null) continue;
            UUID playerUuid = session.ref().characterId();
            double upkeep = VehicleUpkeepProjection.projectedDailyUpkeep(playerUuid.toString(), registry)
                    + VehicleUpkeepProjection.projectedDailyTax(playerUuid.toString(), registry);
            if (upkeep <= 0.0) {
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

}
