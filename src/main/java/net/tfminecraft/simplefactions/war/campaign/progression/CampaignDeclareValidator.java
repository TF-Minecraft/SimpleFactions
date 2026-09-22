package net.tfminecraft.simplefactions.war.campaign.progression;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.declare.WarValidationResult;

public final class CampaignDeclareValidator {
	private CampaignDeclareValidator() {}

	public static WarValidationResult validateAttackerCanDeclare(Faction attacker) {
		if (attacker == null || attacker.getMilitary() == null) {
			return WarValidationResult.fail("§cCould not declare war.");
		}
		if (attacker.getMilitary().getManpower(true) < 1) {
			return WarValidationResult.fail("§cYou need at least one offensive regiment to declare war.");
		}
		return WarValidationResult.ok();
	}
}
