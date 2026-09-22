package me.Plugins.SimpleFactions.Guild;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class GuildType {
    private String id;
    private String name;
    private boolean base;
    private boolean defaultType;

    public GuildType(String key, ConfigurationSection config) {
        id = key;
        name = StringFormatter.formatHex(config.getString("name", id));
        base = config.getBoolean("base", false);
        defaultType = config.getBoolean("default", false);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isBase() { return base; }
    public boolean isDefault() { return defaultType; }
}
