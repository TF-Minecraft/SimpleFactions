package me.Plugins.SimpleFactions.War.campaign.runtime;


import me.Plugins.SimpleFactions.War.campaign.runtime.pick.BattleInstallationPickService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Side;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleLaunchService;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignProgressionService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.vote.BattleQuorumService;
import me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.BattleScheduleCloseResult;
import me.Plugins.SimpleFactions.War.campaign.vote.BattleVoteService;
import me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.CloseVoteOptions;

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

		postpone(war);
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
			if (belligerentSide == null) {
				continue;
			}
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
		if (war == null || war.getBattleDay() == null) {
			return;
		}

		CampaignProgressionService.applyPostponedBattle(war);
		war.setBattleDay(war.getBattleDay().plusDays(1));
		war.clearSignupRemindersSent();
		BattleInstallationPickService.clearForNewBattleDay(war);
		CampaignRaidService.clearForNewBattleDay(war);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		clearScheduledTargets(war);
		war.setPostponementsThisCycle(war.getPostponementsThisCycle() + 1);
		war.setDefenderChoiceResolved(false);
	}

	public static void skipBattleDay(War war) {
		if (war == null || war.getBattleDay() == null) {
			return;
		}
		war.setBattleDay(war.getBattleDay().plusDays(1));
		BattleInstallationPickService.clearForNewBattleDay(war);
		CampaignRaidService.clearForNewBattleDay(war);
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
		war.clearSignupRemindersSent();
		LocalDate battleDay = war.getBattleDay();
		Integer scheduleHour = BattleWindowService.resolveScheduleHour(battleDay, scheduledAt);
		if (scheduleHour == null || !BattleWindowService.isValidHour(scheduleHour)) {
			return false;
		}
		if (battleDay == null) {
			battleDay = scheduleHour == 24
					? scheduledAt.atZone(BattleWindowService.SCHEDULE_ZONE).toLocalDate().minusDays(1)
					: scheduledAt.atZone(BattleWindowService.SCHEDULE_ZONE).toLocalDate();
			war.setBattleDay(battleDay);
		}
		war.setScheduledBattleHour(scheduleHour);
		war.setScheduledBattleAt(scheduledAt);
		war.setScheduledBattleProvinceId(provinceId);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
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
