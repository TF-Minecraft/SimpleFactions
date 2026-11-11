package net.tfminecraft.simplefactions.war.campaign.admin;



import java.time.Instant;

import java.time.LocalDate;

import java.time.format.DateTimeParseException;

import java.util.List;



import net.tfminecraft.simplefactions.Cache;
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
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleStaffService;

import net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService;

import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;

import net.tfminecraft.simplefactions.war.battle.warband.Warband;

import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;

import net.tfminecraft.simplefactions.war.enums.WarEndReason;

import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignChoiceService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;

public final class WarScheduleAdminService {

	private WarScheduleAdminService() {}



	public static List<String> devModeReminderLines() {

		if (WarDevMode.isEnabled()) {

			return List.of();

		}

		return List.of(

				"§7War devmode: §cdisabled§7. Use §e/war admin devmode on §7for solo staging (capture min, roster fill).");

	}



	/** What moving a war to another battle day resets, so staff are not surprised by it. */
	static final String NEW_DAY_CLEARED =
			"Installation commitments were cleared (a naval defender's port is recommitted automatically),"
					+ " any running raid was ended and each side's raid for the day is available again."
					+ " Leaders must commit their installations again.";

	/** The war's battle days and, per date shown, its state and marks per hour. */
	public static WarScheduleAdminResult availability(War war, Instant now) {
		WarScheduleAdminResult invalid = requireScheduling(war);
		if (invalid != null) {
			return invalid;
		}
		return WarScheduleAdminResult.ok(String.join("\n", AvailabilityScheduler.describe(war, now)));
	}

	/** Drops the next battle day; the war waits for the next one the marks give. */
	public static WarScheduleAdminResult skipDay(War war) {
		WarScheduleAdminResult invalid = requireScheduling(war);
		if (invalid != null) {
			return invalid;
		}
		LockedBattleDay next = war.getAvailability().head();
		if (next == null) {
			return WarScheduleAdminResult.error("War has no battle day to skip.");
		}
		Battle battle = BattleManager.getByWarId(war.getId());
		if (battle != null && battle.hasStarted()) {
			return WarScheduleAdminResult.error("Cannot skip the battle day while its battle is running.");
		}
		AvailabilityScheduler.skip(war);
		return WarScheduleAdminResult.ok("Skipped battle day " + next.date() + ". Next battle day: "
				+ (war.getBattleDay() == null ? "none yet" : war.getBattleDay().toString()) + ". " + NEW_DAY_CLEARED);
	}

	/** Makes a date (yyyy-MM-dd) a battle day now, whatever the marks say. */
	public static WarScheduleAdminResult lockDay(War war, String dayArg, Instant now) {
		WarScheduleAdminResult invalid = requireScheduling(war);
		if (invalid != null) {
			return invalid;
		}
		LocalDate day = parseDay(dayArg);
		if (day == null) {
			return WarScheduleAdminResult.error(DAY_FORMAT);
		}
		String refusal = AvailabilityScheduler.lockDay(war, day, now);
		return refusal != null ? WarScheduleAdminResult.error(refusal)
				: WarScheduleAdminResult.ok("Battle day " + day + " locked. Its start time is decided "
						+ AvailabilityCalendar.timeLockAt(day) + " unless you set it with sethour.");
	}

	/** Takes a battle day (yyyy-MM-dd) back. */
	public static WarScheduleAdminResult unlockDay(War war, String dayArg) {
		WarScheduleAdminResult invalid = requireScheduling(war);
		if (invalid != null) {
			return invalid;
		}
		LocalDate day = parseDay(dayArg);
		if (day == null) {
			return WarScheduleAdminResult.error(DAY_FORMAT);
		}
		String refusal = AvailabilityScheduler.unlockDay(war, day);
		return refusal != null ? WarScheduleAdminResult.error(refusal)
				: WarScheduleAdminResult.ok("Battle day " + day + " unlocked.");
	}

	/**
	 * Sets a battle day's start time (0 and 1 mean after midnight); the next battle day when no date
	 * is given. The next battle day is set on its province once its picks have locked
	 * ({@code vote_close_hour} that day): at once when that time has come.
	 */
	public static WarScheduleAdminResult setHour(War war, int hour, String dayArg, Instant now) {
		WarScheduleAdminResult invalid = requireScheduling(war);
		if (invalid != null) {
			return invalid;
		}
		Integer normalized = BattleWindowService.normalizeInputHour(hour);
		if (normalized == null) {
			return WarScheduleAdminResult.error(
					"Hour must be a start hour in the battle window: " + BattleWindowService.describeValidHours() + ".");
		}
		LocalDate day = dayArg == null ? war.getBattleDay() : parseDay(dayArg);
		if (day == null) {
			return WarScheduleAdminResult.error(dayArg == null
					? "War has no battle day. Use lockday first." : DAY_FORMAT);
		}
		String refusal = AvailabilityScheduler.setHour(war, day, normalized, now);
		if (refusal != null) {
			return WarScheduleAdminResult.error(refusal);
		}
		return WarScheduleAdminResult.ok("Battle day " + day + " starts at " + BattleWindowService.formatHourLabel(normalized)
				+ (war.getScheduledBattleAt() != null && day.equals(war.getBattleDay())
						? " (province " + war.getScheduledBattleProvinceId() + ")."
						: ". It is set on its province from " + BattleWindowService.formatHourLabel(Cache.warVoteCloseHour)
								+ " that day."));
	}

	/** Dev: marks an hour of a date for every member of the given sides, under test accounts. */
	public static WarScheduleAdminResult castVote(War war, String dayArg, int hour, String sideArg) {
		WarScheduleAdminResult invalid = requireScheduling(war);
		if (invalid != null) {
			return invalid;
		}
		LocalDate day = parseDay(dayArg);
		if (day == null) {
			return WarScheduleAdminResult.error(DAY_FORMAT);
		}
		Integer normalized = BattleWindowService.normalizeInputHour(hour);
		if (normalized == null) {
			return WarScheduleAdminResult.error(
					"Hour must be within the battle window: " + BattleWindowService.describeValidHours() + ".");
		}
		BelligerentRole[] sides = parseSideArg(sideArg);
		if (sides.length == 0) {
			return WarScheduleAdminResult.error("Side must be attacker, defender, or both.");
		}
		int added = AvailabilityScheduler.castMarks(war, day, normalized, sides);
		return WarScheduleAdminResult.ok("Marked " + day + " " + BattleWindowService.formatHourLabel(normalized)
				+ " for " + added + " test accounts.");
	}

	static final String DAY_FORMAT = "Day must be yyyy-MM-dd (e.g. 2026-11-07).";

	private static LocalDate parseDay(String dayArg) {
		try {
			return LocalDate.parse(dayArg == null ? "" : dayArg.trim());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static WarScheduleAdminResult requireScheduling(War war) {
		WarScheduleAdminResult invalid = requireActiveWar(war);
		if (invalid == null && !AvailabilityScheduler.isScheduling(war)) {
			return WarScheduleAdminResult.error("War has no battle schedule.");
		}
		return invalid;
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

		Integer replacementProvince = BattleScheduleService.resolveScheduledProvinceId(war);
		if (replacementProvince == null) {
			return WarScheduleAdminResult.error("Need a single next battle province (green on campaign map).");
		}
		try {
			var type = net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleTypeResolver.resolve(war, replacementProvince);
			Battle replacement = net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory.createBlank(type, battle.getId());
			net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory.applyCampaignDefault(replacement);
		} catch (IllegalArgumentException | IllegalStateException error) {
			return WarScheduleAdminResult.error("Cannot reset campaign battle: " + error.getMessage());
		}

		String removedBattleId = battle.getId();

		BattlePersistenceService.deleteCampaignBattle(battle);

		BattleManager.clearEditorSessions(battle);

		return createFreshCampaignBattle(war, "Reset campaign battle: removed "
				+ removedBattleId + ", recreated ");

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

	/** Restart, abort, end or hold this war's campaign battle; see {@link BattleStaffService}. */
	public static WarScheduleAdminResult battleStaff(War war, String action, String arg, String by) {
		Battle battle = war == null ? null : BattleManager.getByWarId(war.getId());
		if (battle == null) {
			return WarScheduleAdminResult.error("No campaign battle for this war.");
		}
		BattleStaffService.Result result = switch (action) {
			case "battlerestart" -> BattleStaffService.restart(battle, by);
			case "battleabort" -> BattleStaffService.abort(battle, by);
			case "battleend" -> BattleStaffService.end(battle,
					arg == null || arg.equalsIgnoreCase("nowinner") ? null : arg, by);
			default -> "on".equalsIgnoreCase(arg) || "off".equalsIgnoreCase(arg)
					? BattleStaffService.hold(battle, "on".equalsIgnoreCase(arg), by)
					: new BattleStaffService.Result(false, "Usage: /war admin schedule <id> battlehold on|off");
		};
		return result.ok() ? WarScheduleAdminResult.ok(result.message()) : WarScheduleAdminResult.error(result.message());
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
		if (!applied && choice.equalsIgnoreCase("attack") && CampaignPostBattleChoiceService.needsLoserResponse(war)) {
			return WarScheduleAdminResult.error(
					"The losing side cannot attack: it has no initiative left or no battle to fight. Use accept.");
		}
		if (!applied) {
			return WarScheduleAdminResult.error("Post-battle choice not available. Use push, hold, attack, or accept.");
		}
		if (WarManager.getById(war.getId()) == null) {
			return WarScheduleAdminResult.ok(
					"Post-battle choice applied. " + formatWarEndSummary(war.getEndReason()));
		}
		return appliedChoiceMessage(choice);
	}

	static WarScheduleAdminResult appliedChoiceMessage(String choice) {
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
		BattleScheduleService.markScheduledAtProvince(war, provinceId);

		Battle battle = BattleManager.getByWarId(war.getId());

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

		seedCampaignSidePhantomsIfEnabled(war, battle, BattleTemplate.ATTACKER_SIDE);

		seedCampaignSidePhantomsIfEnabled(war, battle, BattleTemplate.DEFENDER_SIDE);

	}



	private static void seedCampaignSidePhantomsIfEnabled(War war, Battle battle, String battleSideId) {

		Warband warband = CampaignBattleRosterService.getCampaignWarband(battle, battleSideId);

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

