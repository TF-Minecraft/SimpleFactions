package me.Plugins.SimpleFactions.War.enums;

public enum WarGoalType {
	DE_JURE_ANNEX("de_jure_annex"),
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
	FORCE_PEACE("force_peace");

	private final String jsonId;

	WarGoalType(String jsonId) {
		this.jsonId = jsonId;
	}

	public String toJson() {
		return jsonId;
	}

	public String getDisplayName() {
		return switch (this) {
			case DE_JURE_ANNEX -> "De Jure Annex";
			case SUBJUGATE -> "Subjugate";
			case TRANSFER_SUBJECT -> "Transfer Subject";
			case WAR -> "War";
			case TRIBUTARY -> "Tributary";
			case USURP -> "Usurp";
			case OPEN_MARKET -> "Open Market";
			case CHANGE_GOVERNMENT -> "Change Government";
			case PILLAGE -> "Pillage";
			case OVERTHROW -> "Overthrow";
			case CHANGE_LAW -> "Change Law";
			case CHANGE_TAX -> "Change Tax";
			case FORCE_PEACE -> "Force Peace";
		};
	}

	public boolean isMovementOrigin() {
		return this == OVERTHROW || this == CHANGE_LAW || this == CHANGE_TAX || this == FORCE_PEACE;
	}

	public static WarGoalType fromJson(String value) {
		if (value == null || value.isBlank()) return null;
		for (WarGoalType type : values()) {
			if (type.jsonId.equalsIgnoreCase(value)) return type;
		}
		return null;
	}
}
