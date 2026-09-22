package me.Plugins.SimpleFactions.Guild.upgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.Plugins.SimpleFactions.Guild.GuildType;
import me.Plugins.SimpleFactions.Guild.Branch.Branch;
import me.Plugins.SimpleFactions.Guild.Branch.BranchModifier;
import me.Plugins.SimpleFactions.Loaders.GuildLoader;
import me.Plugins.SimpleFactions.enums.GuildModifier;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class Upgrade {
    private String id;
    private String name;
    private String icon;
    private int level;
    private int maxLevel;
    private double upkeep;
    private int expansionTime;
    private List<GuildType> allowedTypes = new ArrayList<>();
    private Map<GuildModifier, BranchModifier> modifiers = new HashMap<>();

    private List<String> description = new ArrayList<>();

    public Upgrade(String key, ConfigurationSection config) {
        id = key;
        name = StringFormatter.formatHex(config.getString("name", id));
        icon = config.getString("icon", "black_dye.10");
        upkeep = config.getDouble("upkeep", 10);
        level = 0;
        maxLevel = config.getInt("max-level", Integer.MAX_VALUE);
        expansionTime = config.getInt("expansion-time", 21600);
        for(String s : config.getStringList("allowed-types")) {
            GuildType type = GuildLoader.getByString(s);
            if(type != null) allowedTypes.add(type);
        }
        if(config.contains("description")) {
            for(String s : config.getStringList("description")) {
                description.add(StringFormatter.formatHex(s));
            }
        }
        if(config.contains("modifiers")) {
            for(String s : config.getStringList("modifiers")) {
                String[] args = s.split("\\s+");
                if(args.length < 2) continue;
                try {
                    double base = Double.parseDouble(args[1]);
                    double perLevel = base;
                    if(args.length == 3) {
                        perLevel = Double.parseDouble(args[2]);
                    }
                    modifiers.put(GuildModifier.valueOf(args[0].toUpperCase()), new BranchModifier(base, perLevel));
                } catch (Exception e) {
                    Bukkit.getLogger().info("[SimpleFactions] could not parse modifier "+s);
                    // TODO: handle exception
                }
            }
        }
    }

    public Upgrade(Upgrade b, int level) {
        id = b.id;
        name = b.name;
        icon = b.icon;
        upkeep = b.upkeep;
        allowedTypes = b.allowedTypes;
        modifiers = b.modifiers;
        expansionTime = b.expansionTime;
        maxLevel = b.maxLevel;
        this.description = b.description;
        this.level = Math.min(level, maxLevel);
    }


    public String getId() { return id; }
    public String getName() { return name; }
    public double getUpkeep() { return upkeep; }
    public double getTotalUpkeep() {
        return upkeep * level;
    }
    public boolean isAllowed(GuildType type) {
        return allowedTypes.contains(type);
    }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.min(level, maxLevel); }
    public int getExpansionTime() { return expansionTime; }
    public int getMaxLevel() { return maxLevel; }
    /** Guild upgrades leave max-level unset, so they are unbounded. */
    public boolean hasMaxLevel() { return maxLevel != Integer.MAX_VALUE; }
    public boolean isMaxed() { return level >= maxLevel; }
    public void levelUp() {
        if(isMaxed()) return;
        level++;
    }
    public void levelDown() {
        if (level > 0) level--;
    }
    public ItemStack getIconItem() {
        String[] args = icon.split("\\.");
        ItemStack item = new ItemStack(Material.DIRT, 1);
        try {
            item = new ItemStack(Material.valueOf(args[0].toUpperCase()), 1);
            ItemMeta m = item.getItemMeta();
            m.setCustomModelData(Integer.parseInt(args[1]));
            item.setItemMeta(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return item;
    }

    public List<String> getDescription() { return description; }

    public BranchModifier getModifier(GuildModifier id) {
        return modifiers.getOrDefault(id, null);
    }
    public double getAmount(GuildModifier m) {
        double amount = 0.0;
        BranchModifier mod = getModifier(m);
        if(mod != null) amount = mod.getCurrent(level);
        return amount;
    }
    public Map<GuildModifier, BranchModifier> getModifiers() { return modifiers; }
    public List<GuildModifier> getModifierKeys() {
        List<GuildModifier> ids = new ArrayList<>(modifiers.keySet());
        Collections.sort(ids);
        return ids;
    }
}
