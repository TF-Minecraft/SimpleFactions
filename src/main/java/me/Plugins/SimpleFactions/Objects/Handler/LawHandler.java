package me.Plugins.SimpleFactions.Objects.Handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.LawLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.ModifierMerger;
import me.Plugins.SimpleFactions.enums.Region;
import me.Plugins.SimpleFactions.enums.Scope;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawEffect;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class LawHandler {
    private Faction f;
    private Map<String, LawGroup> laws = new LinkedHashMap<>();

    public List<Law> getCurrentLaws() {
        List<Law> lawList = new ArrayList<>();
        for(LawGroup group : getGroupList()) {
            if(group.getCurrent() == null) continue;
            lawList.add(group.getCurrent());
        }
        return lawList;
    }

    public LawHandler(Faction f) {
        this.f = f;
        for(Map.Entry<String, LawGroup> entry : LawLoader.get().entrySet()) {
            laws.put(entry.getKey(), new LawGroup(f, entry.getValue()));
        }
    }

    public void apply() {
        for(LawGroup group : laws.values()) {
            Law current = group.getCurrent();
            if(current != null) {
                f.applyLaw(current, group);
            }
        }
    }

    public LawHandler(Faction f, List<String> lawData) {
        this.f = f;
        for(Map.Entry<String, LawGroup> entry : LawLoader.get().entrySet()) {
            laws.put(entry.getKey(), new LawGroup(f, entry.getValue()));
        }
        if (lawData == null || lawData.isEmpty()) return;
        for (String lawEntry : lawData) {
            String[] split = lawEntry.split(":");
            if (split.length == 2) {
                String groupId = split[0];
                String lawId = split[1];
                LawGroup group = getGroup(groupId);
                if (group != null) {
                    Law law = group.getLaws().get(lawId);
                    if (law != null) {
                        group.setCurrent(law);
                    }
                }
            }
        }
    }

    public List<LawGroup> getGroupList() {
        return new ArrayList<>(laws.values());
    }

    public LawGroup getGroup(String id) {
        return laws.getOrDefault(id, null);
    }

    public Law getLaw(String group, String law) {
        LawGroup lawGroup = getGroup(group);
        if (lawGroup == null) return null;
        return lawGroup.getLaws().getOrDefault(law, null);
    }

    public List<FactionModifier> getLawModifiers(String id, Scope scope, Region region) {
        List<FactionModifier> result = new ArrayList<>();

        if(id != null && (scope == Scope.DOMESTIC_GUILDS || scope == Scope.VASSALS || scope == Scope.VASSAL_GUILDS)) {
            // Add base effects for guild scopes
            Scope secondary = Scope.FACTION;
            Guild guild = FactionManager.getGuildByString(id);
            if (guild == null) {
                String factionId = f != null ? f.getId() : "unknown";
                String message = "[SimpleFactions] Law modifiers: guild not found id=" + id
                        + " faction=" + factionId + " scope=" + scope;
                if (SimpleFactions.plugin != null) {
                    SimpleFactions.plugin.getLogger().warning(message);
                } else {
                    System.out.println(message);
                }
            } else {
                switch (scope) {
                    case DOMESTIC_GUILDS:
                        if(guild.isFavoured()) secondary = Scope.FAVOURED_GUILDS;
                        else if(guild.isRepressed()) secondary = Scope.REPRESSED_GUILDS;
                    case VASSALS:
                        if(guild.isFavoured()) secondary = Scope.FAVOURED_VASSALS;
                        else if(guild.isRepressed()) secondary = Scope.REPRESSED_VASSALS;
                    case VASSAL_GUILDS:
                        if(guild.isFavoured()) secondary = Scope.FAVOURED_VASSALS;
                        else if(guild.isRepressed()) secondary = Scope.REPRESSED_VASSALS;
                    default:
                        break;
                }
                if(secondary != Scope.FACTION) {
                    LawEffect baseEffect = Cache.baseEffects.get(secondary);
                    if(baseEffect != null) {
                        if(baseEffect.hasGlobalModifiers()) result.addAll(baseEffect.getGlobalModifiers());
                        if(region != null && baseEffect.hasRegionModifiers()) {
                            List<FactionModifier> regionMods = baseEffect.getRegionModifiers().get(region);
                            if(regionMods != null) result.addAll(regionMods);
                        }
                    }
                }
            }
        }

        for (LawGroup group : laws.values()) {

            Law current = group.getCurrent();
            if (current == null || !current.hasEffects()) continue;

            LawEffect effect = current.getScopedEffects().get(scope);
            if (effect == null) continue;

            // ---- Global (scope-only) modifiers ----
            if (effect.hasGlobalModifiers()) {
                result.addAll(effect.getGlobalModifiers());
            }

            // ---- Region-specific modifiers ----
            if (region != null && effect.hasRegionModifiers()) {
                List<FactionModifier> regionMods =
                        effect.getRegionModifiers().get(region);

                if (regionMods != null) {
                    result.addAll(regionMods);
                }
            }
        }

        return ModifierMerger.merge(result);
    }
}
