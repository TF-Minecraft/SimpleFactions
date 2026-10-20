package net.tfminecraft.simplefactions.diplomacy.coalition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.inactivity.InactivityService;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Diplomatic capacity a coalition seat costs: {@code base × max(1, r)^exponent}, where r
 * is the member's realm prestige over the median prestige of active factions. It does
 * not depend on who else is in the coalition, so a small member costs nobody anything.
 */
public final class CoalitionCost {
	private CoalitionCost() {}

	/** What the faction's current seat costs, or 0 when it is in no coalition. */
	public static double costFor(Faction faction) {
		Coalition coalition = CoalitionService.of(faction);
		if (coalition == null || !CoalitionSettings.enabled) return 0;
		return cost(faction, coalition.isLeader(faction.getId()));
	}

	/** What a seat would cost the faction, leading or not. */
	public static double cost(Faction faction, boolean leader) {
		double cost = CoalitionSettings.costBase
				* Math.pow(Math.max(1.0, ratio(faction)), CoalitionSettings.costExponent);
		return leader ? cost * CoalitionSettings.leaderMultiplier : cost;
	}

	/** Realm prestige over the median; 1 or less pays the base cost. */
	public static double ratio(Faction faction) {
		double median = medianPrestige();
		if (median <= 0) return 1.0;
		return realmPrestige(faction) / median;
	}

	/**
	 * The median own prestige of active factions. Inactive factions and factions with
	 * no prestige are left out, so a tail of abandoned factions cannot drag it down.
	 */
	public static double medianPrestige() {
		List<Double> values = new ArrayList<>();
		for (Faction faction : FactionManager.factions) {
			double prestige = prestige(faction);
			if (prestige <= 0) continue;
			if (InactivityService.isFullyInactive(faction.getMembers())) continue;
			values.add(prestige);
		}
		if (values.isEmpty()) return 0;
		Collections.sort(values);
		int middle = values.size() / 2;
		if (values.size() % 2 == 1) return values.get(middle);
		return (values.get(middle - 1) + values.get(middle)) / 2.0;
	}

	/** The faction's prestige plus that of every subject below it. */
	public static double realmPrestige(Faction faction) {
		return realmPrestige(faction, new HashSet<>());
	}

	private static double realmPrestige(Faction faction, Set<String> seen) {
		if (faction == null || !seen.add(faction.getId().toLowerCase())) return 0;
		double total = prestige(faction);
		for (Faction subject : RelationManager.getSubjects(faction)) {
			total += realmPrestige(subject, seen);
		}
		return total;
	}

	static double prestige(Faction faction) {
		Double prestige = faction.getPrestige();
		return prestige == null ? 0 : Math.max(0, prestige);
	}
}
