package me.Plugins.SimpleFactions.Diplomacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.FactionModifier;
import me.Plugins.SimpleFactions.enums.FactionModifiers;
import me.Plugins.SimpleFactions.enums.GuildModifier;

public class DiplomacyHandler {
    private Faction f;
    private HashMap<String, Relation> relations = new HashMap<>();
    private HashMap<String, RelationType> tradeRelations = new HashMap<>();
    private HashMap<String, RelationType> treatyRelations = new HashMap<>();

    public DiplomacyHandler(Faction f) {
        this.f = f;
    }

    public double getDiplomaticCapacity() {
        double base = f.getOrCreateMainGuild().getModifier(GuildModifier.DIPLOMATIC_CAPACITY);
        base *= 1+f.getModifier(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER).getAmount()/100.0;
        base *= f.getGovernment().getStability()/100.0;
        return base;
    }

    public double getUsedDiplomaticCapacity() {
        double used = 0;
        for(Map.Entry<String, Relation> entry : relations.entrySet()) {
            Relation r = entry.getValue();
            Faction from = FactionManager.getByString(entry.getKey());
            if(from == null) continue;
            used += RelationManager.getDiplomaticCost(f, from, r.getType());
            used += RelationManager.getDiplomaticCost(f, from, r.getAttitude());
        }
        for(Map.Entry<String, RelationType> entry : tradeRelations.entrySet()) {
            RelationType r = entry.getValue();
            Faction from = FactionManager.getByString(entry.getKey());
            if(from == null) continue;
            used += RelationManager.getDiplomaticCost(f, from, r);
        }
        for(Map.Entry<String, RelationType> entry : treatyRelations.entrySet()) {
            RelationType r = entry.getValue();
            Faction from = FactionManager.getByString(entry.getKey());
            if(from == null) continue;
            used += RelationManager.getDiplomaticCost(f, from, r);
        }
        return used;
    }

    public double getAvailableCapacity() {
        return getDiplomaticCapacity() - getUsedDiplomaticCapacity();
    }

    public HashMap<String, Relation> getRelations(){
		return relations;
	}
	
	public Relation getRelation(String s) {
		if(relations.containsKey(s)) return relations.get(s);
		return new Relation();
	}

    public void setRelation(Faction f, Relation r) {
		Relation previous = relations.get(f.getId());
		relations.put(f.getId(), r);
		LogManager.relations(
				"SET %s -> %s was=%s now=%s",
				this.f.getId(),
				f.getId(),
				FactionManager.describeRelation(previous),
				FactionManager.describeRelation(r));
	}
	
	public void updateRelations() {
        for(String s : tradeRelations.keySet()) {
            if(!relations.containsKey(s)) {
				LogManager.relations("DEFAULT-FILL %s -> %s (trade partner, no diplomatic map entry)", this.f.getId(), s);
                relations.put(s, new Relation()); //default addition so we can tick it
            }
        }
        for(String s : treatyRelations.keySet()) {
            if(!relations.containsKey(s)) {
				LogManager.relations("DEFAULT-FILL %s -> %s (treaty partner, no diplomatic map entry)", this.f.getId(), s);
                relations.put(s, new Relation());
            }
        }
		for(Map.Entry<String, Relation> entry : relations.entrySet()) {
			entry.getValue().tick(entry.getKey(), this);
		}
	}

    public void removeRelation(String s) {
		Relation previous = relations.get(s);
		relations.remove(s);
		LogManager.relations("REMOVE %s -> %s was=%s", this.f.getId(), s, FactionManager.describeRelation(previous));
    }

    public HashMap<String, RelationType> getTradeRelations() {
        return tradeRelations;
    }

    public boolean hasTradeRelation(String s) {
        return tradeRelations.containsKey(s);
    }

    public void removeTradeRelation(String s) {
        tradeRelations.remove(s);
    }

    public RelationType getTradeRelation(String s) {
        if(tradeRelations.containsKey(s)) return tradeRelations.get(s);
        return null;
    }

    public void setTradeRelation(Faction f, RelationType r) {
        tradeRelations.put(f.getId(), r);
    }

    public HashMap<String, RelationType> getTreatyRelations() {
        return treatyRelations;
    }

    public boolean hasTreatyRelation(String s) {
        return treatyRelations.containsKey(s);
    }

    public void removeTreatyRelation(String s) {
        treatyRelations.remove(s);
    }

    public RelationType getTreatyRelation(String s) {
        if(treatyRelations.containsKey(s)) return treatyRelations.get(s);
        return null;
    }

    public void setTreatyRelation(Faction f, RelationType r) {
        treatyRelations.put(f.getId(), r);
    }

    public List<FactionModifier> getTradeModifiersFor(String target) {
        List<FactionModifier> mods = new ArrayList<>();
        RelationType r = getTradeRelation(target);
        if(r == null) return mods;
        if(r.hasTradeEffectsThem()) {
            for(FactionModifier mod : r.getTradeEffectsThem()) {
                mods.add(new FactionModifier(FactionManager.getByString(target), mod));
            }
        }
        return mods;
    }

    public List<FactionModifier> getModifiers() {
        List<FactionModifier> mods = new ArrayList<>();
        for(Map.Entry<String, Relation> entry : relations.entrySet()) {
            Relation r = entry.getValue();
            if(r.getType().hasRecieveModifiers()) {
                for(FactionModifier mod : r.getType().getRecieveModifiers()) {
                    mods.add(new FactionModifier(FactionManager.getByString(entry.getKey()), mod));
                }
            }
            if(r.getAttitude() != null && r.getAttitude().hasRecieveModifiers()) {
                for(FactionModifier mod : r.getAttitude().getRecieveModifiers()) {
                    mods.add(new FactionModifier(FactionManager.getByString(entry.getKey()), mod));
                }
            }
            Faction other = FactionManager.getByString(entry.getKey());
            if(other == null) continue;
            Relation back = other.getRelation(f.getId());
            if(back == null || back.getType() == null) continue;
            if(back.getType().hasGiveModifiers()) {
                for(FactionModifier mod : back.getType().getGiveModifiers()) {
                    mods.add(new FactionModifier(FactionManager.getByString(entry.getKey()), mod));
                }
            }
        }
        return mods;
    }
}
