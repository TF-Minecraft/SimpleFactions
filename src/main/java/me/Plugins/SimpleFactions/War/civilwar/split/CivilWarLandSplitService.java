package me.Plugins.SimpleFactions.War.civilwar.split;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Map.Provinces.Province;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.installation.InstallationTransferService;
import me.Plugins.SimpleFactions.settlement.Settlement;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

public final class CivilWarLandSplitService {
	private CivilWarLandSplitService() {}

	@FunctionalInterface
	public interface TilePresence {
		double score(int provinceId, Guild guild);
	}

	public record LandSplitPlan(List<Integer> rebelProvinceIds, List<Integer> loyalProvinceIds) {
		public LandSplitPlan {
			rebelProvinceIds = List.copyOf(rebelProvinceIds);
			loyalProvinceIds = List.copyOf(loyalProvinceIds);
		}
	}

	public static LandSplitPlan plan(Faction host, List<Guild> supportingHostGuilds) {
		return plan(host, supportingHostGuilds, defaultPresence());
	}

	public static LandSplitPlan plan(Faction host, List<Guild> supportingHostGuilds, TilePresence presence) {
		if (host == null || host.getProvinces() == null || host.getProvinces().isEmpty()) {
			return null;
		}
		List<Integer> provinces = new ArrayList<>(host.getProvinces());
		int capital = host.getCapital();
		Set<String> rebelGuildIds = new LinkedHashSet<>();
		List<Guild> rebels = supportingHostGuilds == null ? List.of() : supportingHostGuilds;
		for (Guild guild : rebels) {
			if (guild != null && guild.getId() != null) {
				rebelGuildIds.add(guild.getId());
			}
		}
		List<Guild> loyal = new ArrayList<>();
		if (host.getGuildHandler() != null) {
			for (Guild guild : host.getGuildHandler().getGuilds()) {
				if (guild == null || guild.getId() == null) {
					continue;
				}
				if (!rebelGuildIds.contains(guild.getId())) {
					loyal.add(guild);
				}
			}
		}

		if (provinces.size() == 2) {
			Integer other = null;
			for (int provinceId : provinces) {
				if (provinceId != capital) {
					other = provinceId;
					break;
				}
			}
			if (other == null || capital <= 0) {
				return null;
			}
			return new LandSplitPlan(List.of(other), List.of(capital));
		}

		List<Integer> rebelTiles = new ArrayList<>();
		List<Integer> loyalTiles = new ArrayList<>();
		TilePresence scorer = presence == null ? defaultPresence() : presence;
		for (int provinceId : provinces) {
			double rebelScore = 0;
			for (Guild guild : rebels) {
				rebelScore += scorer.score(provinceId, guild);
			}
			double loyalScore = 0;
			for (Guild guild : loyal) {
				loyalScore += scorer.score(provinceId, guild);
			}
			if (rebelScore > loyalScore) {
				rebelTiles.add(provinceId);
			} else {
				loyalTiles.add(provinceId);
			}
		}
		if (rebelTiles.isEmpty() || loyalTiles.isEmpty()) {
			return null;
		}
		return new LandSplitPlan(rebelTiles, loyalTiles);
	}

	public static void apply(Faction host, Faction rebels, LandSplitPlan plan) {
		if (host == null || rebels == null || plan == null) {
			return;
		}
		LogManager.civilwar(
				"LAND_PLAN host=%s rebels=%s rebelProvinces=%s loyalProvinces=%s",
				host.getId(),
				rebels.getId(),
				plan.rebelProvinceIds(),
				plan.loyalProvinceIds());
		for (int provinceId : plan.rebelProvinceIds()) {
			rebels.addProvince(provinceId);
			InstallationTransferService.transfer(host, rebels, provinceId);
			transferSettlement(host, rebels, provinceId);
			host.removeProvince(provinceId, false);
		}
	}

	public static void rollback(Faction host, Faction rebels, LandSplitPlan plan) {
		if (host == null || rebels == null || plan == null) {
			return;
		}
		for (int provinceId : plan.rebelProvinceIds()) {
			host.addProvince(provinceId);
			InstallationTransferService.transfer(rebels, host, provinceId);
			transferSettlement(rebels, host, provinceId);
			rebels.removeProvince(provinceId, false);
		}
	}

	static TilePresence defaultPresence() {
		return (provinceId, guild) -> {
			if (guild == null) {
				return 0;
			}
			SimpleFactions plugin = SimpleFactions.getInstance();
			if (plugin == null) {
				return 0;
			}
			ProvinceManager pm = plugin.getProvinceManager();
			if (pm == null) {
				return 0;
			}
			Province province = pm.get(provinceId);
			if (province == null) {
				return 0;
			}
			return province.getIncome(guild) * province.getTradeFactor(guild);
		};
	}

	private static void transferSettlement(Faction from, Faction to, int provinceId) {
		if (from == null || to == null) {
			return;
		}
		SettlementHandler fromHandler = from.getSettlementHandler();
		SettlementHandler toHandler = to.getSettlementHandler();
		if (fromHandler == null || toHandler == null) {
			return;
		}
		Settlement settlement = fromHandler.detachOnProvince(provinceId);
		if (settlement != null) {
			toHandler.acceptTransferred(settlement);
			LogManager.civilwar(
					"SETTLEMENT_MOVE province=%d id=%s name=%s center=%d from=%s to=%s",
					provinceId,
					settlement.getId(),
					settlement.getName(),
					settlement.getCenterProvince(),
					from.getId(),
					to.getId());
		} else {
			LogManager.civilwar(
					"SETTLEMENT_MISS province=%d from=%s to=%s",
					provinceId,
					from.getId(),
					to.getId());
		}
	}
}
