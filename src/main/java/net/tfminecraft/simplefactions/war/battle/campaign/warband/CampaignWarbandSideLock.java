package net.tfminecraft.simplefactions.war.battle.campaign.warband;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleJoinService.CampaignBattleContext;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleSides;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * Ties an account to the coalition of its first warband signup in a war, so a
 * player cannot fight both sides by switching character. Held until the war ends.
 */
public final class CampaignWarbandSideLock {
	public static final String OTHER_SIDE =
			"You already fight for the other side of this war. You can change sides when the war ends.";

	/** The active war a warband fights in and the coalition it fights for. */
	public record Tie(War war, CampaignSideRole coalition) {}

	private CampaignWarbandSideLock() {
	}

	/** The war coalition a battle side belongs to. */
	public static CampaignSideRole coalition(War war, Battle battle, String battleSideId) {
		return CampaignSideService.coalitionOf(war, CampaignBattleSides.warSideFor(war, battle, battleSideId));
	}

	/** Campaign battle warbands, and raid shells from muster onwards; null for other warbands. */
	public static Tie tieOf(Warband warband) {
		CampaignBattleContext ctx = CampaignBattleJoinService.findCampaignBattleForWarband(warband);
		if (ctx != null) {
			CampaignSideRole coalition = coalition(ctx.war(), ctx.battle(), ctx.sideId());
			return coalition == null ? null : new Tie(ctx.war(), coalition);
		}
		for (War war : WarManager.getActive()) {
			CampaignRaid raid = CampaignRaidService.getActive(war);
			CampaignSideRole raiders = raid == null ? null : raid.getAttackerCoalition();
			if (raiders != null && warband == CampaignRaidWarbandService.getAttackerWarband(raid)) {
				return new Tie(war, raiders);
			}
			if (raiders != null && warband == CampaignRaidWarbandService.getDefenderWarband(raid)) {
				return new Tie(war, raiders.opposing());
			}
		}
		return null;
	}

	/** Null when the account may sign up for that coalition. */
	public static String refusal(War war, CampaignSideRole coalition, UUID accountId) {
		if (war == null || coalition == null || accountId == null) {
			return null;
		}
		CampaignSideRole bound = war.getAccountCoalition(accountId);
		return bound == null || bound == coalition ? null : OTHER_SIDE;
	}

	public static void bind(War war, CampaignSideRole coalition, UUID accountId) {
		if (war != null && war.bindAccountCoalition(accountId, coalition)) {
			WarManager.persist(war);
		}
	}

	/** Ties the enrolled members of war warbands loaded from before this lock existed. */
	public static void bindLoadedMembers() {
		Set<War> changed = new LinkedHashSet<>();
		for (Warband warband : WarbandManager.get()) {
			Tie tie = tieOf(warband);
			if (tie == null) {
				continue;
			}
			for (UUID account : warband.getMemberCharacters().keySet()) {
				if (tie.war().bindAccountCoalition(account, tie.coalition())) {
					changed.add(tie.war());
				}
			}
		}
		changed.forEach(WarManager::persist);
	}
}
