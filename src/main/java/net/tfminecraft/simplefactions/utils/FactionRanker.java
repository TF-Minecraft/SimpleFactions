package net.tfminecraft.simplefactions.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.enums.RankType;

public class FactionRanker {
    public Double visibleGuildValue(org.bukkit.entity.Player viewer, Guild guild, RankType type) {
        String metric = type == RankType.MEMBERS ? "Members" : type == RankType.INCOME ? "Income"
                : type == RankType.TRADE_POWER ? "Trade power" : "Wealth";
        return net.tfminecraft.simplefactions.espionage.EspionageService.visibleValue(viewer, guild.getFaction(),
                "Guild:" + guild.getId() + ":" + metric,
                () -> type == RankType.MEMBERS ? guild.getMembers().size() : type == RankType.INCOME ? guild.getLedger().getNetIncome()
                        : type == RankType.TRADE_POWER ? guild.getTradeBreakdown().getTradePower() : guild.getWealth());
    }

    public List<Guild> getVisibleRankedGuildList(org.bukkit.entity.Player viewer, RankType type) {
        List<Guild> guilds = new java.util.ArrayList<>(FactionManager.getAllGuilds());
        java.util.Map<Guild, Double> values = new java.util.HashMap<>();
        for (Guild guild : guilds) values.put(guild, visibleGuildValue(viewer, guild, type));
        guilds.sort((first, second) -> {
            Double a = values.get(first), b = values.get(second);
            if (a == null && b != null) return 1;
            if (a != null && b == null) return -1;
            int comparison = a == null ? 0 : Double.compare(b, a);
            return comparison != 0 ? comparison : first.getId().compareToIgnoreCase(second.getId());
        });
        return guilds;
    }

    public Integer getVisibleGuildRank(org.bukkit.entity.Player viewer, Guild target, RankType type) {
        if (visibleGuildValue(viewer, target, type) == null) return null;
        var ranked = getVisibleRankedGuildList(viewer, type);
        for (int index = 0; index < ranked.size(); index++) if (ranked.get(index).getId().equals(target.getId())) return index + 1;
        return null;
    }
    public Double visibleValue(org.bukkit.entity.Player viewer, Faction faction, RankType type) {
        String metric = type == RankType.PRESTIGE ? "Prestige" : type == RankType.MEMBERS ? "Members" : "Wealth";
        return net.tfminecraft.simplefactions.espionage.EspionageService.visibleValue(viewer, faction, metric,
                () -> type == RankType.PRESTIGE ? faction.getPrestige()
                        : type == RankType.MEMBERS ? faction.getMembers().size() : faction.getWealth());
    }

    public List<Faction> getVisibleRankedList(org.bukkit.entity.Player viewer, RankType type) {
        List<Faction> factions = FactionManager.getCopy();
        java.util.Map<Faction, Double> values = new java.util.HashMap<>();
        for (Faction faction : factions) values.put(faction, visibleValue(viewer, faction, type));
        factions.sort((first, second) -> {
            Double a = values.get(first), b = values.get(second);
            if (a == null && b != null) return 1;
            if (a != null && b == null) return -1;
            int comparison = a == null ? 0 : Double.compare(b, a);
            return comparison != 0 ? comparison : first.getId().compareToIgnoreCase(second.getId());
        });
        return factions;
    }

    public Integer getVisibleRank(org.bukkit.entity.Player viewer, Faction target, RankType type) {
        if (visibleValue(viewer, target, type) == null) return null;
        List<Faction> ranked = getVisibleRankedList(viewer, type);
        for (int index = 0; index < ranked.size(); index++) if (ranked.get(index).getId().equals(target.getId())) return index + 1;
        return null;
    }
	public Integer getPrestigeRank(Faction f) {
		List<Faction> factions = getRankedList(RankType.PRESTIGE);
		Collections.reverse(factions);
		for(int i = 0; i<factions.size(); i++) {
			if(f.getId().equals(factions.get(i).getId())) return i+1;
		}
		return 0;
	}
	public Integer getWealthRank(Faction f) {
		List<Faction> factions = getRankedList(RankType.WEALTH);
		Collections.reverse(factions);
		for(int i = 0; i<factions.size(); i++) {
			if(f.getId().equals(factions.get(i).getId())) return i+1;
		}
		return 0;
	}
	public List<Faction> getRankedList(RankType t){
		List<Faction> f = FactionManager.getCopy();
		if(t.equals(RankType.PRESTIGE)) {
			Collections.sort(f, new Comparator<Faction>() {
			    @Override
			    public int compare(Faction f1, Faction f2) {
			        return Double.compare(f1.getPrestige(), f2.getPrestige());
			    }
			});
		} else if(t.equals(RankType.WEALTH)){
			Collections.sort(f, new Comparator<Faction>() {
			    @Override
			    public int compare(Faction f1, Faction f2) {
			        return Double.compare(f1.getWealth(), f2.getWealth());
			    }
			});
		} else if(t.equals(RankType.MEMBERS)){
			Collections.sort(f, new Comparator<Faction>() {
			    @Override
			    public int compare(Faction f1, Faction f2) {
			        return Integer.compare(f1.getMembers().size(), f2.getMembers().size());
			    }
			});
		}
		return f;
	}

	public List<Guild> getRankedGuildList(RankType t){
		List<Guild> guilds = FactionManager.getAllGuilds();
		switch (t) {
			case TRADE_POWER:
				Collections.sort(guilds, new Comparator<Guild>() {
					@Override
					public int compare(Guild f1, Guild f2) {
						return Double.compare(f1.getTradeBreakdown().getTradePower(), f2.getTradeBreakdown().getTradePower());
					}
				});
				break;
			case INCOME:
				Collections.sort(guilds, new Comparator<Guild>() {
					@Override
					public int compare(Guild f1, Guild f2) {
						return Double.compare(f1.getLedger().getNetIncome(), f2.getLedger().getNetIncome());
					}
				});
				break;
			case WEALTH:
				Collections.sort(guilds, new Comparator<Guild>() {
					@Override
					public int compare(Guild f1, Guild f2) {
						return Double.compare(f1.getWealth(), f2.getWealth());
					}
				});
				break;
			case MEMBERS:
				Collections.sort(guilds, new Comparator<Guild>() {
					@Override
					public int compare(Guild f1, Guild f2) {
						return Integer.compare(f1.getMembers().size(), f2.getMembers().size());
					}
				});
				break;
			default:
				break;
		}
		return guilds;
	}

	public Integer getWealthRank(Guild guild) {
		List<Guild> guilds = getRankedGuildList(RankType.WEALTH);
		Collections.reverse(guilds);
		for(int i = 0; i<guilds.size(); i++) {
			if(guild.getId().equals(guilds.get(i).getId())) return i+1;
		}
		return 0;
	}
}
