package me.Plugins.SimpleFactions.War.enums;

public enum WarType {
	DE_JURE("de_jure"),
	SUBJUGATE("subjugate"),
	TRANSFER_SUBJECT("transfer_subject"),
	WAR("war"),
	TRIBUTARY("tributary"),
	USURP("usurp"),
	OPEN_MARKET("open_market"),
	CHANGE_GOVERNMENT("change_government"),
	PILLAGE("pillage"),
	OVERTHROW("overthrow"),
	CHANGE_LAW("change_law"),
	CHANGE_TAX("change_tax"),
	RAID("raid");

	private final String jsonId;

	WarType(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public static WarType fromJson(String value) {
		if (value == null || value.isBlank()) return null;
		for (WarType type : values()) {
			if (type.jsonId.equalsIgnoreCase(value)) return type;
		}
		return null;
	}
}
