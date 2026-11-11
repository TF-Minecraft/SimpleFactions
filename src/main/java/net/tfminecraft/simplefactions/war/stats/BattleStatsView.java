package net.tfminecraft.simplefactions.war.stats;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.war.battle.ui.BattlePermissions;

/**
 * The {@code /battle stats} command, the one-line notice when a battle ends, the staff
 * fighter table in chat, and the formatting the menu shares.
 */
public final class BattleStatsView {
	static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("d MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

	private BattleStatsView() {
	}

	/** {@code /battle stats [page | <id> [table] | war [<war>]]} opens the stats menu. */
	public static void command(Player player, String[] args) {
		boolean staff = BattlePermissions.isAdmin(player);
		if (Members.key(player) == null && !staff) {
			player.sendMessage("§cChoose a character to see your battle stats.");
			return;
		}
		if (args.length >= 2 && args[1].equalsIgnoreCase("war")) {
			if (args.length >= 3) {
				BattleStatsMenu.war(player, args[2]);
			} else {
				BattleStatsMenu.wars(player, 1);
			}
			return;
		}
		if (args.length >= 2 && !args[1].matches("[0-9]{1,6}")) {
			BattleRecord record = BattleStatsArchive.get(args[1].toLowerCase(Locale.ROOT));
			if (record == null || (!staff && record.fighter(Members.key(player)) == null)) {
				player.sendMessage("§cNo battle stats with that ID that you fought in.");
			} else if (staff && args.length >= 3 && args[2].equalsIgnoreCase("table")) {
				fighterTable(player, record);
			} else {
				BattleStatsMenu.battle(player, record, 1);
			}
			return;
		}
		BattleStatsMenu.home(player, args.length >= 2 ? Integer.parseInt(args[1]) : 1);
	}

	/** One clickable line to every online fighter of a just-finished battle. */
	static void announce(BattleRecord record) {
		for (FighterStats fighter : record.fighters) {
			Player player = Members.player(fighter.key);
			if (player != null && player.isOnline()) {
				player.sendMessage(LegacyComponentSerializer.legacySection()
						.deserialize("§6⚔ §e" + record.name + " §6is over. §7Click here or use §e/battle stats §7to see how you did.")
						.clickEvent(ClickEvent.runCommand("/battle stats " + record.id))
						.hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection()
								.deserialize("§7Open your stats for this battle"))));
			}
		}
	}

	/** Staff only: every fighter's line and the class shares, in chat for copying. */
	static void fighterTable(Player player, BattleRecord record) {
		List<FighterStats> ranked = new ArrayList<>(record.fighters);
		ranked.sort(Comparator.comparingInt((FighterStats f) -> f.kills).thenComparingDouble(f -> f.damage).reversed());
		player.sendMessage("§6" + record.name + " §7— kills/assists/deaths/team kills, damage, healing, CC s, friendly dmg/CC, taken, blocked, logouts:");
		for (FighterStats f : ranked) {
			player.sendMessage(" §f" + f.name + " §7" + f.side + classSuffix(f) + " §e" + f.kills + "/" + f.assists + "/" + f.deaths
					+ "/" + f.teamKills + " §f" + round(f.damage) + " §a" + round(f.healing) + " §b" + round(f.cc)
					+ " §7ff " + round(f.friendlyDamage) + "/" + round(f.friendlyCc) + " taken " + round(f.damageTaken)
					+ (f.blockedHits > 0 ? " §cblocked " + f.blockedHits : "") + (f.logouts > 0 ? " §7logouts " + f.logouts : ""));
		}
		for (String line : classShares(record)) {
			player.sendMessage(line);
		}
	}

	/** Per class: fighters, share of damage, share of healing, kills. */
	static List<String> classShares(BattleRecord record) {
		double damage = 0;
		double healing = 0;
		int kills = 0;
		Map<String, double[]> byClass = new LinkedHashMap<>();
		for (FighterStats f : record.fighters) {
			double[] sums = byClass.computeIfAbsent(f.className == null ? "No class" : f.className, c -> new double[4]);
			sums[0]++;
			sums[1] += f.damage;
			sums[2] += f.healing;
			sums[3] += f.kills;
			damage += f.damage;
			healing += f.healing;
			kills += f.kills;
		}
		List<String> lines = new ArrayList<>();
		lines.add("§6Classes §7(fighters, damage share, healing share, kills):");
		for (Map.Entry<String, double[]> entry : byClass.entrySet()) {
			double[] sums = entry.getValue();
			lines.add(" §f" + entry.getKey() + " §7" + (int) sums[0] + " §e" + percent(sums[1], damage) + " §a"
					+ percent(sums[2], healing) + " §7" + (int) sums[3] + "/" + kills);
		}
		return lines;
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

	static String kind(BattleRecord record) {
		String type = record.type == null ? "Battle" : record.type.charAt(0) + record.type.substring(1).toLowerCase(Locale.ROOT);
		return (record.naval ? "Naval " + type.toLowerCase(Locale.ROOT) : type) + (record.raid ? " raid" : "");
	}

	static String reason(String endReason) {
		return switch (endReason == null ? "" : endReason) {
			case "SIDE_WIN" -> "side won";
			case "RETREAT" -> "retreat";
			case "TIMER" -> "time ran out";
			case "CONTROL" -> "control of the field";
			case "WAR_ENDED" -> "the war ended";
			case "RAID_CLEARED" -> "the raid was called off";
			default -> "ended";
		};
	}

	static String classSuffix(FighterStats fighter) {
		return fighter.className == null ? "" : ", " + fighter.className;
	}

	static String round(double value) {
		return Long.toString(Math.round(value));
	}

	static String percent(double part, double total) {
		return (total <= 0 ? 0 : Math.round(part * 100 / total)) + "%";
	}
}
