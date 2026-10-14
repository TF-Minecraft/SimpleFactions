package net.tfminecraft.simplefactions.war.battle.campaign.warband;

import java.util.UUID;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Ties an account to the coalition of its first warband signup in a war, so a
 * player cannot fight both sides by switching character. Held until the war ends.
 */
public final class CampaignWarbandSideLock {
	public static final String OTHER_SIDE =
			"You already fight for the other side of this war. You can change sides when the war ends.";

	private CampaignWarbandSideLock() {
	}

	/** The war coalition a battle side belongs to. */
	public static CampaignCoalition coalition(War war, Battle battle, String battleSideId) {
		return CampaignCoalitionService.coalitionOf(war, CampaignBattleSides.warSideFor(war, battle, battleSideId));
	}

	/** Null when the account may sign up for that coalition. */
	public static String refusal(War war, CampaignCoalition coalition, UUID accountId) {
		if (war == null || coalition == null || accountId == null) {
			return null;
		}
		CampaignCoalition bound = war.getAccountCoalition(accountId);
		return bound == null || bound == coalition ? null : OTHER_SIDE;
	}

	public static void bind(War war, CampaignCoalition coalition, UUID accountId) {
		if (war != null && war.bindAccountCoalition(accountId, coalition)) {
			WarManager.persist(war);
		}
	}
}
