package me.Plugins.SimpleFactions.vehicles.maintenance;

import org.bukkit.Bukkit;

import net.tfminecraft.VehicleFramework.VehicleFramework;

public interface VehicleHealthDecayApi {
    boolean unloadedDamage(String vehicleUuid, double fractionOfMax, double minHealthFraction);

    final class Vf implements VehicleHealthDecayApi {
        public static final Vf INSTANCE = new Vf();

        private Vf() {}

        @Override
        public boolean unloadedDamage(String vehicleUuid, double fractionOfMax, double minHealthFraction) {
            if (vehicleUuid == null || vehicleUuid.isBlank()) {
                return false;
            }
            if (Bukkit.getServer() == null
                    || Bukkit.getPluginManager() == null
                    || !Bukkit.getPluginManager().isPluginEnabled("VehicleFramework")) {
                return false;
            }
            return VehicleFramework.getVehicleManager()
                    .unloadedDamage(vehicleUuid, fractionOfMax, minHealthFraction);
        }
    }
}
