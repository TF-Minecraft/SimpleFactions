package net.tfminecraft.simplefactions.government.stability;

public enum StabilityStatus {
	STABLE("Stable State", 1.0, 1.0, 0, true, true),
	STRAINED("Strained State", 1.0, 1.0, 10, true, true),
	STRUGGLING("Struggling State", 0.75, 1.5, 20, true, true),
	FAILING("Failing State", 0.5, 2.0, 30, true, true),
	COLLAPSING("Collapsing State", 0.5, 2.0, 30, false, false),
	FAILED("Failed State", 0.5, 2.0, 30, false, false);

	private final String label;
	private final double adminFactor;
	private final double upkeepFactor;
	private final int deJureBonus;
	private final boolean canWageWar;
	private final boolean canFormTitles;

	StabilityStatus(String label, double adminFactor, double upkeepFactor, int deJureBonus,
			boolean canWageWar, boolean canFormTitles) {
		this.label = label;
		this.adminFactor = adminFactor;
		this.upkeepFactor = upkeepFactor;
		this.deJureBonus = deJureBonus;
		this.canWageWar = canWageWar;
		this.canFormTitles = canFormTitles;
	}

	public String getLabel() {
		return label;
	}

	/** Tax collection. Full through Strained, 75% in a Struggling State, 50% from Failing down. */
	public double getTaxFactor() {
		return adminFactor;
	}

	public double getAdminFactor() {
		return adminFactor;
	}

	public double getUpkeepFactor() {
		return upkeepFactor;
	}

	public int getDeJureBonus() {
		return deJureBonus;
	}

	public boolean canWageWar() {
		return canWageWar;
	}

	public boolean canFormTitles() {
		return canFormTitles;
	}

	public String getListColor() {
		return switch (this) {
			case STABLE -> "#45c46f";
			case STRAINED -> "#d1b43f";
			case STRUGGLING -> "#c76734";
			case FAILING -> "#d13530";
			case COLLAPSING -> "#a32020";
			case FAILED -> "#8f1d1d";
		};
	}

	public static StabilityStatus fromStability(double stability) {
		if (stability >= 75) return STABLE;
		if (stability >= 60) return STRAINED;
		if (stability >= 40) return STRUGGLING;
		if (stability >= 20) return FAILING;
		if (stability >= 1) return COLLAPSING;
		return FAILED;
	}
}
