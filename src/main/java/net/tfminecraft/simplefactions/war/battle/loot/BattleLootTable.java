package net.tfminecraft.simplefactions.war.battle.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The war.battle_loot drop table used in TABLE mode. Each fighter rolls the active table once per
 * minutes_per_roll the battle lasted (rounded to the nearest roll, at least one, at most max_rolls),
 * so a battle pays by the time fought. Entries use the Infestations rewards.yml format: item, amount
 * (a number or a range like 2-4) and percent (0-100, default 100). The item {@code denars} is paid
 * as coins from the coins list, largest first.
 */
public final class BattleLootTable {

	public static final String DENARS = "denars";
	static final int DEFAULT_MINUTES_PER_ROLL = 10;
	static final int DEFAULT_MAX_ROLLS = 12;
	static final String DEFAULT_ACTIVE_TABLE = "act_1";
	/** Largest amount one entry can give per roll, so a typo cannot flood a fighter or overflow totals. */
	static final int MAX_AMOUNT = 10_000;

	public record Entry(String item, double chance, int min, int max) {
	}

	public record Coin(String item, int value) {
	}

	/** Rolled items in table order (goods), the coin items paying the denars, and those denars. */
	public record Payout(Map<String, Integer> goods, Map<String, Integer> coins, int denars) {
	}

	private final String activeTable;
	private final List<Entry> entries;
	private final List<Coin> coins;
	private final int minutesPerRoll;
	private final int maxRolls;

	public BattleLootTable(String activeTable, List<Entry> entries, List<Coin> coins, int minutesPerRoll,
			int maxRolls) {
		this.activeTable = activeTable;
		this.entries = List.copyOf(entries);
		List<Coin> sorted = new ArrayList<>(coins);
		sorted.sort(Comparator.comparingInt(Coin::value).reversed());
		this.coins = List.copyOf(sorted);
		this.minutesPerRoll = Math.max(1, minutesPerRoll);
		this.maxRolls = Math.max(1, maxRolls);
	}

	public static BattleLootTable empty() {
		return new BattleLootTable(DEFAULT_ACTIVE_TABLE, List.of(), List.of(), DEFAULT_MINUTES_PER_ROLL,
				DEFAULT_MAX_ROLLS);
	}

	/** Reads the war.battle_loot section; a missing section gives an empty table. */
	public static BattleLootTable load(ConfigurationSection section) {
		if (section == null) {
			return empty();
		}
		String active = string(section.getString("active_table"));
		if (active == null) {
			active = DEFAULT_ACTIVE_TABLE;
		}
		List<Coin> coinList = new ArrayList<>();
		for (Map<?, ?> row : section.getMapList("coins")) {
			String item = string(row.get("item"));
			int value = integer(row.get("value"), 0);
			if (item != null && value > 0) {
				coinList.add(new Coin(item, value));
			}
		}
		List<Entry> entryList = new ArrayList<>();
		for (Map<?, ?> row : section.getMapList("tables." + active)) {
			String item = string(row.get("item"));
			if (item == null) {
				continue;
			}
			int[] range = range(row.get("amount"));
			entryList.add(new Entry(item, percent(row.get("percent")), range[0], range[1]));
		}
		return new BattleLootTable(active, entryList, coinList,
				section.getInt("minutes_per_roll", DEFAULT_MINUTES_PER_ROLL),
				section.getInt("max_rolls", DEFAULT_MAX_ROLLS));
	}

	public String getActiveTable() {
		return activeTable;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public List<Coin> getCoins() {
		return coins;
	}

	public int getMinutesPerRoll() {
		return minutesPerRoll;
	}

	public int getMaxRolls() {
		return maxRolls;
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * Rolls for a battle that lasted {@code foughtSeconds}: one per minutes_per_roll, rounded to the
	 * nearest, at least one and at most max_rolls. An unknown length pays one roll.
	 */
	public int rollsFor(Long foughtSeconds) {
		if (foughtSeconds == null || foughtSeconds <= 0) {
			return 1;
		}
		long perRoll = minutesPerRoll * 60L;
		long rolls = (foughtSeconds + perRoll / 2) / perRoll;
		return (int) Math.max(1, Math.min(maxRolls, rolls));
	}

	/** Rolls the table {@code rolls} times and pays the denars in coins, largest first. */
	public Payout roll(Random random, int rolls) {
		Map<String, Integer> goods = new LinkedHashMap<>();
		long denars = 0;
		for (int i = 0; i < rolls; i++) {
			for (Entry entry : entries) {
				if (entry.chance() < 1 && random.nextDouble() >= entry.chance()) {
					continue;
				}
				int amount = entry.min() == entry.max() ? entry.min()
						: entry.min() + random.nextInt(entry.max() - entry.min() + 1);
				if (amount <= 0) {
					continue;
				}
				if (DENARS.equalsIgnoreCase(entry.item())) {
					denars += amount;
				} else {
					goods.merge(entry.item(), amount, Integer::sum);
				}
			}
		}
		Map<String, Integer> coinItems = new LinkedHashMap<>();
		long left = denars;
		for (Coin coin : coins) {
			long count = left / coin.value();
			if (count > 0) {
				coinItems.merge(coin.item(), (int) count, Integer::sum);
				left -= count * coin.value();
			}
		}
		return new Payout(goods, coinItems, (int) (denars - left));
	}

	static String string(Object raw) {
		if (raw == null) {
			return null;
		}
		String value = raw.toString().trim();
		return value.isEmpty() ? null : value;
	}

	static int integer(Object raw, int fallback) {
		if (raw instanceof Number number) {
			return number.intValue();
		}
		try {
			return raw == null ? fallback : Integer.parseInt(raw.toString().trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/** Reads a 0-100 percentage (default 100) and returns a 0-1 chance. */
	static double percent(Object raw) {
		double value = 100;
		if (raw instanceof Number number) {
			value = number.doubleValue();
		} else if (raw != null) {
			try {
				value = Double.parseDouble(raw.toString().trim());
			} catch (NumberFormatException ex) {
				value = 100;
			}
		}
		return Math.clamp(value / 100.0, 0.0, 1.0);
	}

	/** Reads {@code 3}, {@code "3"} or {@code "2-5"}, each bound kept within 0 and MAX_AMOUNT. Missing or bad values give 1. */
	static int[] range(Object raw) {
		int[] parsed = parseRange(raw);
		return new int[] { Math.min(MAX_AMOUNT, parsed[0]), Math.min(MAX_AMOUNT, parsed[1]) };
	}

	private static int[] parseRange(Object raw) {
		if (raw instanceof Number number) {
			int n = Math.max(0, number.intValue());
			return new int[] { n, n };
		}
		String text = string(raw);
		if (text == null) {
			return new int[] { 1, 1 };
		}
		int dash = text.indexOf('-', 1);
		try {
			if (dash < 0) {
				int n = Math.max(0, Integer.parseInt(text));
				return new int[] { n, n };
			}
			int a = Math.max(0, Integer.parseInt(text.substring(0, dash).trim()));
			int b = Math.max(0, Integer.parseInt(text.substring(dash + 1).trim()));
			return new int[] { Math.min(a, b), Math.max(a, b) };
		} catch (NumberFormatException ex) {
			return new int[] { 1, 1 };
		}
	}
}
