package me.Plugins.SimpleFactions.laws;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Loaders.RegimentLoader;
import me.Plugins.SimpleFactions.Objects.Bracket;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.enums.Brackets;
import me.Plugins.SimpleFactions.enums.Region;
import me.Plugins.SimpleFactions.enums.Rules;
import me.Plugins.SimpleFactions.enums.Scope;

public class LawEffect {
    private Map<Rules, Boolean> rules = new LinkedHashMap<>();
    private Map<Regiment, Integer> regiments = new LinkedHashMap<>();
    private List<FactionModifier> globalModifiers = new ArrayList<>();
    private Map<Region, List<FactionModifier>> regionModifiers = new LinkedHashMap<>();
    private Map<Brackets, Bracket> brackets = new LinkedHashMap<>();
    private int councilSize;
    
    public LawEffect(Scope scope, ConfigurationSection config) {

        councilSize = config.getInt("council-size", -1);
        // ---- Rules ----
        if (config.contains("rules")) {
            for (String s : config.getStringList("rules")) {
                try {
                    String[] args = s.split("\\s+");
                    if (args.length != 2) continue;

                    Rules rule = Rules.valueOf(args[0].toUpperCase());
                    rules.put(rule, Boolean.parseBoolean(args[1]));
                } catch (Exception e) {
                    Bukkit.getLogger().warning(
                        "[SimpleFactions] Invalid rule in " + config.getCurrentPath()
                    );
                }
            }
        }

        // ---- Rules ----
        if (config.contains("regiments")) {
            for (String s : config.getStringList("regiments")) {
                try {
                    String[] args = s.split("\\s+");
                    if (args.length != 2) continue;

                    Regiment reg = RegimentLoader.getByString(args[0]);
                    regiments.put(reg, Integer.parseInt(args[1]));
                } catch (Exception e) {
                    Bukkit.getLogger().warning(
                        "[SimpleFactions] Invalid regiment in " + config.getCurrentPath()
                    );
                }
            }
        }

        if (config.contains("brackets")) {
            for (String key : config.getConfigurationSection("brackets").getKeys(false)) {
                try {
                    Brackets type = Brackets.valueOf(key.toUpperCase());
                    String value = config.getString("brackets." + key);

                    String[] split = value.split("-");
                    if (split.length != 2) continue;

                    double min = Double.parseDouble(split[0]);
                    double max = Double.parseDouble(split[1]);

                    brackets.put(type, new Bracket(min, max));

                } catch (Exception e) {
                    Bukkit.getLogger().warning(
                        "[SimpleFactions] Invalid bracket: " + key +
                        " at " + config.getCurrentPath()
                    );
                }
            }
        }

        // ---- Global modifiers ----
        if (config.contains("modifiers")) {
            for (String mod : config.getStringList("modifiers")) {
                try {
                    globalModifiers.add(new FactionModifier(mod));
                } catch (Exception ignored) {}
            }
        }

        // ---- Region modifiers ----
        for (String key : config.getKeys(false)) {

            // Skip known non-region sections
            if (key.equalsIgnoreCase("rules")
             || key.equalsIgnoreCase("modifiers")) continue;

            try {
                Region region = Region.valueOf(key.toUpperCase());

                for (String mod : config.getStringList(key)) {
                    try {
                        addModifier(region, new FactionModifier(mod));
                    } catch (Exception e) {
                        Bukkit.getLogger().warning(
                            "[SimpleFactions] Invalid modifier: " + mod
                        );
                    }
                }

            } catch (IllegalArgumentException ignored) {
                // Not a region key → safe to ignore
            }
        }
    }

    public boolean affectsCouncilSize() {
        return councilSize != -1;
    }

    public boolean affectsCouncilType() {
        for(Rules rule : rules.keySet()) {
            if(rule == Rules.HAS_COUNCIL
            || rule == Rules.APPOINTED_COUNCIL
            || rule == Rules.WEALTH_BASED_COUNCIL
            || rule == Rules.ELECTED_COUNCIL) {
                return true;
            }
        }
        return false;
    }
    
    public Rules getCouncilType() {
        if(rules.containsKey(Rules.APPOINTED_COUNCIL)
        && rules.get(Rules.APPOINTED_COUNCIL)) {
            return Rules.APPOINTED_COUNCIL;
        }
        if(rules.containsKey(Rules.WEALTH_BASED_COUNCIL)
        && rules.get(Rules.WEALTH_BASED_COUNCIL)) {
            return Rules.WEALTH_BASED_COUNCIL;
        }
        if(rules.containsKey(Rules.ELECTED_COUNCIL)
        && rules.get(Rules.ELECTED_COUNCIL)) {
            return Rules.ELECTED_COUNCIL;
        }
        return Rules.NO_COUNCIL;
    }

    public int getCouncilSize() {
        return councilSize;
    }

    public void addModifier(Region region, FactionModifier mod) {
        regionModifiers
            .computeIfAbsent(region, r -> new ArrayList<>())
            .add(mod);
    }

    public List<FactionModifier> getGlobalModifiers() {
        return globalModifiers;
    }

    public Map<Region, List<FactionModifier>> getRegionModifiers() {
        return regionModifiers;
    }

    public Map<Rules, Boolean> getRules() {
        return rules;
    }

    public boolean hasGlobalModifiers() {
        return !globalModifiers.isEmpty();
    }

    public boolean hasRegionModifiers() {
        return !regionModifiers.isEmpty();
    }

    public boolean hasRules() {
        return !rules.isEmpty();
    }

    public boolean hasBrackets() {
        return !brackets.isEmpty();
    }

    public Map<Brackets, Bracket> getBrackets() {
        return brackets;
    }

    public boolean hasRegiments() {
        return !regiments.isEmpty();
    }

    public Map<Regiment, Integer> getRegiments() {
        return regiments;
    }

    public boolean affectsEconomy() {
        if(hasBrackets()) {
            for(Brackets b : brackets.keySet()) {
                if(b == Brackets.CITIZEN_TAX || b == Brackets.DIVIDEND_TAX) continue;
                return true;
            }
        }
        if(hasRules()) {
            for(Rules r : rules.keySet()) {
                if(r == Rules.VASSAL_TAX
                || r == Rules.GUILD_TAX
                || r == Rules.TARIFFS) {
                    return true;
                }
            }
        }
        for(FactionModifier mod : globalModifiers) {
            if(mod.getType().affectsEconomy()) return true;
        }
        for(List<FactionModifier> list : regionModifiers.values()) {
            for(FactionModifier mod : list) {
                if(mod.getType().affectsEconomy()) return true;
            }
        }
        return false;
    }

    public boolean prohibitsVassals() {
        if(hasRules()) {
            for(Rules r : rules.keySet()) {
                if(r == Rules.CAN_HAVE_VASSALS && !rules.get(r)) {
                    return true;
                }
            }
        }
        return false;
    }
}
