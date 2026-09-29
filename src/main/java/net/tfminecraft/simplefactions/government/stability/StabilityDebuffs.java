package net.tfminecraft.simplefactions.government.stability;

/**
 * How far below 100 stability the slow debuffs have climbed.
 * Tax stays on the status brackets. Foreign trade and prestige are linear
 * from 100 stability down to 0. Admin, diplomacy, upkeep, and de jure wait
 * on the square and reach their floor at {@link StabilityTuning#slowDebuffSpan}
 * points below 100.
 */
public final class StabilityDebuffs {

	private StabilityDebuffs() {}

	public static double slowFactor(double stability, StabilityTuning tuning) {
		double span = tuning.slowDebuffSpan <= 0 ? 80 : tuning.slowDebuffSpan;
		double missing = Math.max(0, 100 - stability);
		if (missing >= span) return 1;
		double fraction = missing / span;
		return fraction * fraction;
	}

	public static double adminFactor(double stability, StabilityTuning tuning) {
		return 1 - tuning.slowAdminLoss * slowFactor(stability, tuning);
	}

	public static double upkeepFactor(double stability, StabilityTuning tuning) {
		return 1 + tuning.slowUpkeepGain * slowFactor(stability, tuning);
	}

	public static double deJureBonus(double stability, StabilityTuning tuning) {
		return tuning.slowDeJureMax * slowFactor(stability, tuning);
	}

	public static double prestigeMalus(double stability, StabilityTuning tuning) {
		double missing = Math.max(0, Math.min(100, 100 - stability));
		return tuning.slowPrestigeMax * missing / 100.0;
	}

	/** Share of its production and trade power the realm guild keeps. Other guilds stay at 1. */
	public static double realmOutputFactor(double stability) {
		return Math.max(0, Math.min(100, stability)) / 100.0;
	}

	public static double realmSeed(net.tfminecraft.simplefactions.guild.Guild guild, double amount) {
		if (guild == null || !guild.isBase() || amount == 0) return amount;
		net.tfminecraft.simplefactions.objects.Faction host = guild.getFaction();
		if (host == null || host.getGovernment() == null) return amount;
		return amount * realmOutputFactor(host.getGovernment().getStability());
	}

	/** Extra trade power for a foreign guild inside this nation's provinces. 0 at full stability. */
	public static double foreignTradeBonus(double stability, StabilityTuning tuning) {
		double missing = Math.max(0, Math.min(100, 100 - stability));
		return tuning.foreignTradeMax * missing / 100.0;
	}
}
