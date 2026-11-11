package net.tfminecraft.simplefactions.war.history;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityCalendar.DateState;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityScheduler;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally.HourCount;
import net.tfminecraft.simplefactions.war.campaign.availability.BattleAvailability;
import net.tfminecraft.simplefactions.war.campaign.availability.LockedBattleDay;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;

/**
 * A war's battle availability as the website shows it ({@code kind: "availability"}, contract
 * v1.4): the start hours, the dates players can see with their state and locks, each eligible
 * account's marks and "none of these days" answers, and the marks per side. Hours are battle-day
 * hours as in game (24 and 25 after midnight). Private: it goes to the website under an event's
 * {@code private} block and is never published.
 */
public final class WarVoting {
	public static final String KIND = "availability";
	/** At most this many accounts are listed as eligible, and at most this many accounts' marks. Tests lower it. */
	static int maxAccounts = 2000;

	private WarVoting() {
	}

	/**
	 * A mercenary company hired by this faction gained or lost a soldier or a contract: the website relearns
	 * who may mark in the faction's wars.
	 */
	public static void mercenaryVotersChanged(Faction hirer) {
		if (hirer == null) {
			return;
		}
		for (War war : WarManager.getActive()) {
			if (war.isParticipating(hirer)) {
				WarHistoryEvents.votesChanged(war);
			}
		}
	}

	/** The account's faction in this war, by the same lookup the tallies use; null for none. */
	public static Faction factionOf(War war, UUID account) {
		return BattleScheduleLookups.votingFaction(war, account);
	}

	/** {@code private.voting}, built from the live war. {@code kind} is null for a war without a battle schedule. */
	static JsonObject json(War war, Instant now) {
		JsonObject voting = new JsonObject();
		if (!AvailabilityScheduler.isScheduling(war)) {
			voting.addProperty("kind", (String) null);
			return voting;
		}
		voting.addProperty("kind", KIND);
		List<Integer> hours = BattleWindowService.listValidHours();
		JsonArray hourList = new JsonArray();
		JsonArray labels = new JsonArray();
		for (int hour : hours) {
			hourList.add(hour);
			labels.add(BattleWindowService.formatHourLabel(hour));
		}
		voting.add("hours", hourList);
		voting.add("hour_labels", labels);
		List<LocalDate> visible = AvailabilityCalendar.visibleDates(war, now);
		voting.add("dates", dates(war, visible, now));
		voting.addProperty("closes_at", WarHistoryJson.instant(AvailabilityCalendar.nextLockAt(war, now)));
		JsonObject need = new JsonObject();
		need.addProperty(WarHistoryJson.ATTACKER, AvailabilityTally.need(war, BelligerentRole.ATTACKER));
		need.addProperty(WarHistoryJson.DEFENDER, AvailabilityTally.need(war, BelligerentRole.DEFENDER));
		voting.add("need", need);
		voting.add("eligible", eligible(war));
		voting.add("marks", marks(war.getAvailability(), visible));
		voting.add("none", none(war.getAvailability(), visible));
		voting.add("tally", tally(war, visible));
		return voting;
	}

	private static JsonArray dates(War war, List<LocalDate> visible, Instant now) {
		JsonArray dates = new JsonArray();
		for (LocalDate date : visible) {
			DateState state = AvailabilityCalendar.state(war, date);
			LockedBattleDay battle = war.getAvailability().battleDay(date);
			JsonObject row = new JsonObject();
			row.addProperty("date", date.toString());
			row.addProperty("label", AvailabilityCalendar.label(date));
			row.addProperty("state", state.toJson());
			row.addProperty("day_locks_at", WarHistoryJson.instant(AvailabilityCalendar.dayLockAt(date)));
			row.addProperty("time_locks_at", WarHistoryJson.instant(AvailabilityCalendar.timeLockAt(date)));
			row.addProperty("hour", battle == null ? null : battle.hour());
			row.addProperty("editable", AvailabilityCalendar.editable(war, date, now));
			row.addProperty("forced", battle != null && battle.forced());
			dates.add(row);
		}
		return dates;
	}

	/** Every account that may mark, with its side; attackers first, mercenaries after members. */
	static JsonObject eligible(War war) {
		JsonObject eligible = new JsonObject();
		for (Map.Entry<UUID, BelligerentRole> entry : AvailabilityTally.eligibleAccounts(war).entrySet()) {
			if (eligible.size() >= maxAccounts) {
				break;
			}
			eligible.addProperty(entry.getKey().toString(),
					entry.getValue() == BelligerentRole.ATTACKER ? WarHistoryJson.ATTACKER : WarHistoryJson.DEFENDER);
		}
		return eligible;
	}

	/** Each account's marks on the dates shown, by ISO date; accounts with none there are left out. */
	private static JsonObject marks(BattleAvailability availability, List<LocalDate> visible) {
		JsonObject marks = new JsonObject();
		for (Map.Entry<UUID, Map<LocalDate, Set<Integer>>> entry : availability.marks().entrySet()) {
			if (marks.size() >= maxAccounts) {
				break;
			}
			JsonObject byDate = new JsonObject();
			for (LocalDate date : visible) {
				Set<Integer> hours = entry.getValue().get(date);
				if (hours != null) {
					JsonArray list = new JsonArray();
					hours.forEach(list::add);
					byDate.add(date.toString(), list);
				}
			}
			if (byDate.size() > 0) {
				marks.add(entry.getKey().toString(), byDate);
			}
		}
		return marks;
	}

	/** Accounts whose "none of these days work" answer still covers a date shown. */
	private static JsonArray none(BattleAvailability availability, List<LocalDate> visible) {
		JsonArray none = new JsonArray();
		for (Map.Entry<UUID, LocalDate> entry : availability.none().entrySet()) {
			if (!entry.getValue().isBefore(visible.get(0)) && none.size() < maxAccounts) {
				none.add(entry.getKey().toString());
			}
		}
		return none;
	}

	private static JsonArray tally(War war, List<LocalDate> visible) {
		JsonArray tally = new JsonArray();
		var sides = AvailabilityTally.sides(war);
		for (LocalDate date : visible) {
			for (HourCount count : AvailabilityTally.count(war, date, sides)) {
				JsonObject row = new JsonObject();
				row.addProperty("date", date.toString());
				row.addProperty("hour", count.hour());
				row.addProperty(WarHistoryJson.ATTACKER, count.attackers());
				row.addProperty(WarHistoryJson.DEFENDER, count.defenders());
				tally.add(row);
			}
		}
		return tally;
	}
}
