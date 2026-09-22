package me.Plugins.SimpleFactions.War.battle.enums;

public enum BattleType {
	FIELD("field"),
	SIEGE("siege"),
	RAID("raid");

	private final String jsonId;

	BattleType(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static BattleType fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (BattleType type : values()) {
			if (type.jsonId.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}
