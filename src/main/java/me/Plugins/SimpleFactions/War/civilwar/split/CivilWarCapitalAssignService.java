package me.Plugins.SimpleFactions.War.civilwar.split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.Utils.Formatter;
import me.Plugins.SimpleFactions.War.civilwar.split.CivilWarLandSplitService.LandSplitPlan;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

public final class CivilWarCapitalAssignService {
	static final String REBEL_CAMP_NAME = "Rebel Camp";

	private CivilWarCapitalAssignService() {}

	public static int assign(
			Faction host,
			Faction rebels,
			LandSplitPlan plan,
			int hostOldCapital,
			Map<String, Integer> rebelGuildOldCapitals) {
		int rebelCapital = assignFactionCapitals(rebels, plan.rebelProvinceIds(), rebelGuildOldCapitals);
		if (plan.rebelProvinceIds().contains(hostOldCapital)
				|| hostSettlementMissing(host, hostOldCapital)) {
			assignHostCapital(host, plan.loyalProvinceIds(), hostOldCapital);
		}
		return rebelCapital;
	}

	static boolean hostSettlementMissing(Faction host, int capital) {
		if (host == null || capital <= 0 || host.getSettlementHandler() == null) {
			return capital <= 0;
		}
		if (!host.hasProvince(capital)) {
			return true;
		}
		return host.getSettlementHandler().getByProvince(capital) == null;
	}

	private static int assignFactionCapitals(
			Faction rebels,
			List<Integer> directLand,
			Map<String, Integer> rebelGuildOldCapitals) {
		int factionSeat = pickSeat(rebels, directLand, preferredOldCapital(rebels, rebelGuildOldCapitals));
		if (factionSeat > 0) {
			rebels.setCapital(factionSeat, true, false);
		}
		if (rebels.getGuildHandler() != null) {
			for (Guild guild : rebels.getGuildHandler().getGuilds()) {
				if (guild == null || guild.isBase()) {
					continue;
				}
				int old = oldCapitalFor(guild, rebelGuildOldCapitals);
				int seat = pickSeat(rebels, directLand, old);
				if (seat > 0) {
					guild.setCapital(seat, false);
				} else {
					guild.setCapital(-1, false);
				}
			}
		}
		return factionSeat;
	}

	private static void assignHostCapital(Faction host, List<Integer> loyalLand, int hostOldCapital) {
		int seat = pickSeat(host, loyalLand, hostOldCapital);
		if (seat > 0) {
			host.setCapital(seat, true, false);
		} else if (hostOldCapital > 0) {
			host.setCapital(-1, true, false);
		}
	}

	private static int preferredOldCapital(Faction rebels, Map<String, Integer> rebelGuildOldCapitals) {
		if (rebels.getGuildHandler() == null) {
			return -1;
		}
		Guild main = rebels.getOrCreateMainGuild();
		if (main != null) {
			int old = oldCapitalFor(main, rebelGuildOldCapitals);
			if (old > 0) {
				return old;
			}
		}
		if (rebelGuildOldCapitals != null) {
			for (Integer value : rebelGuildOldCapitals.values()) {
				if (value != null && value > 0) {
					return value;
				}
			}
		}
		return -1;
	}

	private static int oldCapitalFor(Guild guild, Map<String, Integer> rebelGuildOldCapitals) {
		if (guild == null || guild.getId() == null || rebelGuildOldCapitals == null) {
			return -1;
		}
		Integer old = rebelGuildOldCapitals.get(guild.getId());
		return old == null ? -1 : old;
	}

	static int pickSeat(Faction faction, List<Integer> directLand, int preferredProvince) {
		List<Settlement> cities = directSettlements(faction, directLand);
		if (preferredProvince > 0) {
			for (Settlement city : cities) {
				if (city.getCenterProvince() == preferredProvince) {
					return preferredProvince;
				}
			}
		}
		Settlement nearest = nearest(cities, preferredProvince, directLand);
		if (nearest != null) {
			return nearest.getCenterProvince();
		}
		return foundRebelCamp(faction, directLand);
	}

	static List<Settlement> directSettlements(Faction faction, List<Integer> directLand) {
		List<Settlement> cities = new ArrayList<>();
		if (faction == null || faction.getSettlementHandler() == null || directLand == null) {
			return cities;
		}
		for (Settlement settlement : faction.getSettlementHandler().getAll()) {
			if (settlement != null && directLand.contains(settlement.getCenterProvince())) {
				cities.add(settlement);
			}
		}
		return cities;
	}

	private static Settlement nearest(
			List<Settlement> cities,
			int fromProvince,
			List<Integer> directLand) {
		if (cities.isEmpty()) {
			return null;
		}
		int origin = fromProvince > 0 ? fromProvince : (directLand == null || directLand.isEmpty() ? -1 : directLand.get(0));
		Settlement best = null;
		double bestScore = Double.POSITIVE_INFINITY;
		for (Settlement city : cities) {
			double score = distance(origin, city.getCenterProvince());
			if (score < bestScore) {
				bestScore = score;
				best = city;
			}
		}
		return best;
	}

	static double distance(int fromProvince, int toProvince) {
		if (fromProvince <= 0 || toProvince <= 0) {
			return Math.abs(fromProvince - toProvince);
		}
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin == null || plugin.getProvinceManager() == null) {
			return Math.abs(fromProvince - toProvince);
		}
		Province from = plugin.getProvinceManager().get(fromProvince);
		Province to = plugin.getProvinceManager().get(toProvince);
		if (from == null || to == null) {
			return Math.abs(fromProvince - toProvince);
		}
		double dx = from.getCenterX() - to.getCenterX();
		double dz = from.getCenterZ() - to.getCenterZ();
		return dx * dx + dz * dz;
	}

	static int foundRebelCamp(Faction faction, List<Integer> directLand) {
		if (faction == null || faction.getSettlementHandler() == null || directLand == null) {
			return -1;
		}
		SettlementHandler handler = faction.getSettlementHandler();
		Settlement existing = handler.getById(Formatter.formatId(REBEL_CAMP_NAME));
		if (existing != null && directLand.contains(existing.getCenterProvince())) {
			return existing.getCenterProvince();
		}
		int tile = emptyDirectTile(handler, directLand);
		if (tile <= 0) {
			return -1;
		}
		int x = 0;
		int z = 0;
		SimpleFactions plugin = SimpleFactions.getInstance();
		if (plugin != null && plugin.getProvinceManager() != null) {
			Province province = plugin.getProvinceManager().get(tile);
			if (province != null) {
				x = province.getCenterX();
				z = province.getCenterZ();
			}
		}
		handler.found(REBEL_CAMP_NAME, tile, x, z);
		LogManager.civilwar(
				"REBEL_CAMP faction=%s province=%d",
				faction.getId(),
				tile);
		Settlement camp = handler.getByProvince(tile);
		return camp == null ? -1 : camp.getCenterProvince();
	}

	private static int emptyDirectTile(SettlementHandler handler, List<Integer> directLand) {
		for (int provinceId : directLand) {
			if (handler.getByProvince(provinceId) == null) {
				return provinceId;
			}
		}
		return directLand.isEmpty() ? -1 : directLand.get(0);
	}
}
