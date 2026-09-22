package me.Plugins.SimpleFactions.War.campaign.raid;

public enum CampaignRaidState {
	MUSTER,
	FIGHTING,
	ENDED;

	public static CampaignRaidState fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (CampaignRaidState state : values()) {
			if (state.name().equalsIgnoreCase(value)) {
				return state;
			}
		}
		return null;
	}

	public String toJson() {
		return name().toLowerCase();
	}
}
