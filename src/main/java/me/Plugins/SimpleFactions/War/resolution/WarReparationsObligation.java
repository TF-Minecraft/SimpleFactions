package me.Plugins.SimpleFactions.War.resolution;

public final class WarReparationsObligation {
	private final String payeeFactionId;
	private final double incomePercent;
	private int daysRemaining;

	public WarReparationsObligation(String payeeFactionId, double incomePercent, int daysRemaining) {
		this.payeeFactionId = payeeFactionId;
		this.incomePercent = incomePercent;
		this.daysRemaining = daysRemaining;
	}

	public String getPayeeFactionId() {
		return payeeFactionId;
	}

	public double getIncomePercent() {
		return incomePercent;
	}

	public int getDaysRemaining() {
		return daysRemaining;
	}

	public void setDaysRemaining(int daysRemaining) {
		this.daysRemaining = daysRemaining;
	}

	public boolean isActive() {
		return daysRemaining > 0 && payeeFactionId != null && !payeeFactionId.isBlank();
	}
}
