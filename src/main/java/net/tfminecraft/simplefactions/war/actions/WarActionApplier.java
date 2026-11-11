package net.tfminecraft.simplefactions.war.actions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
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
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityMarks;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;
import net.tfminecraft.simplefactions.war.core.CallToArmsService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarHistoryJson;
import net.tfminecraft.simplefactions.war.history.WarVoting;

/**
 * Applies one website action on the main thread, with the checks the game makes for the same
 * click or command. Nothing here is a new rule: web marks are the account's marks, exactly as if
 * the player had clicked them, except that the player need not be online.
 */
final class WarActionApplier {
	static final String UNREADABLE = "The server could not read this action.";
	static final String UNKNOWN_KIND = "The server does not know this kind of action.";
	static final String WAR_OVER = "That war is over.";
	static final String NO_FACTION = "That faction no longer exists.";
	static final String NOT_LEADER = "You no longer lead that faction.";
	static final String NOT_A_MEMBER = AvailabilityMarks.NOT_ELIGIBLE;
	/** The old day and hour votes, still queued by a website that has not caught up. */
	static final String VOTE_CHANGED = "This vote has changed: mark your availability instead.";
	static final String BAD_DATE = "That is not a date.";
	/** More hours than any battle window has is not a mark. */
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
		if (WarAction.DAY_VOTE.equals(action.kind()) || WarAction.HOUR_VOTE.equals(action.kind())) {
			return VOTE_CHANGED;
		}
		if (!Set.of(WarAction.CALL_TO_ARMS_ANSWER, WarAction.AVAILABILITY).contains(action.kind())) {
			return UNKNOWN_KIND;
		}
		War war = activeWar(action.warKey());
		if (war == null) {
			return WAR_OVER;
		}
		return WarAction.CALL_TO_ARMS_ANSWER.equals(action.kind()) ? answer(war, action) : availability(war, action);
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

	/**
	 * {@code {"date": "2026-11-20", "hours": [21, 22]}} replaces the account's marks on that date
	 * ({@code []} clears them); {@code {"none": true}} answers "none of these days work".
	 */
	private static String availability(War war, WarAction action) {
		Faction faction = WarVoting.factionOf(war, action.account());
		Instant now = CampaignClock.now();
		JsonElement none = action.payload().get("none");
		if (none != null) {
			if (!(none instanceof JsonPrimitive flag) || !flag.isBoolean() || !flag.getAsBoolean()) {
				return UNREADABLE;
			}
			return changed(war, AvailabilityMarks.setNone(war, action.account(), faction, now));
		}
		String iso = WarAction.stringValue(action.payload().get("date"));
		JsonElement raw = action.payload().get("hours");
		if (iso == null || !(raw instanceof JsonArray list) || list.size() > MAX_PICKS) {
			return UNREADABLE;
		}
		LocalDate date;
		try {
			date = LocalDate.parse(iso);
		} catch (RuntimeException unreadable) {
			return BAD_DATE;
		}
		Set<Integer> hours = new HashSet<>();
		for (JsonElement pick : list) {
			Integer hour = hour(pick);
			if (hour == null) {
				return AvailabilityMarks.BAD_HOUR;
			}
			hours.add(hour);
		}
		return changed(war, AvailabilityMarks.setDate(war, action.account(), faction, date, hours, now));
	}

	/** Saves and reports a change that was made; passes a refusal through. */
	private static String changed(War war, String refusal) {
		if (refusal == null) {
			WarManager.persist(war);
			WarHistoryEvents.votesChanged(war);
		}
		return refusal;
	}

	private static Integer hour(JsonElement pick) {
		Long hour = pick instanceof JsonPrimitive primitive && primitive.isNumber() ? WarAction.longValue(pick) : null;
		return hour == null || hour < Integer.MIN_VALUE || hour > Integer.MAX_VALUE ? null : hour.intValue();
	}
}
