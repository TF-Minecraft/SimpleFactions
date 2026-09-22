package me.Plugins.SimpleFactions.War.declare;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Diplomacy.RelationType;
import me.Plugins.SimpleFactions.Loaders.RelationLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.TitleManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.settlement.handler.SettlementHandler;

public final class DeJureAnnexEligibility {
	private DeJureAnnexEligibility() {}

	public record DeJureTitleOption(Title title, boolean eligible, String blockReason) {}

	public static List<DeJureTitleOption> options(Faction attacker, Faction defender) {
		List<DeJureTitleOption> options = new ArrayList<>();
		if (attacker == null || defender == null) {
			return options;
		}
		for (Title title : TitleLoader.getTitles()) {
			if (title == null) {
				continue;
			}
			if (incomingLandCount(attacker, defender, title) < 1) {
				continue;
			}
			options.add(evaluate(attacker, defender, title));
		}
		return options;
	}

	public static DeJureTitleOption evaluate(Faction attacker, Faction defender, Title title) {
		if (attacker == null || defender == null || title == null) {
			return new DeJureTitleOption(title, false, "§cThat title does not exist.");
		}

		int incoming = incomingLandCount(attacker, defender, title);
		if (incoming < 1) {
			return blocked(title, "§cThat faction holds no land in this title.");
		}

		int attackerTier = attacker.getTier() != null ? attacker.getTier().getTier() : 0;
		int titleTier = title.getTier() != null ? title.getTier().getTier() : 0;
		if (!WarGoalValidator.canAnnexByRank(attackerTier, titleTier)) {
			return blocked(title, "§cYou cannot de jure annex a title above your rank.");
		}

		if (!attackerOwnsTitle(attacker, title)) {
			Faction owner = TitleManager.getOwner(title);
			if (owner != null) {
				return blocked(title, "§cYou do not own this title.");
			}
			if (!attackerOwnsProvinceInTitle(attacker, title)) {
				return blocked(title, "§cYou do not own a province in this unowned title.");
			}
		}

		List<Integer> titleProvinces = TitleManager.getProvinces(title);
		Set<Integer> provinceSet = new HashSet<>(titleProvinces);
		if (WarGoalValidator.titleProvincesContainSettlement(
				provinceSet, collectSettlementProbes(), collectCapitals())) {
			if (canSuggestSubjugate(attacker, defender)) {
				return blocked(title, "§cThis title has settlements - use subjugate instead.");
			}
			return blocked(title, "§cThis title has settlements.");
		}

		if (overProjectedProvinceCap(attacker, incoming)) {
			return blocked(title, "§cYou do not have enough prestige for the incoming provinces.");
		}

		return new DeJureTitleOption(title, true, null);
	}

	static int incomingLandCount(Faction attacker, Faction defender, Title title) {
		return incomingProvinces(attacker, defender, title).size();
	}

	public static List<Integer> incomingProvinces(Faction attacker, Faction defender, Title title) {
		List<Integer> incoming = new ArrayList<>();
		if (attacker == null || defender == null || title == null) {
			return incoming;
		}
		List<Integer> titleProvinces = TitleManager.getProvinces(title);
		if (titleProvinces == null || titleProvinces.isEmpty()) {
			return incoming;
		}
		for (Integer provinceId : titleProvinces) {
			if (provinceId == null) {
				continue;
			}
			Faction holder = ownerOfProvince(provinceId);
			if (holder != null && inDefenderRealm(holder, defender) && !holder.getId().equalsIgnoreCase(attacker.getId())) {
				incoming.add(provinceId);
			}
		}
		return incoming;
	}

	private static DeJureTitleOption blocked(Title title, String reason) {
		return new DeJureTitleOption(title, false, reason);
	}

	private static boolean attackerOwnsTitle(Faction attacker, Title title) {
		List<Title> titles = attacker.getTitles();
		if (titles == null || title.getId() == null) {
			return false;
		}
		for (Title held : titles) {
			if (held != null && held.getId() != null && held.getId().equalsIgnoreCase(title.getId())) {
				return true;
			}
		}
		return false;
	}

	private static boolean attackerOwnsProvinceInTitle(Faction attacker, Title title) {
		List<Integer> attackerProvinces = attacker.getProvinces();
		List<Integer> titleProvinces = TitleManager.getProvinces(title);
		if (attackerProvinces == null || titleProvinces == null) {
			return false;
		}
		for (Integer provinceId : titleProvinces) {
			if (provinceId != null && attackerProvinces.contains(provinceId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inDefenderRealm(Faction holder, Faction defender) {
		if (holder.getId().equalsIgnoreCase(defender.getId())) {
			return true;
		}
		return RelationManager.isOnOverlordPath(holder, defender);
	}

	public static Faction ownerOfProvince(int provinceId) {
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			List<Integer> provinces = faction.getProvinces();
			if (provinces != null && provinces.contains(provinceId)) {
				return faction;
			}
		}
		return null;
	}

	private static boolean overProjectedProvinceCap(Faction attacker, int incoming) {
		List<Integer> provinces = attacker.getProvinces();
		int current = provinces == null ? 0 : provinces.size();
		Double prestige = attacker.getPrestige();
		double prestigeValue = prestige == null ? 0 : prestige;
		return prestigeValue < Math.max(0, current + incoming - 1) * Cache.provinceCost;
	}

	private static boolean canSuggestSubjugate(Faction attacker, Faction defender) {
		RelationType type = firstPickableTypeNotAtLimit(attacker);
		if (type == null) {
			return false;
		}
		WarDeclareRequest request = new WarDeclareRequest(
				attacker, defender, WarGoalType.SUBJUGATE, null, null, type.getId());
		return new WarGoalValidator().validate(request).isValid();
	}

	private static RelationType firstPickableTypeNotAtLimit(Faction attacker) {
		for (RelationType type : RelationLoader.getWarPickableVassalTypes()) {
			if (type != null && !RelationManager.atLimit(attacker, type)) {
				return type;
			}
		}
		return null;
	}

	private static List<WarGoalValidator.SettlementProbe> collectSettlementProbes() {
		List<WarGoalValidator.SettlementProbe> probes = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			SettlementHandler handler = faction.getSettlementHandler();
			if (handler == null) {
				continue;
			}
			handler.getAll().forEach(s -> probes.add(new WarGoalValidator.SettlementProbe(s.getCenterProvince())));
		}
		return probes;
	}

	private static List<Integer> collectCapitals() {
		List<Integer> capitals = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			if (faction == null) {
				continue;
			}
			capitals.add(faction.getCapital());
		}
		return capitals;
	}
}
