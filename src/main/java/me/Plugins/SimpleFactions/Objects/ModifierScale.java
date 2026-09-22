package me.Plugins.SimpleFactions.Objects;

public final class ModifierScale {
	public static final double PRESTIGE_RATIO_K = 2.5;
	private static final double EPSILON = 1e-6;

	public enum Kind {
		NONE,
		RELATIVE_PRESTIGE
	}

	private ModifierScale() {}

	public static Kind kindFrom(String raw) {
		if (raw == null || raw.isBlank()) {
			return Kind.NONE;
		}
		if (raw.equalsIgnoreCase("relative_prestige")) {
			return Kind.RELATIVE_PRESTIGE;
		}
		return Kind.NONE;
	}

	public static double relativePrestige(
			double theirPrestige,
			double ourPrestige,
			double atWeaker,
			double atEqual,
			double atStronger) {
		double ours = Math.max(ourPrestige, EPSILON);
		double theirs = Math.max(theirPrestige, 0);
		double r = theirs / ours;
		if (r <= 0) {
			return atWeaker;
		}
		double t = Math.log(r) / Math.log(PRESTIGE_RATIO_K);
		t = Math.max(-1.0, Math.min(1.0, t));
		if (t <= 0) {
			return lerp(atWeaker, atEqual, t + 1.0);
		}
		return lerp(atEqual, atStronger, t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
