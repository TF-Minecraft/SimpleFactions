package me.Plugins.SimpleFactions.War.declare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.ProvinceManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Map.SeaConnectivity;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.settlement.Settlement;

public final class PillageEligibility {
	private PillageEligibility() {}

	public record PillageSettlementOption(Settlement settlement, boolean eligible, String blockReason) {}

	public static List<PillageSettlementOption> options(Faction attacker, Faction defender) {
		return options(provinceManager(), attacker, defender);
	}

	public static List<PillageSettlementOption> options(
			ProvinceManager provinceManager,
			Faction attacker,
			Faction defender) {
		List<PillageSettlementOption> options = new ArrayList<>();
		if (attacker == null || defender == null) {
			return options;
		}
		Set<Integer> realm = realmProvinces(defender);
		Map<String, Settlement> byId = new LinkedHashMap<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getSettlementHandler() == null) {
				continue;
			}
			for (Settlement settlement : faction.getSettlementHandler().getAll()) {
				if (settlement == null || settlement.getId() == null) {
					continue;
				}
				if (!realm.contains(settlement.getCenterProvince())) {
					continue;
				}
				byId.putIfAbsent(settlement.getId(), settlement);
			}
		}
		for (Settlement settlement : byId.values()) {
			options.add(evaluate(provinceManager, attacker, defender, settlement, realm));
		}
		return options;
	}

	public static PillageSettlementOption evaluate(Faction attacker, Faction defender, Settlement settlement) {
		return evaluate(provinceManager(), attacker, defender, settlement, realmProvinces(defender));
	}

	public static Settlement findSettlement(String settlementId) {
		if (settlementId == null || settlementId.isBlank()) {
			return null;
		}
		for (Faction faction : FactionManager.factions) {
			if (faction == null || faction.getSettlementHandler() == null) {
				continue;
			}
			Settlement settlement = faction.getSettlementHandler().getById(settlementId);
			if (settlement != null) {
				return settlement;
			}
		}
		return null;
	}

	public static Faction landOwner(int provinceId) {
		for (Faction faction : FactionManager.factions) {
			if (faction != null && faction.ownsProvince(provinceId)) {
				return faction;
			}
		}
		return null;
	}

	static PillageSettlementOption evaluate(
			ProvinceManager provinceManager,
			Faction attacker,
			Faction defender,
			Settlement settlement,
			Set<Integer> realm) {
		if (settlement == null) {
			return new PillageSettlementOption(null, false, "§cThat settlement does not exist.");
		}
		int center = settlement.getCenterProvince();
		if (attacker != null && attacker.ownsProvince(center)) {
			return blocked(settlement, "§cYou cannot pillage your own settlement.");
		}
		if (realm == null || !realm.contains(center)) {
			return blocked(settlement, "§cThat settlement is not in their realm.");
		}
		Faction owner = landOwner(center);
		if (owner == null) {
			owner = defender;
		}
		int range = Cache.pillageRangeProvinces;
		if (PillageRangeQueries.canPillageSettlement(
				provinceManager, attacker, settlement, owner, realm, range)) {
			return new PillageSettlementOption(settlement, true, null);
		}
		if (provinceManager != null
				&& PillageRangeQueries.inSeaRange(provinceManager, attacker, owner, center, range)) {
			return blocked(settlement, "§cThat settlement is out of pillage range.");
		}
		OptionalInt coast = provinceManager == null
				? OptionalInt.empty()
				: PillageRangeQueries.distanceToCoast(provinceManager, center);
		boolean seaLinked = provinceManager != null
				&& SeaConnectivity.hasSeaConnection(provinceManager, attacker, owner);
		if (coast.isPresent() && coast.getAsInt() <= range && !seaLinked) {
			return blocked(settlement, "§cNo sea connection to that settlement.");
		}
		return blocked(settlement, "§cThat settlement is out of pillage range.");
	}

	private static PillageSettlementOption blocked(Settlement settlement, String reason) {
		return new PillageSettlementOption(settlement, false, reason);
	}

	private static Set<Integer> realmProvinces(Faction defender) {
		if (defender == null) {
			return Set.of();
		}
		return new HashSet<>(TitleManager.getProvinces(defender));
	}

	private static ProvinceManager provinceManager() {
		SimpleFactions plugin = SimpleFactions.getInstance();
		return plugin == null ? null : plugin.getProvinceManager();
	}
}
