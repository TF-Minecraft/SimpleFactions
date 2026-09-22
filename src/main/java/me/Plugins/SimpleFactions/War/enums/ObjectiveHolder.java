package me.Plugins.SimpleFactions.War.enums;

public enum ObjectiveHolder {
	ATTACKER("attacker"),
	DEFENDER("defender");

	private final String jsonId;

	ObjectiveHolder(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static ObjectiveHolder fromJson(String value) {
		if (value == null || value.isBlank()) {
			return DEFENDER;
		}
		for (ObjectiveHolder holder : values()) {
			if (holder.jsonId.equalsIgnoreCase(value)) {
				return holder;
			}
		}
		return null;
	}
}
