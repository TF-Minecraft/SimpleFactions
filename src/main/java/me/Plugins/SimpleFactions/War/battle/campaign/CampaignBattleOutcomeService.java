package me.Plugins.SimpleFactions.War.battle.campaign;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.SimpleFactions;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.War.battle.military.BattleCasualtyService;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignBattleEndService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignMilitaryWalkoverService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.OccupationService;
import me.Plugins.SimpleFactions.War.pathfinder.TitleManagerProvinceOwnerLookup;
import me.Plugins.SimpleFactions.War.resolution.ResolutionContext;
import me.Plugins.SimpleFactions.War.resolution.WarResolutionService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleScheduleService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;
import me.Plugins.SimpleFactions.War.campaign.schedule.CampaignScheduleService;
import me.Plugins.SimpleFactions.War.campaign.zoc.FortControlService;
import me.Plugins.SimpleFactions.installation.WartimeInstallationService;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBattleService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;

public class CampaignBattleOutcomeService implements Listener {
	public record CampaignBattleApplyResult(
			boolean progressionApplied,
			boolean postBattleChoicePending,
			Optional<WarEndReason> autoEndReason) {}

	@EventHandler
	public void onBattleEnded(BattleEndedEvent event) {
		handleBattleEnded(event);
	}

	static void handleBattleEnded(BattleEndedEvent event) {
		if (event == null || event.getWarId() == null) {
			return;
		}

		War war = WarManager.getById(event.getWarId());
		if (war == null || !war.isActive()) {
			return;
		}

		if (CampaignRaidBattleService.isCampaignRaidEvent(war, event)) {
			return;
		}

		Battle battle = BattleManager.getByString(event.getBattleId());
		BelligerentRole winnerRole = mapWinningSide(event.getWinningSideId());
		Integer battleProvinceId = battle != null && battle.getProvinceId() != null
				? battle.getProvinceId()
				: war.getScheduledBattleProvinceId();

		CampaignBattleApplyResult result = applyCampaignBattleOutcome(
				war,
				winnerRole,
				battleProvinceId,
				battle,
				event.getSideCasualties());

		if (battle != null) {
			finalizeCampaignBattleAfterOutcome(war, battle);
		} else if (WarManager.getById(war.getId()) != null) {
			WarManager.persist(war);
		}
		broadcastBattleEnded(war, event.getWinningSideId(), result);
	}

	public static void finalizeCampaignBattleAfterOutcome(War war, Battle battle) {
		if (battle != null) {
			BattlePersistenceService.deleteCampaignBattle(battle);
		}
		if (war != null && WarManager.getById(war.getId()) != null) {
			WarManager.persist(war);
		}
	}

	public static void finalizeCampaignBattleAfterOutcome(War war) {
		finalizeCampaignBattleAfterOutcome(war, BattleManager.getByWarId(war != null ? war.getId() : null));
	}

	public static CampaignBattleApplyResult applyCampaignBattleOutcome(
			War war,
			BelligerentRole winnerRole,
			Integer battleProvinceId) {
		return applyCampaignBattleOutcome(war, winnerRole, battleProvinceId, null, null);
	}

	public static CampaignBattleApplyResult applyCampaignBattleOutcome(
			War war,
			BelligerentRole winnerRole,
			Integer battleProvinceId,
			Battle battle,
			Map<String, Integer> sideCasualties) {
		return applyCampaignBattleOutcome(war, winnerRole, battleProvinceId, battle, sideCasualties, null);
	}

	public static CampaignBattleApplyResult applyCampaignBattleOutcome(
			War war,
			BelligerentRole winnerRole,
			Integer battleProvinceId,
			Battle battle,
			Map<String, Integer> sideCasualties,
			CampaignCoalition lastBattleOffensiveOverride) {
		if (war == null) {
			return new CampaignBattleApplyResult(false, false, Optional.empty());
		}

		CampaignPushTarget preBattlePushTarget = war.getPushTarget();
		ObjectiveHolder preBattleObjectiveHeldBy = war.getObjectiveHeldBy();

		if (lastBattleOffensiveOverride != null) {
			CampaignBattleEndService.snapshotBattleStart(war, lastBattleOffensiveOverride);
		} else {
			CampaignBattleEndService.snapshotBattleStart(war);
		}

		if (battle != null) {
			BattleCasualtyService.applyBattleCasualties(
					war,
					battle,
					sideCasualties != null ? sideCasualties : Map.of());
		}

		CampaignBattleEndService.spendOffensiveFuel(war);
		CampaignBattleEndService.clearHoldPeace(war);

		ScheduledCampaignBattle foughtSlot = CampaignScheduleService.slotAtActiveIndex(war).orElse(null);

		boolean progressionApplied = false;
		if (winnerRole != null && battleProvinceId != null) {
			CampaignCoalition winner = CampaignCoalitionService.belligerentRoleToCoalition(winnerRole);
			if (foughtSlot != null
					&& foughtSlot.kind() == CampaignBattleKind.SIEGE
					&& foughtSlot.fortInstallationId() != null
					&& winner != null) {
				FortControlService.setController(war, foughtSlot.fortInstallationId(), winner);
			}

			occupationService().applyBattleWin(war, battleProvinceId, winnerRole);
			WartimeInstallationService.occupySiegeFort(war, winnerRole, foughtSlot);
			BattleNamingService.recordLocationBattle(war, battleProvinceId, foughtSlot);
			if (CampaignScheduleService.hasActiveSchedule(war)) {
				CampaignScheduleService.advanceIndex(war);
			}
			war.setCampaignBattlesFought(war.getCampaignBattlesFought() + 1);
			progressionApplied = true;
		}

		CampaignCoalition winner = CampaignCoalitionService.belligerentRoleToCoalition(winnerRole);
		if (winner != null && battleProvinceId != null) {
			Optional<WarEndReason> battleVictory = WarResolutionService.tryEndAfterBattle(
					war,
					battleProvinceId,
					winner,
					preBattlePushTarget,
					preBattleObjectiveHeldBy);
			if (battleVictory.isPresent()) {
				return new CampaignBattleApplyResult(progressionApplied, false, battleVictory);
			}
		}

		if (winner != null) {
			if (CampaignPostBattleChoiceService.resolveMandatoryHoldIfNeeded(war, winner)) {
				notifyPostBattleChoicePending(war);
				return new CampaignBattleApplyResult(progressionApplied, true, Optional.empty());
			}
			CampaignBattleEndService.beginPostBattleChoice(war, winner);
		}

		Optional<WarEndReason> autoEnd = Optional.empty();
		if (!CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			CampaignMilitaryWalkoverService.resolvePendingWalkovers(war);
			if (WarManager.getById(war.getId()) != null) {
				autoEnd = WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());
			}
		}

		if (WarManager.getById(war.getId()) != null) {
			BattleScheduleService.openVote(war);
		}

		boolean choicePending = CampaignPostBattleChoiceService.needsAnyChoice(war);
		if (choicePending) {
			notifyPostBattleChoicePending(war);
		}

		return new CampaignBattleApplyResult(progressionApplied, choicePending, autoEnd);
	}

	public static void notifyPostBattleChoicePending(War war) {
		if (war == null || !CampaignPostBattleChoiceService.needsAnyChoice(war)) {
			return;
		}
		if (Bukkit.getServer() == null) {
			return;
		}
		CampaignCoalition leaderCoalition = CampaignPostBattleChoiceService.choiceLeaderCoalition(war);
		if (leaderCoalition == null) {
			return;
		}
		String message = CampaignPostBattleChoiceService.needsWinnerChoice(war)
				? "§eChoose §6push §eor §6hold §eon the campaign map."
				: "§eChoose §6attack §eor §6accept white peace §eon the campaign map.";
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(
				CampaignCoalitionService.toSide(war, leaderCoalition))) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(message);
			}
		}
	}

	private static BelligerentRole mapWinningSide(String winningSideId) {
		if (winningSideId == null || winningSideId.isBlank()) {
			return null;
		}
		if (BattleTemplate.ATTACKER_SIDE.equalsIgnoreCase(winningSideId)) {
			return BelligerentRole.ATTACKER;
		}
		if (BattleTemplate.DEFENDER_SIDE.equalsIgnoreCase(winningSideId)) {
			return BelligerentRole.DEFENDER;
		}
		return null;
	}

	private static OccupationService occupationService() {
		if (SimpleFactions.plugin != null) {
			return new OccupationService(
					SimpleFactions.plugin.getProvinceManager(),
					new TitleManagerProvinceOwnerLookup());
		}
		return new OccupationService(null, new TitleManagerProvinceOwnerLookup());
	}

	private static void broadcastBattleEnded(
			War war,
			String winningSideId,
			CampaignBattleApplyResult result) {
		String resultLine = winningSideId == null || winningSideId.isBlank()
				? "§7Campaign battle ended with no winner. Voting reopened."
				: "§aCampaign battle ended. Winner: §e" + winningSideId + "§7. Voting reopened.";
		if (result.autoEndReason().isPresent()) {
			resultLine = "§aCampaign battle ended. War resolved automatically.";
		}
		if (Bukkit.getServer() == null) {
			return;
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getAttackers())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(resultLine);
			}
		}
		for (String memberName : BattleSideMembers.collectEligibleMemberNames(war.getDefenders())) {
			Player player = Bukkit.getPlayerExact(memberName);
			if (player != null && player.isOnline()) {
				player.sendMessage(resultLine);
			}
		}
	}
}
