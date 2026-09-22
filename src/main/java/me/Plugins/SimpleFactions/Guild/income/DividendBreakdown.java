package me.Plugins.SimpleFactions.Guild.income;

public record DividendBreakdown(
		double base,
		double pool,
		double tax,
		double payout,
		int eligibleCount,
		double perMember) {

	public static DividendBreakdown none() {
		return new DividendBreakdown(0.0, 0.0, 0.0, 0.0, 0, 0.0);
	}
}
