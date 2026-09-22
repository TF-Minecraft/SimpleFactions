package me.Plugins.SimpleFactions.War.enums;

public enum CampaignPhase {
	INVASION("invasion"),
	RETAKE("retake"),
	COUNTER_PUSH("counter_push");

	private final String jsonId;

	CampaignPhase(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static CampaignPhase fromJson(String value) {
		if (value == null || value.isBlank()) {
			return INVASION;
		}
		for (CampaignPhase phase : values()) {
			if (phase.jsonId.equalsIgnoreCase(value)) {
				return phase;
			}
		}
		return null;
	}
}
