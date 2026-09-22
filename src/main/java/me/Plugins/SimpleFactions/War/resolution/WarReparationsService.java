package me.Plugins.SimpleFactions.War.resolution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;

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
		payer.addWarReparationsObligation(new WarReparationsObligation(payee.getId(), percent, days));
		return true;
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
