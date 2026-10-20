package net.tfminecraft.simplefactions.war.core;

import java.util.Optional;

import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;

public final class WarCommandHelper {
	private WarCommandHelper() {}

	public static Optional<Integer> parseWarId(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Integer.parseInt(value));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public static BelligerentRole parseBelligerentRoleArg(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (value.equalsIgnoreCase("attacker")) {
			return BelligerentRole.ATTACKER;
		}
		if (value.equalsIgnoreCase("defender")) {
			return BelligerentRole.DEFENDER;
		}
		return null;
	}

	/** @return coalition for one side, or {@code null} for both */
	public static CampaignSideRole parseCoalitionScope(String value) {
		if (value == null || value.isBlank() || value.equalsIgnoreCase("both")) {
			return null;
		}
		if (value.equalsIgnoreCase("aggressor") || value.equalsIgnoreCase("attacker")) {
			return CampaignSideRole.AGGRESSOR;
		}
		if (value.equalsIgnoreCase("defender")) {
			return CampaignSideRole.DEFENDER;
		}
		return CampaignSideRole.fromJson(value);
	}

	public static boolean isValidCoalitionScope(String value) {
		if (value == null || value.isBlank() || value.equalsIgnoreCase("both")) {
			return true;
		}
		return parseCoalitionScope(value) != null;
	}
}
