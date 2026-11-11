package net.tfminecraft.simplefactions.war.campaign.vote;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiTimeFormatter;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.history.WarVoting;

/**
 * Tells players who have not voted that a war's vote is open: when their character is next played,
 * and 24, 3 and 1 hours before it closes. A player who has picked anything in that vote hears nothing.
 * Mercenaries under contract to a side are reminded like the side's own members.
 */
public final class BattleVoteReminders {
	/** Hours before the close at which online players who have not voted are reminded. */
	static final int[] HOURS_BEFORE_CLOSE = {24, 3, 1};
	/** A reminder still goes out this long after its time, so a slow tick does not skip it. */
	static final Duration LATE_BY = Duration.ofMinutes(5);
	/** Reminders already sent: war, vote, close time and hours before. Lost on restart, at worst one repeat. */
	private static final Set<String> sent = new HashSet<>();

	/** The vote a war has open, and when it closes. */
	public record OpenVote(WarVoting.Kind kind, Instant closesAt) {
	}

	private BattleVoteReminders() {
	}

	static void clearForTests() {
		sent.clear();
	}

	/** The war's open vote, or null when none is open or it has no closing time. */
	public static OpenVote openVote(War war, Instant now) {
		WarVoting.Kind kind = WarVoting.open(war, now);
		if (kind == null) {
			return null;
		}
		Instant closesAt = WarVoting.closesAt(war, kind);
		return closesAt == null || !now.isBefore(closesAt) ? null : new OpenVote(kind, closesAt);
	}

	/** Whether the account has picked an option still on offer in this vote; outdated picks don't count. */
	public static boolean hasVoted(War war, UUID account, WarVoting.Kind kind) {
		Map<UUID, Set<Integer>> votes = kind == WarVoting.Kind.DAY ? war.getDayVotes() : war.getBattleVotes();
		Set<Integer> picks = votes.get(account);
		return picks != null && picks.stream().anyMatch(WarVoting.options(kind)::contains);
	}

	/** The reminder hours due now for this vote, each only once. */
	static List<Integer> dueReminders(War war, OpenVote vote, Instant now) {
		List<Integer> due = new ArrayList<>();
		for (int hours : HOURS_BEFORE_CLOSE) {
			Instant at = vote.closesAt().minus(Duration.ofHours(hours));
			if (now.isBefore(at) || !now.isBefore(at.plus(LATE_BY))) {
				continue;
			}
			if (sent.add(war.getId() + "|" + vote.kind() + "|" + vote.closesAt() + "|" + hours)) {
				due.add(hours);
			}
		}
		return due;
	}

	/** Every minute: reminds the online players who have not voted when a reminder falls due. */
	public static void process(War war, Instant now) {
		OpenVote vote = openVote(war, now);
		if (vote == null || dueReminders(war, vote, now).isEmpty()) {
			return;
		}
		for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
			remindIfNotVoted(war, player, vote, now);
		}
	}

	/** When a character is played: one line for each war whose open vote this player has not voted in. */
	public static void onCharacterStart(Player player, Instant now) {
		for (War war : WarManager.getActive()) {
			OpenVote vote = openVote(war, now);
			if (vote != null) {
				remindIfNotVoted(war, player, vote, now);
			}
		}
	}

	static boolean remindIfNotVoted(War war, Player player, OpenVote vote, Instant now) {
		if (player == null || BattleScheduleLookups.voterFaction(war, player) == null
				|| hasVoted(war, player.getUniqueId(), vote.kind())) {
			return false;
		}
		player.sendMessage(message(war, vote, now));
		return true;
	}

	static Component message(War war, OpenVote vote, Instant now) {
		String what = vote.kind() == WarVoting.Kind.DAY ? "the day of the first battle" : "the battle hour";
		String text = "§6⚔ §e" + war.getName() + "§6: §fyou have not voted on " + what + ". §7Voting closes in §f"
				+ formatRemaining(Duration.between(now, vote.closesAt())) + " §7("
				+ CampaignUiTimeFormatter.formatDateTime(vote.closesAt()) + "§7). §a§l[Click to vote]";
		return LegacyComponentSerializer.legacySection()
				.deserialize(StringFormatter.formatHex(text))
				.clickEvent(ClickEvent.runCommand("/war vote " + war.getId()))
				.hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection()
						.deserialize("§7Open this war's vote")));
	}

	/** {@code 2d 4h}, {@code 3h 20m} or {@code 45m}. */
	static String formatRemaining(Duration remaining) {
		long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
		long days = minutes / 1440;
		long hours = (minutes % 1440) / 60;
		long mins = minutes % 60;
		if (days > 0) {
			return days + "d " + hours + "h";
		}
		if (hours > 0) {
			return mins > 0 ? hours + "h " + mins + "m" : hours + "h";
		}
		return mins + "m";
	}
}
