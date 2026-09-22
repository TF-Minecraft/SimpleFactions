package me.Plugins.SimpleFactions.enums;

/**
 * Unused leftover types from the removed YAML war-goal picker.
 * Declare and resolution use {@link me.Plugins.SimpleFactions.War.enums.WarGoalType}.
 */
public enum Goal {
	/** Legacy YAML id {@code annex}; maps to v2 {@code de_jure_annex}. */
	ANNEX,
	SUBJUGATE,
	/** @deprecated Legacy only; not a v2 declare goal. */
	@Deprecated REVOLT,
	/** @deprecated Legacy only; not a v2 declare goal. */
	@Deprecated TRIBUTARY,
	/** @deprecated Legacy only; not a v2 declare goal. */
	@Deprecated INDEPENDENCE,
	/** @deprecated Legacy only; not a v2 declare goal. */
	@Deprecated WAR_REPARATIONS,
	TRANSFER_SUBJECT,
	/** @deprecated Legacy only; not a v2 declare goal. */
	@Deprecated USURP,
}
