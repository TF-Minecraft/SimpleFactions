package me.Plugins.SimpleFactions.War.enums;

public enum CampaignBattleKind {
	FIELD("field"),
	SIEGE("siege"),
	NAVAL("naval"),
	NAVAL_INVASION("naval_invasion");

	private final String jsonId;

	CampaignBattleKind(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static CampaignBattleKind fromJson(String value) {
		if (value == null || value.isBlank()) {
			return FIELD;
		}
		for (CampaignBattleKind kind : values()) {
			if (kind.jsonId.equalsIgnoreCase(value) || kind.name().equalsIgnoreCase(value)) {
				return kind;
			}
		}
		return FIELD;
	}
}
