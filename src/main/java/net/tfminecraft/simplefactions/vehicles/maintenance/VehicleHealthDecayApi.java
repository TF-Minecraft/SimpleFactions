package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;

import net.tfminecraft.vehicleframework.VehicleFramework;
import net.tfminecraft.vehicleframework.vehicles.HealthDecayRule;

public interface VehicleHealthDecayApi {
    boolean unloadedDamage(String vehicleUuid, UnpaidVehicleEffects effects);

    final class Vf implements VehicleHealthDecayApi {
        public static final Vf INSTANCE = new Vf();

        private Vf() {}

        @Override
        public boolean unloadedDamage(String vehicleUuid, UnpaidVehicleEffects effects) {
            if (vehicleUuid == null || vehicleUuid.isBlank() || effects == null) {
                return false;
            }
            if (Bukkit.getServer() == null
                    || Bukkit.getPluginManager() == null
                    || !Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) {
                return false;
            }
            return VehicleFramework.getVehicleManager().unloadedDamage(vehicleUuid, rule(effects));
        }

        static HealthDecayRule rule(UnpaidVehicleEffects effects) {
            Map<String, Double> parts = new HashMap<>();
            effects.partMinHealthPercent().forEach((part, percent) -> parts.put(part, percent / 100.0));
            return new HealthDecayRule(
                    effects.hourlyDamagePercent() / 100.0,
                    effects.minHealthPercent() / 100.0,
                    parts,
                    effects.keepAfloat(),
                    effects.keepAloft());
        }
    }
}
