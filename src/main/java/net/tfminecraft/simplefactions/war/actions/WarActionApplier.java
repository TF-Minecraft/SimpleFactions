package net.tfminecraft.simplefactions.war.actions;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoterEligibility;
import net.tfminecraft.simplefactions.war.core.CallToArmsService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarHistoryJson;
import net.tfminecraft.simplefactions.war.history.WarVoting;

/**
 * Applies one website action on the main thread, with the checks the game makes for the same
 * click or command. Nothing here is a new rule: a web vote is the account's vote, exactly as if
 * the player had clicked it, except that the player need not be online.
 */
final class WarActionApplier {
	static final String UNREADABLE = "The server could not read this action.";
	static final String UNKNOWN_KIND = "The server does not know this kind of action.";
	static final String WAR_OVER = "That war is over.";
	static final String NO_FACTION = "That faction no longer exists.";
	static final String NOT_LEADER = "You no longer lead that faction.";
	static final String NOT_A_MEMBER = "Only members of the warring factions vote.";
	static final String DAY_VOTE_CLOSED = "The vote on the first battle day is closed.";
	static final String HOUR_VOTE_CLOSED = "The vote on the battle hour is closed.";
	static final String BAD_DAY = "One of your picks is not a day on offer.";
	static final String BAD_HOUR = "One of your picks is not an hour on offer.";
	/** More picks than any vote offers is not a vote. */
	static final int MAX_PICKS = 50;

	WarActionResult apply(WarAction action) {
		String reason = reason(action);
		return reason == null ? WarActionResult.applied(action.id()) : WarActionResult.rejected(action.id(), reason);
	}

	/** Null when the action was carried out, otherwise why not. */
	private String reason(WarAction action) {
		if (action.kind() == null || action.account() == null || action.payload() == null || action.warKey() == null) {
			return UNREADABLE;
		}
		if (!Set.of(WarAction.CALL_TO_ARMS_ANSWER, WarAction.DAY_VOTE, WarAction.HOUR_VOTE).contains(action.kind())) {
			return UNKNOWN_KIND;
		}
		War war = activeWar(action.warKey());
		if (war == null) {
			return WAR_OVER;
		}
		return switch (action.kind()) {
			case WarAction.CALL_TO_ARMS_ANSWER -> answer(war, action);
			case WarAction.DAY_VOTE -> vote(war, action, WarVoting.Kind.DAY);
			default -> vote(war, action, WarVoting.Kind.HOUR);
		};
	}

	private static War activeWar(String warKey) {
		for (War war : WarManager.getActive()) {
			if (warKey.equals(WarHistoryJson.warKey(war))) {
				return war;
			}
		}
		return null;
	}

	private static String answer(War war, WarAction action) {
		String factionId = WarAction.stringValue(action.payload().get("target_faction_id"));
		JsonElement accept = action.payload().get("accept");
		if (factionId == null || !(accept instanceof JsonPrimitive flag) || !flag.isBoolean()) {
			return UNREADABLE;
		}
		Faction faction = FactionManager.getByString(factionId);
		if (faction == null) {
			return NO_FACTION;
		}
		Optional<UUID> owner = Members.accountId(faction.getLeader());
		if (owner.isEmpty() || !owner.get().equals(action.account())) {
			return NOT_LEADER;
		}
		CallToArmsService.Answer answer = flag.getAsBoolean()
				? CallToArmsService.accept(war, faction)
				: CallToArmsService.decline(war, faction);
		return answer.done() ? null : answer.reason();
	}

	private static String vote(War war, WarAction action, WarVoting.Kind kind) {
		JsonElement raw = action.payload().get("picks");
		if (!(raw instanceof JsonArray list) || list.size() > MAX_PICKS) {
			return UNREADABLE;
		}
		Faction faction = WarVoting.factionOf(war, action.account());
		if (faction == null) {
			return NOT_A_MEMBER;
		}
		Instant now = CampaignClock.now();
		boolean open = kind == WarVoting.Kind.DAY
				? BuildupService.canToggleDayVote(war, faction)
				: BattleVoterEligibility.canToggleVote(war, faction, now);
		if (!open) {
			return kind == WarVoting.Kind.DAY ? DAY_VOTE_CLOSED : HOUR_VOTE_CLOSED;
		}
		Set<Integer> picks = new HashSet<>();
		for (JsonElement pick : list) {
			Integer option = kind == WarVoting.Kind.DAY ? day(war, pick) : hour(pick);
			if (option == null) {
				return kind == WarVoting.Kind.DAY ? BAD_DAY : BAD_HOUR;
			}
			picks.add(option);
		}
		Map<UUID, Set<Integer>> votes = kind == WarVoting.Kind.DAY ? war.getDayVotes() : war.getBattleVotes();
		if (picks.isEmpty()) {
			votes.remove(action.account());
		} else {
			votes.put(action.account(), picks);
		}
		WarManager.persist(war);
		WarHistoryEvents.votesChanged(war);
		return null;
	}

	private static Integer day(War war, JsonElement pick) {
		String iso = WarAction.stringValue(pick);
		return iso == null ? null : WarVoting.dayOffset(war, iso);
	}

	private static Integer hour(JsonElement pick) {
		Long hour = pick instanceof JsonPrimitive primitive && primitive.isNumber() ? WarAction.longValue(pick) : null;
		if (hour == null || hour < Integer.MIN_VALUE || hour > Integer.MAX_VALUE
				|| !BattleWindowService.isValidHour(hour.intValue())) {
			return null;
		}
		return hour.intValue();
	}
}
