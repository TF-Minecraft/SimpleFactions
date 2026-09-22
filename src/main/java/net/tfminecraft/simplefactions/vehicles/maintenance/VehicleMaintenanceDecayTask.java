package net.tfminecraft.simplefactions.vehicles.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.simplefactions.loaders.VehiclesConfigLoader;
import net.tfminecraft.simplefactions.SimpleFactions;

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
