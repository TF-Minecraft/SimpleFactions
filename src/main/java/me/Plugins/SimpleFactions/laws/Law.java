package me.Plugins.SimpleFactions.laws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.Plugins.TLibs.TLibs;

public class Law {
    private String group;
    private String id;
    private String name;
    private String icon;
    private List<String> requirements = new ArrayList<>();
    private List<String> description = new ArrayList<>();
    private Map<String, Integer> compatibility = new HashMap<>();
    private double upkeep;
    private double cost;

    //Effects
    private Map<Scope, LawEffect> scopedEffects = new LinkedHashMap<>();

    public Law(String group, String key, ConfigurationSection config) {
        this.group = group;
        id = key;
        icon = config.getString("icon", "v.book");
        name = StringFormatter.formatHex(config.getString("name", key));
        upkeep = config.getDouble("upkeep", 0);
        cost = config.getDouble("cost", 10);
        if(config.contains("requirements")) {
            for(String s : config.getStringList("requirements")) 
                requirements.add(s);
        }
        if(config.contains("description")) {
            for(String s : config.getStringList("description")) 
                description.add(StringFormatter.formatHex(s));
        }
        if(config.contains("effects")) {
            for(String s : config.getConfigurationSection("effects").getKeys(false)) {
                try {
                    Scope scope = Scope.valueOf(s.toUpperCase());
                    scopedEffects.put(scope, new LawEffect(scope, config.getConfigurationSection("effects."+s)));
                } catch (Exception e) {
                    Bukkit.getLogger().info("[SimpleFactions] could not parse modifier for scope "+s+" in law "+key);
                    // TODO: handle exception
                }
            }
        }
        if(config.contains("compatibility")) {
            for(String s : config.getStringList("compatibility")) {
                String[] split = s.split("\\s+");
                if(split.length != 2) continue;
                try {
                    compatibility.put(split[0], Integer.parseInt(split[1]));
                } catch (Exception e) {
                    Bukkit.getLogger().info("[SimpleFactions] could not parse compatibility for law "+key+" with law "+split[0]);
                }
            }
        }
    }

    public String getGroup() { return group; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getIconString() { return icon; }
    public ItemStack getIcon() {
        return TLibs.getItemAPI().getCreator().getItemFromPath(icon);
    }
    public List<String> getRequirements() { return requirements; }
    public boolean hasEffects() { return !scopedEffects.isEmpty(); }
    public Map<Scope, LawEffect> getScopedEffects() { return scopedEffects; }
    public List<String> getDescription() { return description; }
    public boolean hasDescription() { return !description.isEmpty(); }
    public boolean isAvailable(Faction f) {
        return CanHaveLaw.canHave(f, this);
    }
    public boolean affectsEconomy() {
        for(LawEffect effect : scopedEffects.values()) {
            if(effect.affectsEconomy()) return true;
        }
        return false;
    }

    public Map<String, Integer> getCompatibility() {
        return compatibility;
    }

    public Integer getCompatibility(String lawId) {
        return compatibility.getOrDefault(lawId, 1);
    }

    public double getUpkeep() {
        return upkeep;
    }
    public double getCost() {
        return cost;
    }
}
