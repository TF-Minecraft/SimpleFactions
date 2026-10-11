package net.tfminecraft.simplefactions.plots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotsTest {
    @TempDir Path directory;
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final BukkitTask task = mock(BukkitTask.class);
    private final Logger logger = mock(Logger.class);

    @BeforeEach
    void setup() throws Exception {
        reset();
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getPluginManager()).thenReturn(plugins);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(100L), eq(100L))).thenReturn(task);
    }

    @AfterEach
    void reset() throws Exception {
        Plots.stop();
        for (String name : new String[]{"started", "charactersRegistered"}) {
            Field field = Plots.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, false);
        }
    }

    @Test
    void startRegistersOnceAndRunsTheTimer() throws Exception {
        PluginCommand command = mock(PluginCommand.class);
        when(plugin.getCommand("plot")).thenReturn(command);
        try (var characters = mockStatic(PlotCharacters.class)) {
            Plots.registerCharacters(plugin);
            Plots.start(plugin);
            verify(command).setExecutor(any(PlotCommand.class));
            verify(command).setTabCompleter(any(PlotCommand.class));
            verify(plugins).registerEvents(any(PlotListener.class), eq(plugin));
            verify(plugins, never()).registerEvents(any(PlotCharacterListener.class), eq(plugin));
            characters.when(PlotCharacters::enabled).thenReturn(true);
            Plots.registerCharacters(plugin);
            Plots.registerCharacters(plugin);
            verify(plugins, times(1)).registerEvents(any(PlotCharacterListener.class), eq(plugin));
            when(plugin.getCommand("plot")).thenReturn(null);
            Plots.start(plugin);
            verify(task).cancel();
            verify(plugins, times(1)).registerEvents(any(PlotListener.class), eq(plugin));
            var timer = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(2)).runTaskTimer(eq(plugin), timer.capture(), eq(100L), eq(100L));
            timer.getValue().run();
        }
        Plots.stop();
        Plots.stop();
    }

    @Test
    void aDamagedPlotFileDisablesPlotsInsteadOfOverwritingIt() throws Exception {
        Path file = directory.resolve("Cache/plots.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{not json");
        Plots.start(plugin);
        verify(logger).log(eq(Level.SEVERE), anyString(), any(Throwable.class));
        verify(plugins, never()).registerEvents(any(), any());
        assertEquals("{not json", Files.readString(file));
    }
}
