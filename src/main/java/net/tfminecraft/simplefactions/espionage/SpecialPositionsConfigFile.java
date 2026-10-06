package net.tfminecraft.simplefactions.espionage;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Migrates the old espionage section once; existing dedicated settings always win. */
public final class SpecialPositionsConfigFile {
    // Settings whose rule no longer exists; their comments would mislead staff.
    private static final java.util.List<String> RETIRED_KEYS = java.util.List.of(
            "espionage.aptitude.solo-leader-multiplier", "espionage.appointments.repeat-cost");
    private SpecialPositionsConfigFile() {}

    public static void load(JavaPlugin plugin) {
        try (var resource = plugin.getResource("special-positions.yml")) {
            if (resource == null) throw new IOException("Missing special-positions.yml resource");
            var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            var file = new File(plugin.getDataFolder(), "special-positions.yml");
            var legacy = new File(plugin.getDataFolder(), "config.yml");
            boolean existed = file.exists();
            var settings = prepare(file, legacy, defaults);
            EspionageConfig.load(settings);
            if (!existed && legacy.exists()) {
                // Preserve comments and unrelated settings. Never rewrite the entire main YAML.
                String before = Files.readString(legacy.toPath());
                // Keep comments for the next root key; interior comments have another indented line after them.
                String after = before.replaceFirst("(?m)^espionage:[^\\r\\n]*(?:(?:\\r?\\n(?:[ \\t]*|[ \\t]*#[^\\r\\n]*))*"
                        + "\\r?\\n[ \\t]+[^\\r\\n]*)*(?:\\r?\\n[ \\t]*(?=\\r?\\n|$))*(?:\\r?\\n)?", "");
                if (!before.equals(after)) {
                    Files.copy(legacy.toPath(), new File(plugin.getDataFolder(), "config.yml.before-special-positions").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    atomicWrite(legacy, after);
                    plugin.getLogger().info("Moved espionage settings from config.yml to special-positions.yml");
                }
            }
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load special-positions.yml; existing settings have been preserved", exception);
        }
    }

    static YamlConfiguration prepare(File file, File legacy, YamlConfiguration defaults)
            throws IOException, InvalidConfigurationException {
        var settings = new YamlConfiguration();
        if (file.exists()) settings.load(file);
        else if (legacy.exists()) {
            var old = new YamlConfiguration();
            old.load(legacy);
            for (var entry : old.getValues(true).entrySet())
                if (entry.getKey().startsWith("espionage.") && !(entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection))
                    settings.set(entry.getKey(), entry.getValue());
        }
        boolean changed = !file.exists();
        if (changed) settings.options().setHeader(defaults.options().getHeader());
        for (String retired : RETIRED_KEYS) {
            if (settings.contains(retired)) {
                settings.set(retired, null);
                changed = true;
            }
        }
        for (var entry : defaults.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) && !settings.contains(entry.getKey())) {
                settings.set(entry.getKey(), entry.getValue());
                settings.setComments(entry.getKey(), defaults.getComments(entry.getKey()));
                changed = true;
            }
        }
        if (changed) atomicWrite(file, settings.saveToString());
        return settings;
    }

    private static void atomicWrite(File file, String contents) throws IOException {
        Files.createDirectories(file.toPath().toAbsolutePath().getParent());
        var temporary = file.toPath().resolveSibling(file.getName() + ".pending");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
