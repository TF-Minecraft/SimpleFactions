package net.tfminecraft.simplefactions.war.battle.campaign;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;

public final class CampaignBattleSides {
	private CampaignBattleSides() {
	}

	public static Side warSideFor(War war, Battle battle, String battleSideId) {
		if (war == null || battleSideId == null || battleSideId.isBlank()) {
			return null;
		}
		CampaignCoalition offensive = offensiveCoalition(battle);
		if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(battleSideId)) {
			return CampaignCoalitionService.toSide(war, offensive);
		}
		if (BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(battleSideId)) {
			return CampaignCoalitionService.toSide(war, offensive.opposing());
		}
		return null;
	}

	public static String battleSideFor(War war, Battle battle, Side side) {
		if (war == null || side == null) {
			return null;
		}
		CampaignCoalition coalition = CampaignCoalitionService.coalitionOf(war, side);
		if (coalition == null) {
			return null;
		}
		CampaignCoalition offensive = offensiveCoalition(battle);
		return coalition == offensive ? BattleTemplate.ATTACKER_SIDE : BattleTemplate.DEFENDER_SIDE;
	}

	public static BelligerentRole roleFor(War war, Battle battle, String battleSideId) {
		Side side = warSideFor(war, battle, battleSideId);
		if (side == null) {
			return null;
		}
		CampaignCoalition coalition = CampaignCoalitionService.coalitionOf(war, side);
		if (coalition == null) {
			return null;
		}
		return CampaignCoalitionService.coalitionToBelligerentRole(coalition);
	}

	private static CampaignCoalition offensiveCoalition(Battle battle) {
		if (battle != null && battle.getOffensiveCoalition() != null) {
			return battle.getOffensiveCoalition();
		}
		// Missing snapshot: war attackers stay on the battle attacker side.
		return CampaignCoalition.AGGRESSOR;
	}
}
