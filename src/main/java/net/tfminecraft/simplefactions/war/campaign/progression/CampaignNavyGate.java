package net.tfminecraft.simplefactions.war.campaign.progression;

import java.util.List;

import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.declare.WarValidationResult;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.installation.InstallationNavyQueries;

public final class CampaignNavyGate {
	private CampaignNavyGate() {}

	public static boolean isNavalKind(CampaignBattleKind kind) {
		return kind == CampaignBattleKind.NAVAL || kind == CampaignBattleKind.NAVAL_INVASION;
	}

	public static boolean canChallengeNaval(Faction faction) {
		return InstallationNavyQueries.hasOperationalPort(faction);
	}

	public static boolean invasionRequiresNavy(War war) {
		if (war == null) {
			return false;
		}
		if (war.isPillageNaturalNavyRequired()) {
			return true;
		}
		return scheduleRequiresNavy(war.getCampaignBattleSchedule());
	}

	public static boolean nextSlotRequiresNavy(War war) {
		return CampaignScheduleService.currentSlot(war)
				.map(slot -> isNavalKind(slot.kind()))
				.orElse(false);
	}

	public static boolean winnerCanContestNextNaval(War war, CampaignCoalition winner) {
		if (!nextSlotRequiresNavy(war)) {
			return true;
		}
		Side side = CampaignCoalitionService.toSide(war, winner);
		if (side == null || side.getLeader() == null) {
			return false;
		}
		return canChallengeNaval(side.getLeader());
	}

	public static WarValidationResult validateDeclareAfterPopulate(War war) {
		if (war == null || !invasionRequiresNavy(war)) {
			return WarValidationResult.ok();
		}
		Faction attacker = war.getAttackers() != null ? war.getAttackers().getLeader() : null;
		if (canChallengeNaval(attacker)) {
			return WarValidationResult.ok();
		}
		return WarValidationResult.fail(CampaignUiCopy.navyBlockadeDeclareMessage());
	}

	public static boolean scheduleRequiresNavy(List<ScheduledCampaignBattle> schedule) {
		if (schedule == null || schedule.isEmpty()) {
			return false;
		}
		for (ScheduledCampaignBattle slot : schedule) {
			if (slot != null && isNavalKind(slot.kind())) {
				return true;
			}
		}
		return false;
	}
}
