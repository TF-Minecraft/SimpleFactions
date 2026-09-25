package net.tfminecraft.simplefactions.laws;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.TaxHandler;
import net.tfminecraft.simplefactions.enums.Scope;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class LawGroup {
    private String id;
    private String name;
    private Law current;
    private long changedAt; // epoch millis of the last switch, 0 if never
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
    public Law getCurrent() {
        Law overlay = IncomePreviewContext.overlayLaw(this);
        return overlay != null ? overlay : current;
    }
    public Map<String, Law> getLaws() { return laws; }
    public List<String> getDescription() { return description; }
    public boolean hasDescription() { return !description.isEmpty(); }
    public void setCurrent(Law law) {
        if(!laws.containsValue(law)) return;
        current = law;
    }

    /** Sets the law and, if it differs from the current one, starts the switch lock. */
    public void switchTo(Law law, long now) {
        if(!laws.containsValue(law)) return;
        if(law != current) changedAt = now;
        current = law;
    }

    public long getChangedAt() { return changedAt; }
    public void setChangedAt(long changedAt) { this.changedAt = changedAt; }

    /** Millis left before this group may be switched again, 0 if it may be now. */
    public long lockRemaining(long now) {
        if(changedAt <= 0 || Cache.lawSwitchLockDays <= 0) return 0;
        long until = changedAt + (long) (Cache.lawSwitchLockDays * 86_400_000L);
        return Math.max(0, until - now);
    }
}
