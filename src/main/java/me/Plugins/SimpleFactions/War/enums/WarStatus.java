package me.Plugins.SimpleFactions.War.enums;

public enum WarStatus {
	ACTIVE("active"),
	ENDED("ended");

	private final String jsonId;

	WarStatus(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static WarStatus fromJson(String value) {
		if (value == null || value.isBlank()) return ACTIVE;
		for (WarStatus status : values()) {
			if (status.jsonId.equalsIgnoreCase(value)) return status;
		}
		return null;
	}
}
