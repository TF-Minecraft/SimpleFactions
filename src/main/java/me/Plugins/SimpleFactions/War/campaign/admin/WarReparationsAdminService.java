package me.Plugins.SimpleFactions.War.campaign.admin;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsService;

public final class WarReparationsAdminService {
	public static final String USAGE =
			"§cUsage: /war admin reparations <fromFaction> <toFaction> [percent] [days]";

	private WarReparationsAdminService() {}

	public record ApplyResult(boolean ok, String message) {}

	public static ApplyResult apply(String fromFactionId, String toFactionId, String percentArg, String daysArg) {
		if (fromFactionId == null || fromFactionId.isBlank() || toFactionId == null || toFactionId.isBlank()) {
			return new ApplyResult(false, USAGE);
		}
		Faction payer = FactionManager.getByString(fromFactionId);
		Faction payee = FactionManager.getByString(toFactionId);
		if (payer == null) {
			return new ApplyResult(false, "§cUnknown faction: " + fromFactionId);
		}
		if (payee == null) {
			return new ApplyResult(false, "§cUnknown faction: " + toFactionId);
		}

		double percent = Cache.warReparationsIncomePercent;
		int days = Cache.warReparationsDays;
		if (percentArg != null && !percentArg.isBlank()) {
			try {
				percent = Double.parseDouble(percentArg);
			} catch (NumberFormatException ignored) {
				return new ApplyResult(false, "§cPercent must be a number greater than 0.");
			}
		}
		if (daysArg != null && !daysArg.isBlank()) {
			try {
				days = Integer.parseInt(daysArg);
			} catch (NumberFormatException ignored) {
				return new ApplyResult(false, "§cDays must be a whole number greater than 0.");
			}
		}
		if (percent <= 0 || days <= 0) {
			return new ApplyResult(false, "§cPercent and days must be greater than 0.");
		}

		if (!WarReparationsService.apply(payer, payee, percent, days)) {
			return new ApplyResult(false, "§cCould not add reparations (same faction or invalid).");
		}
		new Database().saveFaction(payer);
		return new ApplyResult(
				true,
				"§aAdded reparations: "
						+ payer.getId()
						+ " pays "
						+ payee.getId()
						+ " "
						+ formatPercent(percent)
						+ "% of main-guild income for "
						+ days
						+ " days.");
	}

	private static String formatPercent(double percent) {
		if (percent == Math.rint(percent)) {
			return String.valueOf((long) percent);
		}
		return String.valueOf(percent);
	}
}
