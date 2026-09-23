package net.tfminecraft.simplefactions.war.campaign.admin;



import java.time.Instant;

import java.time.format.DateTimeParseException;

import java.util.List;



import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.war.core.War;

import net.tfminecraft.simplefactions.war.battle.campaign.BattleNamingService;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;

import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleOutcomeService;

import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleRosterService;

import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.core.WarDevMode;

import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;

import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;

import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;

import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;

import net.tfminecraft.simplefactions.war.battle.warband.Warband;

import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;

import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;

import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignChoiceService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleScheduleCloseResult;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.CloseVoteOptions;

public final class WarScheduleAdminService {

	private WarScheduleAdminService() {}



	public static List<String> devModeReminderLines() {

		if (WarDevMode.isEnabled()) {

			return List.of();

		}

		return List.of(

				"§7War devmode: §cdisabled§7. Use §e/war admin devmode on §7for solo staging (capture min, roster fill).");

	}



	public static WarScheduleAdminResult openVote(War war) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		BattleScheduleService.openVote(war);

		return WarScheduleAdminResult.ok("Opened voting for war " + war.getId() + ".");

	}



	public static WarScheduleAdminResult closeVote(War war, Instant now) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {

			return WarScheduleAdminResult.error("War is not in VOTING phase.");

		}



		BattleScheduleCloseResult result = BattleScheduleService.closeVote(

				war,

				now,

				BattleScheduleLookups.uuidToFactionForWar(war),

				BattleScheduleLookups.memberNameToUuid(),

				CloseVoteOptions.admin(war.isForceQuorumNextClose()));



		return switch (result) {

			case SCHEDULED -> WarScheduleAdminResult.ok(

					"Vote close scheduled battle at "

							+ war.getScheduledBattleAt()

							+ " (province "

							+ war.getScheduledBattleProvinceId()

							+ ").");

			case POSTPONED -> WarScheduleAdminResult.ok(

					"Vote close postponed to battle day " + war.getBattleDay() + ".");

			case AUTORESOLVE_PENDING -> WarScheduleAdminResult.ok("Vote close set AUTORESOLVE_PENDING.");

			case BLOCKED_DEFENDER_CHOICE -> WarScheduleAdminResult.error(

					"Vote close blocked: defender choice unresolved.");

			case SKIPPED -> WarScheduleAdminResult.error("Vote close skipped.");

		};

	}



	public static WarScheduleAdminResult skipDay(War war) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		if (war.getBattleDay() == null) {

			return WarScheduleAdminResult.error("War has no battle day.");

		}

		BattleScheduleService.skipBattleDay(war);

		return WarScheduleAdminResult.ok("Skipped to battle day " + war.getBattleDay() + ".");

	}



	public static WarScheduleAdminResult castVote(War war, int hour, String sideArg) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		if (!BattleWindowService.isValidHour(hour)) {

			return WarScheduleAdminResult.error("Hour must be within the battle window.");

		}



		BelligerentRole[] sides = parseSideArg(sideArg);

		if (sides.length == 0) {

			return WarScheduleAdminResult.error("Side must be attacker, defender, or both.");

		}



		int added = BattleScheduleService.castSpoofVotes(

				war,

				hour,

				BattleScheduleLookups.spoofMemberNameToUuid(),

				sides);

		return WarScheduleAdminResult.ok(

				"Cast spoof votes for hour "

						+ hour

						+ " ("

						+ added

						+ " selections, "

						+ BattleQuorumService.countDistinctVoters(war)

						+ " voters total).");

	}



	public static WarScheduleAdminResult forceQuorum(War war) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		war.setForceQuorumNextClose(true);

		return WarScheduleAdminResult.ok("Next vote close will bypass quorum for war " + war.getId() + ".");

	}



	public static WarScheduleAdminResult setScheduled(War war, String isoInstant) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		if (isoInstant == null || isoInstant.isBlank()) {

			return WarScheduleAdminResult.error("Instant must be ISO-8601 (e.g. 2026-08-21T21:00:00Z).");

		}



		Instant scheduledAt;

		try {

			scheduledAt = Instant.parse(isoInstant.trim());

		} catch (DateTimeParseException e) {

			return WarScheduleAdminResult.error("Could not parse instant: " + isoInstant);

		}



		if (!BattleScheduleService.applyScheduledInstant(war, scheduledAt)) {

			return WarScheduleAdminResult.error(

					"Could not set scheduled battle (invalid hour or no battle province).");

		}



		return WarScheduleAdminResult.ok(

				"Set scheduled battle at "

						+ war.getScheduledBattleAt()

						+ " (province "

						+ war.getScheduledBattleProvinceId()

						+ ").");

	}



	public static WarScheduleAdminResult battleCreate(War war) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		Battle existing = BattleManager.getByWarId(war.getId());

		if (existing != null) {

			CampaignBattleRosterService.ensureEnrolledForced(war, existing);

			seedCampaignSidePhantomsIfEnabled(war, existing);

			return WarScheduleAdminResult.ok(

					"Campaign battle already exists: "

							+ existing.getId()

							+ " (province "

							+ war.getScheduledBattleProvinceId()

							+ ").");

		}

		return createFreshCampaignBattle(war, "Created campaign battle ");

	}



	public static WarScheduleAdminResult battleDelete(War war) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		Battle battle = BattleManager.getByWarId(war.getId());

		if (battle == null) {

			return WarScheduleAdminResult.error("No campaign battle for this war.");

		}

		if (battle.hasStarted()) {

			return WarScheduleAdminResult.error(

					"Cannot reset while the battle is running. End it first (/battle edit -> End Battle).");

		}

		String removedBattleId = battle.getId();

		BattlePersistenceService.deleteCampaignBattle(battle);

		BattleManager.clearEditorSessions(battle);

		WarScheduleAdminResult createResult = createFreshCampaignBattle(war, "Reset campaign battle: removed "

				+ removedBattleId

				+ ", recreated ");

		if (!createResult.success()) {

			return WarScheduleAdminResult.error(

					"Removed battle "

							+ removedBattleId

							+ " but could not recreate: "

							+ createResult.message());

		}

		return createResult;

	}



	public static WarScheduleAdminResult battleStart(War war) {

		if (war == null) {

			return WarScheduleAdminResult.error("War not found.");

		}

		if (!war.isActive()) {

			return WarScheduleAdminResult.error("War is not active.");

		}

		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null) {
			battle = CampaignBattleLaunchService.prepareScheduledBattle(war);
		}
		String startError = CampaignBattleLaunchService.startPreparedBattle(war, battle);
		if (startError != null) {
			return WarScheduleAdminResult.error(startError);
		}
		return WarScheduleAdminResult.ok("Started battle " + battle.getDisplayName() + ".");
	}

	public static WarScheduleAdminResult winBattle(War war, BelligerentRole winner) {
		WarScheduleAdminResult validation = requireActiveWar(war);
		if (validation != null) {
			return validation;
		}
		if (winner == null) {
			return WarScheduleAdminResult.error("Winner must be attacker or defender.");
		}
		if (BattleScheduleService.needsPostBattleChoice(war) && !war.isPostBattleChoiceResolved()) {
			return WarScheduleAdminResult.error(
					"Post-battle choice pending (push/hold or attack/peace on campaign view).");
		}
		Integer provinceId = BattleScheduleService.resolveBattleProvinceId(war);
		if (provinceId == null) {
			return WarScheduleAdminResult.error(
					"Could not resolve battle province. Need a single next battle node.");
		}
		CampaignBattleOutcomeService.CampaignBattleApplyResult result =
				CampaignBattleOutcomeService.applyCampaignBattleOutcome(war, winner, provinceId);
		if (winner != null && !result.progressionApplied()) {
			return WarScheduleAdminResult.error("Could not apply battle outcome.");
		}
		CampaignBattleOutcomeService.finalizeCampaignBattleAfterOutcome(war);
		if (result.autoEndReason().isPresent()) {
			return WarScheduleAdminResult.ok(
					"Applied "
							+ winner.name().toLowerCase()
							+ " win at province "
							+ provinceId
							+ ". "
							+ formatWarEndSummary(result.autoEndReason().get()));
		}
		String message = "Applied "
				+ winner.name().toLowerCase()
				+ " win at province "
				+ provinceId
				+ ". Voting reopened.";
		if (result.postBattleChoicePending()) {
			message += " Post-battle choice pending.";
		}
		return WarScheduleAdminResult.ok(message);
	}

	public static WarScheduleAdminResult battleChoice(War war, String choice) {
		WarScheduleAdminResult validation = requireActiveWar(war);
		if (validation != null) {
			return validation;
		}
		if (choice == null || choice.isBlank()) {
			return WarScheduleAdminResult.error("Usage: /war admin schedule <id> choice push|hold|attack|accept");
		}
		boolean applied = switch (choice.toLowerCase()) {
			case "push" -> CampaignChoiceService.applyPush(war);
			case "hold" -> CampaignChoiceService.applyHold(war);
			case "attack" -> CampaignChoiceService.applyLoserAttack(war);
			case "accept", "peace", "acceptpeace", "accept-peace" -> CampaignChoiceService.applyLoserAcceptPeace(war);
			default -> false;
		};
		if (!applied) {
			return WarScheduleAdminResult.error("Post-battle choice not available. Use push, hold, attack, or accept.");
		}
		if (WarManager.getById(war.getId()) == null) {
			return WarScheduleAdminResult.ok(
					"Post-battle choice applied. " + formatWarEndSummary(war.getEndReason()));
		}
		return switch (choice.toLowerCase()) {
			case "push" -> WarScheduleAdminResult.ok("Winner pushes. Voting reopened.");
			case "hold" -> WarScheduleAdminResult.ok("Winner holds. White peace proposed.");
			case "attack" -> WarScheduleAdminResult.ok("Loser attacks at the held front. Voting reopened.");
			default -> WarScheduleAdminResult.ok("Loser accepted white peace.");
		};
	}

	private static WarScheduleAdminResult createFreshCampaignBattle(War war, String successPrefix) {
		Integer provinceId = BattleScheduleService.resolveScheduledProvinceId(war);
		if (provinceId == null) {
			return WarScheduleAdminResult.error(
					"Need a single next battle province (green on campaign map).");
		}

		BattlePersistenceService.purgeCampaignWarbandsForWar(war.getId());
		if (!BattleScheduleService.markScheduledAtProvince(war, provinceId)) {
			return WarScheduleAdminResult.error("Could not create campaign battle.");
		}

		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle == null) {
			return WarScheduleAdminResult.error("Could not create campaign battle.");
		}

		CampaignBattleRosterService.ensureEnrolledForced(war, battle);

		seedCampaignSidePhantomsIfEnabled(war, battle);

		BattlePersistenceService.persistBattle(battle);

		return WarScheduleAdminResult.ok(
				successPrefix
						+ battle.getDisplayName()
						+ " at province "
						+ provinceId
						+ " with fresh warband shells on both sides.");
	}



	private static WarScheduleAdminResult requireActiveWar(War war) {
		if (war == null) {
			return WarScheduleAdminResult.error("War not found.");
		}
		if (!war.isActive()) {
			return WarScheduleAdminResult.error("War is not active.");
		}
		return null;
	}

	private static BelligerentRole[] parseSideArg(String sideArg) {
		if (sideArg == null || sideArg.isBlank() || sideArg.equalsIgnoreCase("both")) {

			return new BelligerentRole[] {BelligerentRole.ATTACKER, BelligerentRole.DEFENDER};

		}

		if (sideArg.equalsIgnoreCase("attacker")) {

			return new BelligerentRole[] {BelligerentRole.ATTACKER};

		}

		if (sideArg.equalsIgnoreCase("defender")) {

			return new BelligerentRole[] {BelligerentRole.DEFENDER};

		}

		return new BelligerentRole[0];

	}



	private static void seedCampaignSidePhantomsIfEnabled(War war, Battle battle) {

		if (war == null || battle == null) {

			return;

		}

		seedCampaignSidePhantomsIfEnabled(war, battle, BattleTemplate.ATTACKER_SIDE);

		seedCampaignSidePhantomsIfEnabled(war, battle, BattleTemplate.DEFENDER_SIDE);

	}



	private static void seedCampaignSidePhantomsIfEnabled(War war, Battle battle, String battleSideId) {

		Warband warband = WarbandManager.getByString(
				BattleNamingService.campaignWarbandId(battle.getDisplayName(), battleSideId));

		if (warband == null) {

			return;

		}

		WarDevMode.seedCampaignSideIfEnabled(warband, war, battle, battleSideId);

	}

	static String formatWarEndSummary(WarEndReason reason) {
		if (reason == null) {
			return "War ended.";
		}
		return switch (reason) {
			case WHITE_PEACE -> "War ended (white peace).";
			case ATTACKER_VICTORY -> "War ended (attacker victory).";
			case DEFENDER_VICTORY -> "War ended (defender victory).";
			case ADMIN_END -> "War ended (admin).";
		};
	}

}


