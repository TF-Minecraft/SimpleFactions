package net.tfminecraft.simplefactions.war.stats;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.battle.ui.BattlePermissions;

/**
 * Chat views of battle stats. Players see the side totals, their own numbers and
 * where they ranked; staff also see every fighter and the class shares.
 */
public final class BattleStatsView {
	static final int PAGE_SIZE = 8;
	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("d MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

	private BattleStatsView() {
	}

	/** {@code /battle stats [page | <id> | war [<war>]]} */
	public static void command(Player player, String[] args) {
		boolean staff = BattlePermissions.isAdmin(player);
		String key = Members.key(player);
		if (key == null && !staff) {
			player.sendMessage("§cChoose a character to see your battle stats.");
			return;
		}
		String viewer = staff ? null : key;
		if (args.length >= 2 && args[1].equalsIgnoreCase("war")) {
			if (args.length >= 3) {
				warReport(player, viewer, key, args[2]);
			} else {
				warList(player, viewer);
			}
			return;
		}
		if (args.length >= 2 && !isPage(args[1])) {
			BattleRecord record = BattleStatsArchive.get(args[1].toLowerCase(Locale.ROOT));
			if (record == null || (viewer != null && record.fighter(viewer) == null)) {
				player.sendMessage("§cNo battle stats with that ID that you fought in.");
				return;
			}
			report(player, record, key, staff);
			return;
		}
		battleList(player, viewer, args.length >= 2 ? Integer.parseInt(args[1]) : 1);
	}

	/** Sends every online fighter of a just-finished battle their summary. */
	static void announce(BattleRecord record) {
		for (FighterStats fighter : record.fighters) {
			Player player = Members.player(fighter.key);
			if (player != null && player.isOnline()) {
				report(player, record, fighter.key, false);
			}
		}
	}

	static void report(Player player, BattleRecord record, String key, boolean staff) {
		player.sendMessage("§6§l" + record.name + " §7(" + kind(record) + ", "
				+ duration(record.endedAt - record.startedAt) + ", " + DATE.format(Instant.ofEpochMilli(record.endedAt)) + ")");
		if (record.warName != null) {
			player.sendMessage("§7War: §f" + record.warName);
		}
		player.sendMessage(record.winner == null
				? "§7No winner §8(" + reason(record.endReason) + ")"
				: "§aWinner: §e" + record.winner + " §8(" + reason(record.endReason) + ")");
		for (BattleRecord.SideRecord side : record.sides) {
			player.sendMessage(sideLine(record, side));
		}
		FighterStats own = key == null ? null : record.fighter(key);
		if (own != null) {
			int of = record.fighters.size();
			player.sendMessage("§bYou §7(" + own.name + ", " + own.side + classSuffix(own) + ")");
			player.sendMessage(" §fKills §e" + own.kills + " §7(#" + rank(record.fighters, own, f -> f.kills) + " of " + of
					+ ") §8· §fDamage §e" + round(own.damage) + " §7(#" + rank(record.fighters, own, f -> f.damage) + " of " + of
					+ ") §8· §fDeaths §e" + own.deaths);
			player.sendMessage(" §7Damage taken " + round(own.damageTaken) + " §8· §7Friendly fire " + round(own.friendlyDamage)
					+ " damage, " + own.teamKills + " team kills");
		}
		if (staff) {
			staffDetail(player, record);
		} else {
			player.sendMessage(button("[Battle history]", "/battle stats", "All the battles you fought in"));
		}
	}

	private static void staffDetail(Player player, BattleRecord record) {
		List<FighterStats> ranked = new ArrayList<>(record.fighters);
		ranked.sort(Comparator.comparingInt((FighterStats f) -> f.kills).thenComparingDouble(f -> f.damage).reversed());
		player.sendMessage("§6Fighters §7(kills/deaths/team kills, damage, friendly, taken, blocked hits, logouts):");
		for (FighterStats f : ranked) {
			player.sendMessage(" §f" + f.name + " §7" + f.side + classSuffix(f) + " §e" + f.kills + "/" + f.deaths + "/" + f.teamKills
					+ " §f" + round(f.damage) + " §7ff " + round(f.friendlyDamage) + " taken " + round(f.damageTaken)
					+ (f.blockedHits > 0 ? " §cblocked " + f.blockedHits : "") + (f.logouts > 0 ? " §7logouts " + f.logouts : ""));
		}
		double totalDamage = 0;
		int totalKills = 0;
		Map<String, double[]> byClass = new LinkedHashMap<>();
		for (FighterStats f : ranked) {
			double[] sums = byClass.computeIfAbsent(f.className == null ? "No class" : f.className, c -> new double[3]);
			sums[0] += f.damage;
			sums[1] += f.kills;
			sums[2]++;
			totalDamage += f.damage;
			totalKills += f.kills;
		}
		player.sendMessage("§6Classes §7(fighters, damage share, kills):");
		for (Map.Entry<String, double[]> entry : byClass.entrySet()) {
			double[] sums = entry.getValue();
			player.sendMessage(" §f" + entry.getKey() + " §7" + (int) sums[2] + " §e" + percent(sums[0], totalDamage)
					+ " §7" + (int) sums[1] + "/" + totalKills);
		}
	}

	private static void battleList(Player player, String viewer, int page) {
		List<BattleStatsArchive.Summary> battles = BattleStatsArchive.list(viewer);
		if (battles.isEmpty()) {
			player.sendMessage("§7No battle stats yet.");
			return;
		}
		int pages = (battles.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int shown = Math.max(1, Math.min(page, pages));
		player.sendMessage("§6Battle history §7(page " + shown + " of " + pages + ", click one to open it)");
		for (BattleStatsArchive.Summary summary : battles.subList((shown - 1) * PAGE_SIZE,
				Math.min(battles.size(), shown * PAGE_SIZE))) {
			player.sendMessage(button(" " + summary.name() + " §7" + DATE.format(Instant.ofEpochMilli(summary.endedAt()))
					+ (summary.winner() == null ? " §8no winner" : " §8won by " + summary.winner()),
					"/battle stats " + summary.id(), summary.warName() == null ? "Open the report" : summary.warName()));
		}
		if (shown < pages) {
			player.sendMessage(button("[Next page]", "/battle stats " + (shown + 1), "Older battles"));
		}
		player.sendMessage(button("[Wars]", "/battle stats war", "Totals per war"));
	}

	private static void warList(Player player, String viewer) {
		Map<String, String> wars = new LinkedHashMap<>();
		for (BattleStatsArchive.Summary summary : BattleStatsArchive.list(viewer)) {
			if (summary.warKey() != null) {
				wars.putIfAbsent(summary.warKey(), summary.warName());
			}
		}
		if (wars.isEmpty()) {
			player.sendMessage("§7No war battles yet.");
			return;
		}
		player.sendMessage("§6Wars §7(click one to open it)");
		for (Map.Entry<String, String> war : wars.entrySet()) {
			player.sendMessage(button(" " + war.getValue(), "/battle stats war " + war.getKey(), "Battles and totals"));
		}
	}

	/** Built from the in-memory summaries only, so a long war reads no files. */
	private static void warReport(Player player, String viewer, String key, String warKey) {
		List<BattleStatsArchive.Summary> battles = new ArrayList<>();
		for (BattleStatsArchive.Summary summary : BattleStatsArchive.list(viewer)) {
			if (warKey.equalsIgnoreCase(summary.warKey())) {
				battles.add(summary);
			}
		}
		if (battles.isEmpty()) {
			player.sendMessage("§cNo battle stats for that war that you fought in.");
			return;
		}
		player.sendMessage("§6§l" + battles.get(0).warName() + " §7(" + battles.size() + " battles)");
		Map<String, Integer> wins = new LinkedHashMap<>();
		Map<String, FighterStats> totals = new LinkedHashMap<>();
		for (BattleStatsArchive.Summary battle : battles) {
			if (battle.winner() != null) {
				wins.merge(battle.winner(), 1, Integer::sum);
			}
			for (FighterStats f : battle.fighters().values()) {
				FighterStats sum = totals.computeIfAbsent(f.key, k -> new FighterStats());
				sum.key = f.key;
				sum.kills += f.kills;
				sum.deaths += f.deaths;
				sum.teamKills += f.teamKills;
				sum.damage += f.damage;
			}
			player.sendMessage(button(" " + battle.name() + " §8" + (battle.winner() == null ? "no winner" : "won by " + battle.winner()),
					"/battle stats " + battle.id(), "Open the report"));
		}
		StringBuilder line = new StringBuilder("§7Wins:");
		for (Map.Entry<String, Integer> entry : wins.entrySet()) {
			line.append(" §e").append(entry.getKey()).append(" §f").append(entry.getValue());
		}
		player.sendMessage(wins.isEmpty() ? "§7Wins: none" : line.toString());
		FighterStats own = key == null ? null : totals.get(key);
		if (own != null) {
			List<FighterStats> all = new ArrayList<>(totals.values());
			player.sendMessage("§bYou §7in this war: §fKills §e" + own.kills + " §7(#" + rank(all, own, f -> f.kills) + " of "
					+ all.size() + ") §8· §fDamage §e" + round(own.damage) + " §7(#" + rank(all, own, f -> f.damage) + " of "
					+ all.size() + ") §8· §fDeaths §e" + own.deaths + " §8· §7Team kills " + own.teamKills);
		}
	}

	private static String sideLine(BattleRecord record, BattleRecord.SideRecord side) {
		int kills = 0;
		int deaths = 0;
		int fighters = 0;
		double damage = 0;
		TreeSet<String> factions = new TreeSet<>();
		for (FighterStats f : record.fighters) {
			if (side.id.equalsIgnoreCase(f.side)) {
				kills += f.kills;
				deaths += f.deaths;
				damage += f.damage;
				fighters++;
				if (f.faction != null) {
					factions.add(f.faction);
				}
			}
		}
		return " §e" + side.id + " §7" + fighters + " fighters" + (factions.isEmpty() ? "" : " §8(" + String.join(", ", factions) + "§8)")
				+ " §7· " + kills + " kills, " + deaths + " deaths, " + round(damage) + " damage"
				+ (side.maxLives > 0 ? ", lives " + side.lives + "/" + side.maxLives : "");
	}

	/** 1 + how many fighters did strictly better, so ties share a place. */
	static int rank(List<FighterStats> fighters, FighterStats own, ToDoubleFunction<FighterStats> value) {
		double mine = value.applyAsDouble(own);
		int better = 0;
		for (FighterStats other : fighters) {
			if (value.applyAsDouble(other) > mine) {
				better++;
			}
		}
		return better + 1;
	}

	static String duration(long millis) {
		long minutes = Math.max(0, millis) / 60_000L;
		return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + "m";
	}

	private static String kind(BattleRecord record) {
		String type = record.type == null ? "Battle" : record.type.charAt(0) + record.type.substring(1).toLowerCase(Locale.ROOT);
		return (record.naval ? "Naval " + type.toLowerCase(Locale.ROOT) : type) + (record.raid ? " raid" : "");
	}

	private static String reason(String endReason) {
		return switch (endReason == null ? "" : endReason) {
			case "SIDE_WIN" -> "side won";
			case "RETREAT" -> "retreat";
			case "TIMER" -> "time ran out";
			case "WAR_ENDED" -> "the war ended";
			default -> "ended";
		};
	}

	private static String classSuffix(FighterStats fighter) {
		return fighter.className == null ? "" : ", " + fighter.className;
	}

	private static String round(double value) {
		return Long.toString(Math.round(value));
	}

	private static String percent(double part, double total) {
		return (total <= 0 ? 0 : Math.round(part * 100 / total)) + "%";
	}

	private static boolean isPage(String arg) {
		return arg.matches("[0-9]{1,6}");
	}

	private static Component button(String label, String command, String hover) {
		return LegacyComponentSerializer.legacySection().deserialize("§e" + label)
				.clickEvent(ClickEvent.runCommand(command))
				.hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)));
	}
}
