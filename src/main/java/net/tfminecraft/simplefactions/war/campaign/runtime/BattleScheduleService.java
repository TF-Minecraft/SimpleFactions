package net.tfminecraft.simplefactions.war.campaign.runtime;


import net.tfminecraft.simplefactions.war.campaign.runtime.pick.BattleInstallationPickService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.freeze.PreparationFreeze;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCapabilityService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.core.WarDevMode;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignProgressionService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleScheduleCloseResult;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.CloseVoteOptions;

public final class BattleScheduleService {
	private BattleScheduleService() {}

	public static int battleDayHour(Instant now) {
		return BattleWindowService.scheduleHour(now);
	}

	public static LocalDate battleDayDate(Instant now) {
		return BattleWindowService.scheduleDate(now);
	}

	public static boolean isOnBattleDay(War war, Instant now) {
		if (war == null || war.getBattleDay() == null || now == null) {
			return false;
		}
		return war.getBattleDay().equals(battleDayDate(now));
	}

	public static boolean isPostBattleChoiceDeadlineDue(War war, Instant now) {
		return isOnBattleDay(war, now) && battleDayHour(now) >= Cache.warDefenderChoiceDeadlineHour;
	}

	public static boolean isVoteCloseDue(War war, Instant now) {
		return isOnBattleDay(war, now) && battleDayHour(now) >= Cache.warVoteCloseHour;
	}

	public static boolean isBeforeVoteClose(War war, Instant now) {
		return isOnBattleDay(war, now) && battleDayHour(now) < Cache.warVoteCloseHour;
	}

	public static boolean isRaidWindowOpen(War war, Instant now) {
		if (WarDevMode.isEnabled() && war != null && war.isActive()) {
			return true;
		}
		if (!isOnBattleDay(war, now)) {
			return false;
		}
		int hour = battleDayHour(now);
		return hour >= Cache.warRaidWindowStartHour && hour <= Cache.warRaidWindowEndHour;
	}

	public static boolean isBattleWindowOpen(War war, Instant now) {
		if (!isOnBattleDay(war, now)) {
			return false;
		}
		int hour = battleDayHour(now);
		return hour >= Cache.warBattleWindowStartHour && hour <= Cache.warBattleWindowEndHour;
	}

	public static boolean needsPostBattleChoice(War war) {
		return CampaignPostBattleChoiceService.needsAnyChoice(war);
	}

	public static boolean isPostBattleChoiceResolved(War war) {
		if (!needsPostBattleChoice(war)) {
			return true;
		}
		return war.isPostBattleChoiceResolved();
	}

	public static boolean applyPostBattleChoiceDeadline(War war, Instant now) {
		return CampaignPostBattleChoiceService.applyDeadlineIfDue(war, now);
	}

	public static void enterAutoresolvePending(War war) {
		if (war == null) {
			return;
		}
		clearScheduledTargets(war);
		war.setBattleSchedulePhase(BattleSchedulePhase.AUTORESOLVE_PENDING);
		war.setAutoresolveProposedByAttacker(false);
		war.setAutoresolveProposedByDefender(false);
	}

	public static void openVote(War war) {
		if (war == null) {
			return;
		}
		war.clearSignupRemindersSent();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		clearScheduledTargets(war);
		war.setDefenderChoiceResolved(false);
	}

	public static BattleScheduleCloseResult closeVote(
			War war,
			Instant now,
			Function<UUID, Faction> uuidToFaction,
			Function<String, UUID> memberNameToUuid) {
		return closeVote(war, now, uuidToFaction, memberNameToUuid, CloseVoteOptions.scheduled());
	}

	public static BattleScheduleCloseResult closeVote(
			War war,
			Instant now,
			Function<UUID, Faction> uuidToFaction,
			Function<String, UUID> memberNameToUuid,
			CloseVoteOptions options) {
		if (war == null || !war.isActive() || uuidToFaction == null || memberNameToUuid == null) {
			return BattleScheduleCloseResult.SKIPPED;
		}
		if (options == null) {
			options = CloseVoteOptions.scheduled();
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return BattleScheduleCloseResult.SKIPPED;
		}
		if (!options.forceImmediate() && !isVoteCloseDue(war, now)) {
			return BattleScheduleCloseResult.SKIPPED;
		}

		if (needsPostBattleChoice(war) && !isPostBattleChoiceResolved(war)) {
			applyPostBattleChoiceDeadline(war, now);
			if (!isPostBattleChoiceResolved(war)) {
				return BattleScheduleCloseResult.BLOCKED_DEFENDER_CHOICE;
			}
		}

		boolean forceQuorum = options.forceQuorum() || war.isForceQuorumNextClose();
		clearForceQuorumNextClose(war);

		boolean quorumPassed = forceQuorum
				|| BattleQuorumService.meetsQuorum(war, memberNameToUuid).passed();
		if (quorumPassed && scheduleFromVotes(war, uuidToFaction)) {
			return BattleScheduleCloseResult.SCHEDULED;
		}

		int maxPostponements = Math.max(0, Cache.warBattleVotingMaxPostponements);
		if (war.getPostponementsThisCycle() < maxPostponements) {
			postpone(war, now);
			return BattleScheduleCloseResult.POSTPONED;
		}
		if (BattleAutoresolveService.resolve(war)) {
			return BattleScheduleCloseResult.AUTORESOLVED;
		}
		postpone(war, now);
		return BattleScheduleCloseResult.POSTPONED;
	}

	public static boolean scheduleFromVotes(War war, Function<UUID, Faction> uuidToFaction) {
		if (war == null || uuidToFaction == null || war.getBattleDay() == null) {
			return false;
		}

		OptionalInt pickedHour = BattleVoteService.pickHour(war, uuidToFaction);
		if (pickedHour.isEmpty()) {
			return false;
		}

		Integer provinceId = resolveScheduledProvinceId(war);
		if (provinceId == null) {
			return false;
		}

		int hour = pickedHour.getAsInt();
		Instant scheduledAt = BattleWindowService.computeScheduledBattleAt(war.getBattleDay(), hour);
		return scheduleBattleAtProvince(war, provinceId, scheduledAt);
	}

	public static boolean applyScheduledInstant(War war, Instant scheduledAt) {
		if (war == null || scheduledAt == null) {
			return false;
		}

		Integer provinceId = resolveScheduledProvinceId(war);
		if (provinceId == null) {
			return false;
		}

		return scheduleBattleAtProvince(war, provinceId, scheduledAt);
	}

	public static int castSpoofVotes(
			War war,
			int hour,
			Function<String, UUID> memberNameToUuid,
			BelligerentRole... sides) {
		if (war == null || memberNameToUuid == null || sides == null || sides.length == 0) {
			return 0;
		}
		if (!BattleWindowService.isValidHour(hour)) {
			return 0;
		}

		int added = 0;
		for (BelligerentRole side : sides) {
			Side belligerentSide = side == BelligerentRole.ATTACKER ? war.getAttackers() : war.getDefenders();
			for (String memberName : BattleSideMembers.collectEligibleMemberNames(belligerentSide)) {
				UUID playerId = memberNameToUuid.apply(memberName);
				if (playerId == null) {
					continue;
				}
				Set<Integer> selections = war.getBattleVotes().computeIfAbsent(playerId, ignored -> new HashSet<>());
				if (selections.add(hour)) {
					added++;
				}
			}
		}
		return added;
	}

	public static void postpone(War war) {
		postpone(war, Instant.now());
	}

	public static void postpone(War war, Instant now) {
		if (war == null || war.getBattleDay() == null) {
			return;
		}

		CampaignProgressionService.applyPostponedBattle(war);
		releaseStaffHold(war);
		war.setBattleDay(war.getBattleDay().plusDays(1));
		war.clearSignupRemindersSent();
		BattleInstallationPickService.clearForNewBattleDay(war);
		CampaignRaidService.clearForNewBattleDay(war);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		clearScheduledTargets(war);
		war.setPostponementsThisCycle(war.getPostponementsThisCycle() + 1);
		war.setDefenderChoiceResolved(false);
		PreparationFreeze.applyPostponement(war, now);
	}

	public static void skipBattleDay(War war) {
		skipBattleDay(war, Instant.now());
	}

	public static void skipBattleDay(War war, Instant now) {
		if (war == null || war.getBattleDay() == null) {
			return;
		}
		// A scheduled war keeps its start time here, so its hold stays: releasing it would start the battle.
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.SCHEDULED) {
			releaseStaffHold(war);
		}
		war.setBattleDay(war.getBattleDay().plusDays(1));
		BattleInstallationPickService.clearForNewBattleDay(war);
		CampaignRaidService.clearForNewBattleDay(war);
		PreparationFreeze.applyPostponement(war, now);
	}

	/**
	 * Moves the war to the battle day after the one just fought. A battle that runs past midnight
	 * still belongs to the evening it started on, so the next battle day is the day after that
	 * evening, never the same night. Votes, picks and raids from the fought day are cleared.
	 */
	public static void advanceAfterBattle(War war, Instant now) {
		if (war == null) {
			return;
		}
		LocalDate fought = war.getBattleDay();
		war.setBattleDay(firstVotableBattleDay(fought == null ? null : fought.plusDays(1),
				now != null ? now : Instant.now()));
		startFreshBattleDay(war);
	}

	/**
	 * Sets the battle day by hand. A war that was voting or scheduled goes back to voting on the
	 * new day with no votes, picks or raids carried over.
	 */
	public static boolean setBattleDay(War war, LocalDate day) {
		if (war == null || day == null) {
			return false;
		}
		war.setBattleDay(day);
		startFreshBattleDay(war);
		BattleSchedulePhase phase = war.getBattleSchedulePhase();
		if (phase == BattleSchedulePhase.VOTING || phase == BattleSchedulePhase.SCHEDULED) {
			openVote(war);
		}
		return true;
	}

	/**
	 * A voting war whose battle day already passed (for example while the server was down) moves
	 * to the current battle day so its vote can close again, or to the next one when today's vote
	 * has already closed. Not counted as a postponement.
	 */
	public static boolean catchUpStaleBattleDay(War war, Instant now) {
		if (war == null || now == null || war.getBattleDay() == null) {
			return false;
		}
		if (war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return false;
		}
		LocalDate current = battleDayDate(now);
		if (!war.getBattleDay().isBefore(current)) {
			return false;
		}
		war.setBattleDay(firstVotableBattleDay(current, now));
		startFreshBattleDay(war);
		return true;
	}

	/**
	 * {@code candidate}, or the first later battle day whose vote is still open: never a past day,
	 * and not today once today's vote close hour has passed.
	 */
	private static LocalDate firstVotableBattleDay(LocalDate candidate, Instant now) {
		LocalDate current = battleDayDate(now);
		LocalDate day = candidate == null || candidate.isBefore(current) ? current : candidate;
		if (day.equals(current) && battleDayHour(now) >= Cache.warVoteCloseHour) {
			return current.plusDays(1);
		}
		return day;
	}

	private static void startFreshBattleDay(War war) {
		releaseStaffHold(war);
		BattleVoteService.clearVotes(war);
		war.clearSignupRemindersSent();
		BattleInstallationPickService.clearForNewBattleDay(war);
		CampaignRaidService.clearForNewBattleDay(war);
	}

	/** A hold belongs to one battle day; a new day schedules the battle afresh. */
	static void releaseStaffHold(War war) {
		var battle = net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager.getByWarId(war.getId());
		if (battle != null && !battle.hasStarted() && battle.isStaffHold()) {
			battle.setStaffHold(false);
			net.tfminecraft.simplefactions.war.battle.persistence.BattlePersistenceService.persistBattle(battle);
		}
	}

	public static Integer resolveBattleProvinceId(War war) {
		if (war == null) {
			return null;
		}
		if (war.getScheduledBattleProvinceId() != null) {
			return war.getScheduledBattleProvinceId();
		}
		return resolveScheduledProvinceId(war);
	}

	public static boolean scheduleBattleAtProvince(War war, int provinceId, Instant scheduledAt) {
		if (war == null || scheduledAt == null) {
			return false;
		}
		LocalDate battleDay = war.getBattleDay();
		Integer scheduleHour = BattleWindowService.resolveScheduleHour(battleDay, scheduledAt);
		if (scheduleHour == null || !BattleWindowService.isValidHour(scheduleHour)) {
			return false;
		}
		war.clearSignupRemindersSent();
		war.setScheduledBattleHour(scheduleHour);
		war.setScheduledBattleAt(scheduledAt);
		war.setScheduledBattleProvinceId(provinceId);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setPostponementsThisCycle(0);
		CampaignBattleLaunchService.prepareScheduledBattle(war);
		return war.getScheduledBattleAt() != null;
	}

	public static boolean markScheduledAtProvince(War war, int provinceId) {
		if (war == null) {
			return false;
		}
		war.clearSignupRemindersSent();
		war.setScheduledBattleProvinceId(provinceId);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setPostponementsThisCycle(0);
		return CampaignBattleLaunchService.prepareScheduledBattle(war) != null;
	}

	public static Integer resolveScheduledProvinceId(War war) {
		if (needsPostBattleChoice(war) && !isPostBattleChoiceResolved(war)) {
			return null;
		}
		OptionalInt next = CampaignCapabilityService.nextBattleProvince(war);
		return next.isPresent() ? next.getAsInt() : null;
	}

	private static void clearForceQuorumNextClose(War war) {
		if (war != null && war.isForceQuorumNextClose()) {
			war.setForceQuorumNextClose(false);
		}
	}

	private static void clearScheduledTargets(War war) {
		war.setScheduledBattleAt(null);
		war.setScheduledBattleHour(0);
		war.setScheduledBattleProvinceId(null);
	}
}
