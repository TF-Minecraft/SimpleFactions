package net.tfminecraft.simplefactions.war.declare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.ProvinceManager;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.map.SeaConnectivity;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.settlement.Settlement;

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
		Set<String> ambiguousIds = new HashSet<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			for (Settlement settlement : faction.getSettlementHandler().getAll()) {
				if (!realm.contains(settlement.getCenterProvince())) {
					continue;
				}
				String id = settlement.getId();
				if (byId.putIfAbsent(id, settlement) != null) ambiguousIds.add(id);
			}
		}
		for (Settlement settlement : byId.values()) {
			options.add(ambiguousIds.contains(settlement.getId())
					? blocked(settlement, "§cMore than one settlement in their realm uses this id.")
					: evaluate(provinceManager, attacker, defender, settlement, realm));
		}
		return options;
	}

	public static PillageSettlementOption evaluate(Faction attacker, Faction defender, Settlement settlement) {
		return evaluate(provinceManager(), attacker, defender, settlement, realmProvinces(defender));
	}

	public static Settlement findSettlement(String settlementId) {
		return findInRealm(settlementId, null);
	}

	/** Settlement IDs are unique within a faction, so war targets must be resolved in the defender's realm. */
	public static Settlement findSettlement(String settlementId, Faction defender) {
		return defender == null ? null : findInRealm(settlementId, realmProvinces(defender));
	}

	private static Settlement findInRealm(String settlementId, Set<Integer> realm) {
		if (settlementId == null || settlementId.isBlank()) {
			return null;
		}
		Settlement match = null;
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			Settlement settlement = faction.getSettlementHandler().getById(settlementId);
			if (settlement != null && (realm == null || realm.contains(settlement.getCenterProvince()))) {
				if (match != null) return null;
				match = settlement;
			}
		}
		return match;
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
