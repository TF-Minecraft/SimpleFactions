package net.tfminecraft.simplefactions.plots;

import java.io.IOException;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

/** Starts plots with the plugin: saved plots, the /plot command, listeners and the timer. */
public final class Plots {
    private static BukkitTask task;
    private static boolean started, charactersRegistered;

    private Plots() {}

    public static void start(JavaPlugin plugin) {
        stop();
        try {
            PlotService.load(plugin.getDataFolder().toPath().resolve("Cache/plots.json"));
        } catch (IOException | RuntimeException error) {
            // Never run on an empty store that would overwrite the saved plots.
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot load Cache/plots.json; plots are disabled", error);
            return;
        }
        var command = plugin.getCommand("plot");
        if (command != null) {
            PlotCommand executor = new PlotCommand();
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        if (!started) plugin.getServer().getPluginManager().registerEvents(new PlotListener(), plugin);
        started = true;
        registerCharacters(plugin);
        long period = PlotConfig.scanSeconds() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            PlotService.tick();
            PlotService.scan();
        }, period, period);
    }

    /** RPCharacters may enable after SimpleFactions; its listener waits until then. */
    public static void registerCharacters(JavaPlugin plugin) {
        if (!started || charactersRegistered || !PlotCharacters.enabled()) return;
        plugin.getServer().getPluginManager().registerEvents(new PlotCharacterListener(), plugin);
        charactersRegistered = true;
    }

    public static void stop() {
        if (task != null) task.cancel();
        task = null;
    }
}
