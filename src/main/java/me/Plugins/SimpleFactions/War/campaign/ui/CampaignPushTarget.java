package me.Plugins.SimpleFactions.War.campaign.ui;

public enum CampaignPushTarget {
	TOWARD_OBJECTIVE("toward_objective"),
	TOWARD_AGGRESSOR_CAPITAL("toward_aggressor_capital"),
	RETAKE_OBJECTIVE("retake_objective");

	private final String jsonId;

	CampaignPushTarget(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static CampaignPushTarget fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (CampaignPushTarget target : values()) {
			if (target.jsonId.equalsIgnoreCase(value)) {
				return target;
			}
		}
		return null;
	}
}
