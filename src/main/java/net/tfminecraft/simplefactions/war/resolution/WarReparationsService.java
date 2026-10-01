package net.tfminecraft.simplefactions.war.resolution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.managers.RelationManager;
import net.tfminecraft.simplefactions.war.core.War;

public final class WarReparationsService {
	private WarReparationsService() {}

	public static void applyFromWar(War war) {
		if (war == null || war.getAttackers() == null || war.getDefenders() == null) {
			return;
		}
		apply(war.getAttackers().getLeader(), war.getDefenders().getLeader());
	}

	public static boolean apply(Faction payer, Faction payee) {
		return apply(payer, payee, Cache.warReparationsIncomePercent, Cache.warReparationsDays);
	}

	public static boolean apply(Faction payer, Faction payee, double percent, int days) {
		if (payer == null || payee == null || payer.getId() == null || payee.getId() == null) {
			return false;
		}
		if (payer.getId().equalsIgnoreCase(payee.getId())) {
			return false;
		}
		if (days <= 0 || percent <= 0) {
			return false;
		}
		for (Faction includedPayer : payerAndVassals(payer)) {
			includedPayer.addWarReparationsObligation(
					new WarReparationsObligation(payee.getId(), percent, days));
		}
		return true;
	}

	/** Returns the defeated faction and its full vassal tree, once each. */
	private static List<Faction> payerAndVassals(Faction root) {
		Set<String> visitedIds = new LinkedHashSet<>();
		List<Faction> result = new ArrayList<>();
		collectPayers(root, visitedIds, result);
		return result;
	}

	private static void collectPayers(Faction faction, Set<String> visitedIds, List<Faction> result) {
		if (faction == null || faction.getId() == null || !visitedIds.add(faction.getId().toLowerCase())) {
			return;
		}
		result.add(faction);
		List<Faction> subjects;
		try {
			subjects = RelationManager.getSubjects(faction);
		} catch (RuntimeException ignored) {
			// A faction being removed during settlement has no subjects to process.
			return;
		}
		if (subjects == null) {
			return;
		}
		for (Faction subject : subjects) {
			collectPayers(subject, visitedIds, result);
		}
	}

	public static void tickAfterDailySettlement(Faction payer) {
		if (payer == null) {
			return;
		}
		List<WarReparationsObligation> obligations = payer.getWarReparationsObligations();
		if (obligations == null || obligations.isEmpty()) {
			return;
		}
		Iterator<WarReparationsObligation> iterator = obligations.iterator();
		while (iterator.hasNext()) {
			WarReparationsObligation obligation = iterator.next();
			if (obligation == null) {
				iterator.remove();
				continue;
			}
			obligation.setDaysRemaining(obligation.getDaysRemaining() - 1);
			if (obligation.getDaysRemaining() <= 0) {
				iterator.remove();
			}
		}
	}

	public static List<WarReparationsObligation> activeObligations(Faction payer) {
		if (payer == null || payer.getWarReparationsObligations() == null) {
			return List.of();
		}
		List<WarReparationsObligation> active = new ArrayList<>();
		for (WarReparationsObligation obligation : payer.getWarReparationsObligations()) {
			if (obligation != null && obligation.isActive()) {
				active.add(obligation);
			}
		}
		return active;
	}

	public static WarReparationsObligation findObligation(Faction payer, Faction payee) {
		if (payer == null || payee == null || payee.getId() == null) {
			return null;
		}
		for (WarReparationsObligation obligation : activeObligations(payer)) {
			if (payee.getId().equalsIgnoreCase(obligation.getPayeeFactionId())) {
				return obligation;
			}
		}
		return null;
	}
}
