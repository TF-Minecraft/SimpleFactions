package net.tfminecraft.simplefactions.war.campaign.buildup;

import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleQuorumService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleVoteToggleResult;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.notice.WarNotices;

/**
 * A declared war waits before its first battle. It is frozen (see
 * {@link net.tfminecraft.simplefactions.war.freeze.PreparationFreeze}) until that battle starts, and
 * while in {@link BattleSchedulePhase#BUILDUP} its members vote on the battle's day, the way they vote
 * on its hour. Day picks are stored as offsets from the first day offered. When the day vote closes
 * the war moves to the ordinary hour vote on the chosen day.
 */
public final class BuildupService {

	/** How a day vote closed: the day chosen, and whether the vote reached quorum. */
	public record DayVoteResult(LocalDate day, boolean quorum) {
	}

	private BuildupService() {
	}

	/**
	 * Puts a newly declared war into its buildup. Does nothing when buildups are off or the war has no
	 * campaign schedule (legacy raid wars), and returns whether it started.
	 */
	public static boolean start(War war, Instant now) {
		if (!Cache.warBuildupEnabled || war == null || now == null
				|| war.getBattleSchedulePhase() != BattleSchedulePhase.VOTING) {
			return false;
		}
		war.setBuildup(true);
		war.setDayVoteFirstDay(BattleWindowService.scheduleDate(now).plusDays(Cache.warBuildupFirstOptionDay));
		war.setDayVoteClosesAt(now.plus(Cache.warBuildupDayVoteCloseHours, ChronoUnit.HOURS));
		war.getDayVotes().clear();
		war.getBattleVotes().clear();
		war.setBattleDay(null);
		war.setBattleSchedulePhase(BattleSchedulePhase.BUILDUP);
		return true;
	}

	public static boolean isDayVoteOpen(War war) {
		return war != null && war.isActive()
				&& war.getBattleSchedulePhase() == BattleSchedulePhase.BUILDUP
				&& war.getDayVoteFirstDay() != null;
	}

	/** The offsets the day vote offers: 0 is the first day. */
	public static List<Integer> dayOptions() {
		List<Integer> options = new ArrayList<>();
		for (int i = 0; i < Cache.warBuildupDayOptions; i++) {
			options.add(i);
		}
		return options;
	}

	public static LocalDate dayOf(War war, int offset) {
		return war.getDayVoteFirstDay() == null ? null : war.getDayVoteFirstDay().plusDays(offset);
	}

	public static boolean canToggleDayVote(War war, Faction faction) {
		return isDayVoteOpen(war) && faction != null && war.getSide(faction) != null;
	}

	public static BattleVoteToggleResult toggleDayVote(
			War war, UUID playerId, int offset, Faction playerFaction, boolean playerOnline) {
		if (war == null || playerId == null) {
			return BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT;
		}
		if (!playerOnline) {
			return BattleVoteToggleResult.REJECTED_OFFLINE;
		}
		if (playerFaction == null || war.getSide(playerFaction) == null) {
			return BattleVoteToggleResult.REJECTED_NOT_PARTICIPANT;
		}
		if (!isDayVoteOpen(war)) {
			return BattleVoteToggleResult.REJECTED_VOTE_CLOSED;
		}
		if (offset < 0 || offset >= Cache.warBuildupDayOptions) {
			return BattleVoteToggleResult.REJECTED_INVALID_HOUR;
		}
		Map<UUID, Set<Integer>> votes = war.getDayVotes();
		Set<Integer> picks = votes.computeIfAbsent(playerId, ignored -> new HashSet<>());
		if (picks.remove(offset)) {
			if (picks.isEmpty()) {
				votes.remove(playerId);
			}
			return BattleVoteToggleResult.REMOVED;
		}
		picks.add(offset);
		return BattleVoteToggleResult.ADDED;
	}

	public static Set<Integer> getPlayerSelections(War war, UUID playerId) {
		Set<Integer> picks = war == null || playerId == null ? null : war.getDayVotes().get(playerId);
		return picks == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(picks));
	}

	public static Map<Integer, VoteResults.BattleHourTally> buildDayTally(
			War war, Function<UUID, Faction> uuidToFaction) {
		return BattleVoteService.buildTally(war, war.getDayVotes(), dayOptions(), uuidToFaction);
	}

	/** Closes the day vote once its time has come. Returns whether the war changed. */
	public static boolean closeDayVoteIfDue(War war, Instant now) {
		if (!isDayVoteOpen(war) || now == null
				|| (war.getDayVoteClosesAt() != null && now.isBefore(war.getDayVoteClosesAt()))) {
			return false;
		}
		closeDayVote(war, now, BattleScheduleLookups.uuidToFactionForWar(war), BattleScheduleLookups.memberNameToUuid());
		return true;
	}

	/**
	 * Picks the first battle day the way the hour vote picks its hour; without quorum, or with no day
	 * both sides chose, the first day offered. A day already past moves to the next one that can still
	 * vote on its hour. The war then holds an ordinary hour vote on that day.
	 */
	public static DayVoteResult closeDayVote(
			War war, Instant now, Function<UUID, Faction> uuidToFaction, Function<String, UUID> memberNameToUuid) {
		boolean quorum = BattleQuorumService.meetsQuorum(war, war.getDayVotes(), memberNameToUuid).passed();
		OptionalInt picked = quorum ? BattleVoteService.pickBest(buildDayTally(war, uuidToFaction)) : OptionalInt.empty();
		LocalDate day = dayOf(war, picked.orElse(0));
		return setFirstBattleDay(war, day, now, quorum);
	}

	/** Staff: sets the first battle day and closes the day vote. */
	public static DayVoteResult setFirstBattleDay(War war, LocalDate day, Instant now, boolean quorum) {
		LocalDate chosen = BattleScheduleService.firstVotableBattleDay(day, now);
		war.setDayVoteFirstDay(null);
		war.setDayVoteClosesAt(null);
		war.getDayVotes().clear();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		BattleScheduleService.setBattleDay(war, chosen);
		tellBothSides(war, "§eThe first battle of " + war.getName() + " §eis on §f"
				+ CampaignUiCopy.formatBattleDay(chosen) + "§e. Vote on its hour in §a/war list§e.");
		WarHistoryEvents.dayVoteClosed(war, chosen, quorum);
		WarHistoryEvents.votesChanged(war);
		return new DayVoteResult(chosen, quorum);
	}

	/**
	 * Staff: ends the buildup now. The freeze lifts, and a war still voting on its day moves to the
	 * first battle day that can still vote on its hour.
	 */
	public static void end(War war, Instant now) {
		war.setBuildup(false);
		if (isDayVoteOpen(war)) {
			setFirstBattleDay(war, BattleWindowService.scheduleDate(now), now, false);
		}
	}

	private static void tellBothSides(War war, String text) {
		for (String member : BattleSideMembers.collectEligibleMemberNames(war.getAttackers())) {
			WarNotices.send(member, text);
		}
		for (String member : BattleSideMembers.collectEligibleMemberNames(war.getDefenders())) {
			WarNotices.send(member, text);
		}
	}
}
