package me.Plugins.SimpleFactions.War.battle.enums;

public enum DefenderRespawnMode {
	INFINITE("infinite"),
	LIVES("lives");

	private final String jsonId;

	DefenderRespawnMode(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static DefenderRespawnMode fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (DefenderRespawnMode mode : values()) {
			if (mode.jsonId.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
				return mode;
			}
		}
		return null;
	}
}
