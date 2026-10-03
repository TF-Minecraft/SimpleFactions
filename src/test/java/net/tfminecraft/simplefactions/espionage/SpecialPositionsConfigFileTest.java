package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecialPositionsConfigFileTest {
    @TempDir Path directory;

    @Test void completeMigrationRemovesOnlyTheLegacySectionAndBacksUpTheOriginal() throws Exception {
        var legacy = directory.resolve("config.yml");
        String original = "# Keep this comment\nunrelated: value\nespionage:\n  aptitude:\n# An interior office comment\n    attribute-weights:\n      strength: -4.0\n\n# Keep the next key's comment\nafter: preserved\n";
        Files.writeString(legacy, original);
        var plugin = org.mockito.Mockito.mock(net.tfminecraft.simplefactions.SimpleFactions.class);
        org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(directory.toFile());
        org.mockito.Mockito.when(plugin.getResource("special-positions.yml")).thenAnswer(ignored ->
                getClass().getResourceAsStream("/special-positions.yml"));
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        try {
            SpecialPositionsConfigFile.load(plugin);
            assertEquals("# Keep this comment\nunrelated: value\n# Keep the next key's comment\nafter: preserved\n", Files.readString(legacy));
            assertEquals(original, Files.readString(directory.resolve("config.yml.before-special-positions")));
            assertEquals(-4, EspionageConfig.weights().get("strength"));
            SpecialPositionsConfigFile.load(plugin);
            assertEquals(original, Files.readString(directory.resolve("config.yml.before-special-positions")));
        } finally { EspionageConfig.load(new YamlConfiguration()); }
    }

    @Test void migrationPreservesCustomWeightsPermissionCostsAndMainConfiguration() throws Exception {
        var legacy = directory.resolve("config.yml");
        String original = "unrelated: value\nespionage:\n  bypass-permission: staff.spy\n  aptitude:\n    attribute-weights:\n      strength: -4.0\n  appointments:\n    repeat-cost: 333.0\n";
        Files.writeString(legacy, original);
        var file = directory.resolve("special-positions.yml");
        var defaults = defaults();
        var migrated = SpecialPositionsConfigFile.prepare(file.toFile(), legacy.toFile(), defaults);
        assertEquals("staff.spy", migrated.getString("espionage.bypass-permission"));
        assertEquals(-4, migrated.getDouble("espionage.aptitude.attribute-weights.strength"));
        assertEquals(333, migrated.getDouble("espionage.appointments.repeat-cost"));
        assertEquals("reliable", migrated.getString("espionage.intelligence.minimum-tiers.professional-army"));
        assertEquals(original, Files.readString(legacy));
        // Existing dedicated settings always win over stale legacy values; missing new fields are added.
        migrated.set("espionage.appointments.repeat-cost", 250);
        migrated.set("espionage.intelligence.minimum-tiers.professional-army", null);
        migrated.save(file.toFile());
        var reloaded = SpecialPositionsConfigFile.prepare(file.toFile(), legacy.toFile(), defaults);
        assertEquals(250, reloaded.getDouble("espionage.appointments.repeat-cost"));
        assertEquals(-4, reloaded.getDouble("espionage.aptitude.attribute-weights.strength"));
        assertEquals("reliable", reloaded.getString("espionage.intelligence.minimum-tiers.professional-army"));
        assertEquals(original, Files.readString(legacy));
    }

    @Test void malformedDedicatedYamlIsRejectedWithoutOverwritingEitherFile() throws Exception {
        var file = directory.resolve("special-positions.yml");
        Files.writeString(file, "espionage: [invalid\n");
        var legacy = directory.resolve("config.yml");
        Files.writeString(legacy, "unrelated: value\n");
        assertThrows(org.bukkit.configuration.InvalidConfigurationException.class,
                () -> SpecialPositionsConfigFile.prepare(file.toFile(), legacy.toFile(), defaults()));
        assertEquals("espionage: [invalid\n", Files.readString(file));
        assertEquals("unrelated: value\n", Files.readString(legacy));
    }

    private YamlConfiguration defaults() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(new java.io.InputStreamReader(getClass().getResourceAsStream("/special-positions.yml"), java.nio.charset.StandardCharsets.UTF_8));
        return yaml;
    }
}
