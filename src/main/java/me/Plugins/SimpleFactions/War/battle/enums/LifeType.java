package me.Plugins.SimpleFactions.War.battle.enums;

public enum LifeType {
	COLLECTIVE;

	public String toJson() {
		return name();
	}

	public static LifeType fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if ("PER_PLAYER".equalsIgnoreCase(value)) {
			return COLLECTIVE;
		}
		for (LifeType type : values()) {
			if (type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}
