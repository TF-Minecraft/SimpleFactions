package me.Plugins.SimpleFactions.War.enums;

public enum WarEndReason {
	ADMIN_END("admin_end"),
	WHITE_PEACE("white_peace"),
	ATTACKER_VICTORY("attacker_victory"),
	DEFENDER_VICTORY("defender_victory");

	private final String jsonId;

	WarEndReason(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static WarEndReason fromJson(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (WarEndReason reason : values()) {
			if (reason.jsonId.equalsIgnoreCase(value)) {
				return reason;
			}
		}
		return null;
	}
}
