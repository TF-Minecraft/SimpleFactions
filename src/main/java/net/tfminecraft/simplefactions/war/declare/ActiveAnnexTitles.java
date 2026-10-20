package net.tfminecraft.simplefactions.war.declare;

import java.util.List;

import net.tfminecraft.simplefactions.loaders.TitleLoader;
import net.tfminecraft.simplefactions.managers.TitleManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.tiers.Title;
import net.tfminecraft.simplefactions.war.core.War;
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
			if (war == null || war.getGoal() != WarGoalType.DE_JURE_ANNEX) {
				continue;
			}
			if (faction != null && !againstRealmOf(war, faction)) {
				continue;
			}
			if (titleContains(war.getTargetTitleId(), provinceId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean againstRealmOf(War war, Faction faction) {
		Faction defender = war.getDefenders() == null ? null : war.getDefenders().getLeader();
		return defender != null && faction.getId() != null && defender.getId() != null
				&& DeJureAnnexEligibility.inDefenderRealm(faction, defender);
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
