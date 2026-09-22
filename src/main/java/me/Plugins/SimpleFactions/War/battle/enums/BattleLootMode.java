package me.Plugins.SimpleFactions.War.battle.enums;

public enum BattleLootMode {
	COMMAND("command"),
	ITEM("item");

	private final String jsonId;

	BattleLootMode(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static BattleLootMode fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (BattleLootMode mode : values()) {
			if (mode.jsonId.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
				return mode;
			}
		}
		return null;
	}
}
