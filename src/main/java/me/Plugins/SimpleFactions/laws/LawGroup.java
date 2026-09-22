package me.Plugins.SimpleFactions.laws;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Handler.TaxHandler;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class LawGroup {
    private String id;
    private String name;
    private Law current;
    private Map<String, Law> laws = new LinkedHashMap<>();
    private List<String> description = new ArrayList<>();

    public LawGroup(String key, ConfigurationSection config) {
        id = key;
        name = StringFormatter.formatHex(config.getString("name", id));
        for(String lawKey : config.getConfigurationSection("laws").getKeys(false)) {
            laws.put(lawKey, new Law(id, lawKey, config.getConfigurationSection("laws."+lawKey)));
        }
        if(config.contains("description")) {
            for(String s : config.getStringList("description")) 
                description.add(StringFormatter.formatHex(s));
        }
    }

    public LawGroup(Faction f, LawGroup another) {
        id = another.id;
        name = another.name;
        description = another.description;
        for(Map.Entry<String, Law> entry : another.getLaws().entrySet()) {
            laws.put(entry.getKey(), entry.getValue());
        }
        current = getFirstAvailable(f);
    }

    public Law getFirstAvailable(Faction f) {
        for(Law law : laws.values()) {
            if(law.isAvailable(f)) {
                return law;
            }
        }
        return new ArrayList<>(laws.values()).get(0);
    }

    public Law getLaw(String lawId) {
        return laws.get(lawId);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Law getCurrent() { return current; }
    public Map<String, Law> getLaws() { return laws; }
    public List<String> getDescription() { return description; }
    public boolean hasDescription() { return !description.isEmpty(); }
    public void setCurrent(Law law) {
        if(!laws.containsValue(law)) return;
        current = law;
    }
}
