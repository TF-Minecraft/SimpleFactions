package net.tfminecraft.simplefactions.war.declare;

import java.util.List;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.core.WarDemand;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;

/** Titles under an active de jure annex demand, so defenders cannot shield that land mid-war. */
public final class ActiveAnnexTitles {
	private ActiveAnnexTitles() {}

	/** Whether {@code provinceId} lies in the target title of any active de jure annex war. */
	public static boolean isContested(int provinceId) {
		return isContested(provinceId, null);
	}

	/**
	 * Whether {@code provinceId} lies in the target title of an active de jure annex war
	 * against {@code faction}'s realm; any defender when {@code faction} is null.
	 */
	public static boolean isContested(int provinceId, Faction faction) {
		for (War war : WarManager.getActive()) {
			Faction defender = war.getDefenders() == null ? null : war.getDefenders().getLeader();
			if (war.getGoal() == WarGoalType.DE_JURE_ANNEX
					&& claims(defender, war.getTargetTitleId(), provinceId, faction)) {
				return true;
			}
			// A second demand can annex a title too, from either side's target.
			for (boolean attackerSide : new boolean[] {true, false}) {
				WarDemand demand = war.getSecondDemand(attackerSide);
				if (demand != null && demand.getGoal() == WarGoalType.DE_JURE_ANNEX
						&& claims(demand.getTarget(), demand.getTargetTitleId(), provinceId, faction)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean claims(Faction target, String titleId, int provinceId, Faction faction) {
		if (faction != null && !inRealmOf(target, faction)) {
			return false;
		}
		return titleContains(titleId, provinceId);
	}

	private static boolean inRealmOf(Faction target, Faction faction) {
		return target != null && faction.getId() != null && target.getId() != null
				&& DeJureAnnexEligibility.inDefenderRealm(faction, target);
	}

	private static boolean titleContains(String titleId, int provinceId) {
		if (titleId == null || titleId.isBlank()) {
			return false;
		}
		Title title = TitleLoader.getById(titleId);
		if (title == null) {
			return false;
		}
		List<Integer> provinces = TitleManager.getProvinces(title);
		return provinces != null && provinces.contains(provinceId);
	}
}
