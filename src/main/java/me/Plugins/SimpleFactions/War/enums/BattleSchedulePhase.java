package me.Plugins.SimpleFactions.War.enums;

public enum BattleSchedulePhase {
	IDLE("idle"),
	VOTING("voting"),
	SCHEDULED("scheduled"),
	AUTORESOLVE_PENDING("autoresolve_pending");

	private final String jsonId;

	BattleSchedulePhase(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static BattleSchedulePhase fromJson(String value) {
		if (value == null || value.isBlank()) {
			return IDLE;
		}
		for (BattleSchedulePhase phase : values()) {
			if (phase.jsonId.equalsIgnoreCase(value)) {
				return phase;
			}
		}
		return null;
	}
}
