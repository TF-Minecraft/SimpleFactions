package net.tfminecraft.simplefactions.war.resolution;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignSideService.CampaignSideRole;
import net.tfminecraft.simplefactions.war.campaign.progression.WhitePeaceService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.government.movement.Action;
import net.tfminecraft.simplefactions.government.proposal.Proposal;

public final class CouncilPeaceService {

	private CouncilPeaceService() {}

	public static void apply(Faction actor, Proposal proposal) {
		if (actor == null || proposal == null || proposal.getPoliticalAction() == null) {
			return;
		}
		Action action = proposal.getPoliticalAction().getAction();
		if (!CouncilPeaceQueries.isWarEndAction(action)) {
			return;
		}
		War war = CouncilPeaceQueries.warFromTarget(proposal.getTarget());
		if (war == null || !war.isParticipating(actor)) {
			return;
		}
		Faction main = CouncilPeaceQueries.sideMain(war, actor);
		if (main == null) {
			return;
		}
		if (action == Action.SURRENDER) {
			WarResolutionService.surrender(war, main);
			return;
		}
		CampaignSideRole coalition = CampaignSideService.coalitionOf(war, war.getSide(actor));
		if (coalition == CampaignSideRole.AGGRESSOR) {
			war.setForcedWhitePeaceByAttacker(true);
		} else {
			war.setForcedWhitePeaceByDefender(true);
		}
		CampaignSideService.setWhitePeaceProposed(war, coalition, true);
		if (WhitePeaceService.shouldAutoEnd(war)) {
			WarResolutionService.endWhitePeace(war);
		}
	}
}
