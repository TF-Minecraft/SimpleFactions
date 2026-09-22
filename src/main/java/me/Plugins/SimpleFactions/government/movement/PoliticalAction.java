package me.Plugins.SimpleFactions.government.movement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class PoliticalAction {
    private Action action;
    private String name;
    private String icon;
    private List<String> description = new ArrayList<>();
    private List<String> pools = new ArrayList<>();

    public PoliticalAction(String key, ConfigurationSection config) {
        try {
            action = Action.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid political action key: " + key);
        }
        this.icon = config.getString("icon", "v.paper");
        this.name = StringFormatter.formatHex(config.getString("name", key));
        for (String line : config.getStringList("description")) {
            description.add(StringFormatter.formatHex(line));
        }
        for (String pool : config.getStringList("pools")) {
            pools.add(pool);
        }
    }

    public PoliticalAction(Action action) {
        this.action = action;
        this.icon = "v.paper";
        this.name = action.getDisplay();
        this.description = new ArrayList<>();
        this.pools = Arrays.asList("citizens", "guilds", "factions");
    }
 
    public Action getAction() {
        return action;
    }

    public String getName() {
        return name;
    }

    public String getIconString() {
        return icon;
    }

    public ItemStack getIcon() {
        return TLibs.getItemAPI().getCreator().getItemFromPath(icon);
    }

    public List<String> getDescription() {
        return description;
    }

    public List<String> getPools() {
        return pools;
    }

    public boolean allowCitizens() {
        return pools.contains("citizens");
    }

    public boolean allowGuilds() {
        return pools.contains("guilds");
    }

    public boolean allowFactions() {
        return pools.contains("factions") || pools.contains("vassals");
    }
}
