package net.tfminecraft.simplefactions.war.campaign.progression;


import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import java.util.Optional;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

public final class WhitePeaceService {
	private WhitePeaceService() {}

	public static Optional<WarEndReason> recalculateProposals(War war) {
		if (!isValidWar(war)) {
			return Optional.empty();
		}

		for (CampaignCoalition coalition : CampaignCoalition.values()) {
			boolean propose = !CampaignCapabilityService.canReachTarget(war, coalition);
			if (war.isHoldPeaceProposalActive()
					&& war.getPostBattleWinnerCoalition() == coalition) {
				propose = true;
			}
			if ((coalition == CampaignCoalition.AGGRESSOR && war.isForcedWhitePeaceByAttacker())
					|| (coalition == CampaignCoalition.DEFENDER && war.isForcedWhitePeaceByDefender())) {
				propose = true;
			}
			CampaignCoalitionService.setWhitePeaceProposed(war, coalition, propose);
		}

		if (shouldAutoEnd(war)) {
			return Optional.of(WarEndReason.WHITE_PEACE);
		}
		return Optional.empty();
	}

	public static boolean shouldAutoEnd(War war) {
		return war != null
				&& war.isWhitePeaceProposedByAttacker()
				&& war.isWhitePeaceProposedByDefender();
	}

	public static boolean acceptWhitePeace(War war, Faction acceptingLeader) {
		if (!isValidWar(war) || acceptingLeader == null) {
			return false;
		}

		String leaderId = acceptingLeader.getId();
		boolean isAttackerLeader = leaderId.equalsIgnoreCase(war.getAttackerLeaderId());
		boolean isDefenderLeader = leaderId.equalsIgnoreCase(war.getDefenderLeaderId());
		if (!isAttackerLeader && !isDefenderLeader) {
			return false;
		}

		if (isAttackerLeader) {
			return war.isWhitePeaceProposedByDefender();
		}
		return war.isWhitePeaceProposedByAttacker();
	}

	private static boolean isValidWar(War war) {
		return war != null
				&& war.isActive()
				&& war.getCampaignProvinces() != null
				&& !war.getCampaignProvinces().isEmpty();
	}
}
