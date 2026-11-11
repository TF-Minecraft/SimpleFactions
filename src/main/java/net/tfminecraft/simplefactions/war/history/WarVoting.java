package net.tfminecraft.simplefactions.war.history;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleSideMembers;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.vote.BattleVoteService;
import net.tfminecraft.simplefactions.war.campaign.vote.VoteResults.BattleHourTally;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryEngagements;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;

/**
 * A war's open vote as the website shows it: the buildup's vote on the first battle day, or the
 * vote on a battle day's hour. Day picks are stored as offsets from the first day offered and shown
 * as ISO dates; hours are battle-day hours as in game (24 and 25 after midnight). This is private
 * data: it goes to the website under an event's {@code private} block and is never published.
 */
public final class WarVoting {
	/** At most this many accounts are listed as eligible, and at most this many accounts' picks. Tests lower it. */
	static int maxAccounts = 2000;
	private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ENGLISH);

	/** Which vote is open. */
	public enum Kind {
		DAY("day"),
		HOUR("hour");

		private final String json;

		Kind(String json) {
			this.json = json;
		}

		public String toJson() {
			return json;
		}
	}

	private WarVoting() {
	}

	/** The vote players can change now, or null; the same conditions as the in-game vote items. */
	public static Kind open(War war, Instant now) {
		if (BuildupService.isDayVoteOpen(war)) {
			return Kind.DAY;
		}
		if (war != null && war.isActive() && war.getBattleSchedulePhase() == BattleSchedulePhase.VOTING
				&& !BattleScheduleService.isVoteCloseDue(war, now)) {
			return Kind.HOUR;
		}
		return null;
	}

	/** The day vote's offsets or the hour vote's hours, in order. */
	public static List<Integer> options(Kind kind) {
		return kind == Kind.DAY ? BuildupService.dayOptions() : BattleWindowService.listValidHours();
	}

	/**
	 * A mercenary company hired by this faction gained or lost a soldier or a contract: the website relearns
	 * who may vote in the faction's wars.
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

	/** The account's faction in this war, by the same lookup the vote tallies use; null for none. */
	public static Faction factionOf(War war, UUID account) {
		return BattleScheduleLookups.votingFaction(war, account);
	}

	/** A day pick as the website sends it ({@code 2026-11-17}), as the offset the war stores; null if not offered. */
	public static Integer dayOffset(War war, String isoDate) {
		LocalDate first = war.getDayVoteFirstDay();
		LocalDate day;
		try {
			day = LocalDate.parse(isoDate);
		} catch (RuntimeException unreadable) {
			return null;
		}
		if (first == null) {
			return null;
		}
		long offset = java.time.temporal.ChronoUnit.DAYS.between(first, day);
		return offset >= 0 && offset < Cache.warBuildupDayOptions ? (int) offset : null;
	}

	/** {@code private.voting}, built from the live war. */
	static JsonObject json(War war, Instant now) {
		Kind kind = open(war, now);
		JsonObject voting = new JsonObject();
		voting.addProperty("kind", kind == null ? null : kind.toJson());
		JsonArray options = new JsonArray();
		JsonArray labels = new JsonArray();
		JsonObject votes = new JsonObject();
		JsonArray tally = new JsonArray();
		if (kind != null) {
			for (int option : options(kind)) {
				addOption(options, war, kind, option);
				labels.add(label(war, kind, option));
			}
			Map<UUID, Set<Integer>> stored = kind == Kind.DAY ? war.getDayVotes() : war.getBattleVotes();
			List<Integer> valid = options(kind);
			for (Map.Entry<UUID, Set<Integer>> entry : stored.entrySet()) {
				if (votes.size() >= maxAccounts) {
					break;
				}
				JsonArray picks = picks(war, kind, valid, entry.getValue());
				if (entry.getKey() != null && !picks.isEmpty()) {
					votes.add(entry.getKey().toString(), picks);
				}
			}
			Map<Integer, BattleHourTally> counts = BattleVoteService.buildTally(war, stored, valid,
					BattleScheduleLookups.uuidToFactionForWar(war));
			for (int option : valid) {
				BattleHourTally count = counts.getOrDefault(option, new BattleHourTally(0, 0));
				JsonObject row = new JsonObject();
				if (kind == Kind.DAY) {
					row.addProperty("option", day(war, option));
				} else {
					row.addProperty("option", option);
				}
				row.addProperty(WarHistoryJson.ATTACKER, count.attackerCount());
				row.addProperty(WarHistoryJson.DEFENDER, count.defenderCount());
				tally.add(row);
			}
		}
		voting.add("options", options);
		voting.add("option_labels", labels);
		voting.addProperty("closes_at", WarHistoryJson.instant(closesAt(war, kind)));
		voting.add("eligible", eligible(war));
		voting.add("votes", votes);
		voting.add("tally", tally);
		return voting;
	}

	/**
	 * Every account playing a member of a participating faction, then every mercenary under contract to
	 * a side, with its side; attackers first, as the tallies read them.
	 */
	static JsonObject eligible(War war) {
		Map<String, String> accounts = new LinkedHashMap<>();
		addSide(accounts, BattleSideMembers.collectParticipatingFactions(war.getAttackers()), WarHistoryJson.ATTACKER);
		addSide(accounts, BattleSideMembers.collectParticipatingFactions(war.getDefenders()), WarHistoryJson.DEFENDER);
		addMercenaries(accounts, war, war.getAttackers(), WarHistoryJson.ATTACKER);
		addMercenaries(accounts, war, war.getDefenders(), WarHistoryJson.DEFENDER);
		JsonObject eligible = new JsonObject();
		accounts.forEach(eligible::addProperty);
		return eligible;
	}

	private static void addSide(Map<String, String> accounts, List<Faction> factions, String side) {
		for (Faction faction : factions) {
			for (String member : new ArrayList<>(faction.getMembers())) {
				if (accounts.size() >= maxAccounts) {
					return;
				}
				Members.accountId(member).ifPresent(owner -> accounts.putIfAbsent(owner.toString(), side));
			}
		}
	}

	private static void addMercenaries(Map<String, String> accounts, War war, Side side, String label) {
		for (MercenaryEngagements.Engagement engagement : MercenaryEngagements.on(war, side)) {
			for (String enlisted : new ArrayList<>(engagement.company().getEnlisted())) {
				if (accounts.size() >= maxAccounts) {
					return;
				}
				Members.accountId(enlisted).ifPresent(owner -> accounts.putIfAbsent(owner.toString(), label));
			}
		}
	}

	/** One account's picks still on offer, in order. */
	private static JsonArray picks(War war, Kind kind, List<Integer> valid, Set<Integer> stored) {
		JsonArray picks = new JsonArray();
		for (int pick : stored == null ? Set.<Integer>of() : new TreeSet<>(stored)) {
			if (valid.contains(pick)) {
				addOption(picks, war, kind, pick);
			}
		}
		return picks;
	}

	/** When the open vote closes; null for none. */
	public static Instant closesAt(War war, Kind kind) {
		if (kind == Kind.DAY) {
			return war.getDayVoteClosesAt();
		}
		return kind == Kind.HOUR ? BattleWindowService.atScheduleHour(war.getBattleDay(), Cache.warVoteCloseHour) : null;
	}

	private static void addOption(JsonArray into, War war, Kind kind, int option) {
		if (kind == Kind.DAY) {
			into.add(day(war, option));
		} else {
			into.add(option);
		}
	}

	private static String day(War war, int offset) {
		return BuildupService.dayOf(war, offset).toString();
	}

	private static String label(War war, Kind kind, int option) {
		return kind == Kind.DAY ? DAY_LABEL.format(BuildupService.dayOf(war, option))
				: BattleWindowService.formatHourLabel(option);
	}
}
