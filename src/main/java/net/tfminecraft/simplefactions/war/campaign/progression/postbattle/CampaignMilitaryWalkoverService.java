package net.tfminecraft.simplefactions.war.campaign.progression.postbattle;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import net.tfminecraft.simplefactions.war.campaign.progression.OccupationService;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService;
import java.util.Optional;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.resolution.WarResolutionService;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.pathfinder.TitleManagerProvinceOwnerLookup;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.installation.WartimeInstallationService;

public final class CampaignMilitaryWalkoverService {
	private static final int MAX_CHAIN = 32;

	private CampaignMilitaryWalkoverService() {}

	public static void resolvePendingWalkovers(War war) {
		if (war == null || !war.isActive() || CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return;
		}
		Battle existing = BattleManager.getByWarId(war.getId());
		if (existing != null && existing.hasStarted()) {
			return;
		}
		for (int i = 0; i < MAX_CHAIN && war.isActive(); i++) {
			if (CampaignPostBattleChoiceService.needsAnyChoice(war)) {
				return;
			}
			Optional<Integer> province = CampaignCapabilityService.nextBattleProvince(war)
					.stream()
					.boxed()
					.findFirst();
			if (province.isEmpty()) {
				return;
			}
			int battleProvince = province.get();
			if (battleProvince <= 0) {
				return;
			}
			CampaignCoalition holder = CampaignCoalitionService.getInitiativeHolderCoalition(war);
			CampaignCoalition opponent = holder.opposing();
			boolean holderCanAttack = CampaignCapabilityService.canAttack(war, holder);
			boolean opponentCanDefend = CampaignCapabilityService.canDefend(war, battleProvince, opponent);
			boolean holderCanDefend = CampaignCapabilityService.canDefend(war, battleProvince, holder);

			if (!holderCanDefend && !opponentCanDefend) {
				WarResolutionService.endWhitePeace(war);
				return;
			}
			if (!holderCanAttack) {
				CampaignOffensiveForfeitService.applyIfBattleOffensiveCannotAttack(war, battleProvince);
				continue;
			}
			if (opponentCanDefend) {
				return;
			}

			applyWalkoverWin(war, holder, battleProvince);
		}
	}

	static void applyWalkoverWin(War war, CampaignCoalition winner, int battleProvinceId) {
		CampaignPushTarget preBattlePushTarget = CampaignCapabilityService.effectivePushTarget(war);
		ObjectiveHolder preBattleObjectiveHeldBy = war.getObjectiveHeldBy();
		war.setLastBattleOffensiveCoalition(CampaignCoalitionService.getInitiativeHolderCoalition(war));
		CampaignBattleEndService.spendOffensiveFuel(war);
		ScheduledCampaignBattle foughtSlot = CampaignScheduleService.slotAtActiveIndex(war).orElse(null);
		if (foughtSlot != null) CampaignScheduleService.advanceIndex(war);
		CampaignBattleEndService.advanceAlongPushTarget(war);
		BattleNamingService.recordLocationBattle(war, battleProvinceId, foughtSlot);
		occupationService().applyBattleWin(
				war,
				battleProvinceId,
				CampaignCoalitionService.coalitionToBelligerentRole(winner));
		WartimeInstallationService.occupySiegeFort(
				war,
				CampaignCoalitionService.coalitionToBelligerentRole(winner),
				foughtSlot);
		CampaignCoalitionService.setInitiativeHolderCoalition(war, winner);
		war.setCampaignBattlesFought(war.getCampaignBattlesFought() + 1);
		CampaignBattleEndService.clearHoldPeace(war);
		WarResolutionService.tryEndAfterBattle(
				war,
				battleProvinceId,
				winner,
				preBattlePushTarget,
				preBattleObjectiveHeldBy);
	}

	private static OccupationService occupationService() {
		return new OccupationService(
				SimpleFactions.plugin.getProvinceManager(),
				new TitleManagerProvinceOwnerLookup());
	}
}
