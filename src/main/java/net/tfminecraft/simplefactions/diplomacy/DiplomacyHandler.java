package net.tfminecraft.simplefactions.diplomacy;

import net.tfminecraft.simplefactions.diplomacy.coalition.CoalitionCost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.LogManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.FactionModifier;
import net.tfminecraft.simplefactions.enums.FactionModifiers;
import net.tfminecraft.simplefactions.enums.GuildModifier;
import net.tfminecraft.simplefactions.guild.income.IncomePreviewContext;

public class DiplomacyHandler {
    private Faction f;
    private HashMap<String, Relation> relations = new HashMap<>();
    private HashMap<String, RelationType> tradeRelations = new HashMap<>();
    private HashMap<String, RelationType> treatyRelations = new HashMap<>();
    /** Partner faction id -> epoch millis when the notice we gave to end our treaty with them runs out. */
    private HashMap<String, Long> treatyNotices = new HashMap<>();

    public DiplomacyHandler(Faction f) {
        this.f = f;
    }

    public double getDiplomaticCapacity() {
        double base = f.getOrCreateMainGuild().getModifier(GuildModifier.DIPLOMATIC_CAPACITY);
        base *= 1+f.getModifier(FactionModifiers.DIPLOMATIC_CAPACITY_MULTIPLIER).getAmount()/100.0;
        net.tfminecraft.simplefactions.government.stability.StabilityReport report = f.getGovernment().stateReport();
        base *= net.tfminecraft.simplefactions.government.stability.StabilityDebuffs.adminFactor(
				report.stability,
				net.tfminecraft.simplefactions.government.stability.StabilityTuning.get());
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
        used += CoalitionCost.costFor(f);
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
        if (IncomePreviewContext.overridesTrade(this, s)) {
            return IncomePreviewContext.overlayTrade(this, s) != null;
        }
        return tradeRelations.containsKey(s);
    }

    public void removeTradeRelation(String s) {
        tradeRelations.remove(s);
    }

    public RelationType getTradeRelation(String s) {
        if (IncomePreviewContext.overridesTrade(this, s)) {
            return IncomePreviewContext.overlayTrade(this, s);
        }
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
        treatyNotices.remove(s);
    }

    public RelationType getTreatyRelation(String s) {
        if(treatyRelations.containsKey(s)) return treatyRelations.get(s);
        return null;
    }

    public void setTreatyRelation(Faction f, RelationType r) {
        treatyRelations.put(f.getId(), r);
    }

    public HashMap<String, Long> getTreatyNotices() {
        return treatyNotices;
    }

    /** When the notice we gave to end our treaty with s runs out, or null when we gave none. */
    public Long getTreatyNoticeEnd(String s) {
        return treatyNotices.get(s);
    }

    public void setTreatyNoticeEnd(String s, long endsAt) {
        treatyNotices.put(s, endsAt);
    }

    public void clearTreatyNotice(String s) {
        treatyNotices.remove(s);
    }

    /**
     * Trade modifiers on the target faction's guilds in this faction's provinces. Each side keeps its own half
     * of a trade relation: this faction's record gives what visiting guilds get ("their guilds in our
     * territory"), and the target's record towards this faction gives what its own guilds get here ("our
     * guilds in their territory"). Reading both records applies one-sided treaties, such as the leader's half
     * of an unequal treaty, the way the Diplomacy menu shows them. Both lookups go through
     * {@link #getTradeRelation}, so income previews see proposed relations on either side.
     */
    public List<FactionModifier> getTradeModifiersFor(String target) {
        Faction visitor = FactionManager.getByString(target);
        return tradeModifiersFor(target, visitor);
    }

    /** {@link #getTradeModifiersFor(String)} for a faction already in hand, without the lookup by id. */
    public List<FactionModifier> getTradeModifiersFor(Faction visitor) {
        if (visitor == null || visitor.getId() == null) {
            return new ArrayList<>();
        }
        return tradeModifiersFor(visitor.getId(), visitor);
    }

    private List<FactionModifier> tradeModifiersFor(String target, Faction visitor) {
        List<FactionModifier> mods = new ArrayList<>();
        RelationType ours = getTradeRelation(target);
        if (ours != null) {
            for (FactionModifier mod : ours.getTradeEffectsThemRaw()) {
                mods.add(new FactionModifier(visitor, mod));
            }
        }
        RelationType theirs = visitor == null || visitor.getDiplomacyHandler() == null || f == null
                ? null
                : visitor.getDiplomacyHandler().getTradeRelation(f.getId());
        if (theirs != null) {
            for (FactionModifier mod : theirs.getTradeEffectsUsRaw()) {
                mods.add(new FactionModifier(visitor, mod));
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
