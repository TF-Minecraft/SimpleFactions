package net.tfminecraft.simplefactions.testsupport;

import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.simplefactions.espionage.EspionageConfig;

/** Switches between the default military-only Spymaster and the mode that guards everything. */
public final class EspionageModes {
    public static final String MILITARY_ONLY = "espionage.intelligence.military-only";

    private EspionageModes() {}

    /** A Spymaster guards all faction and guild information, as before military-only existed. */
    public static YamlConfiguration guardEverything() {
        return guardEverything(new YamlConfiguration());
    }

    public static YamlConfiguration guardEverything(YamlConfiguration config) {
        config.set(MILITARY_ONLY, false);
        EspionageConfig.load(config);
        return config;
    }

    public static void reset() {
        EspionageConfig.load(new YamlConfiguration());
    }
}
