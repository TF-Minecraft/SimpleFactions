package me.Plugins.SimpleFactions.vehicles.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.Plugins.SimpleFactions.Loaders.VehiclesConfigLoader;
import me.Plugins.SimpleFactions.SimpleFactions;

public final class VehicleMaintenanceDecayTask {
    private BukkitTask task;

    public void start() {
        stop();
        SimpleFactions plugin = SimpleFactions.getInstance();
        if (plugin == null || Bukkit.getScheduler() == null) {
            return;
        }
        long ticks = VehiclesConfigLoader.getMaintenanceIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> plugin.getVehicleUpkeepService().tickHourlyDecay(),
                ticks,
                ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
