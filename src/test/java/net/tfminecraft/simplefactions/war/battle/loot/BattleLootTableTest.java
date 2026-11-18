package net.tfminecraft.simplefactions.war.battle.loot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BattleLootTableTest {

	/** Returns scripted doubles and ints in order. */
	private static final class ScriptedRandom extends Random {
		private final Deque<Double> doubles = new ArrayDeque<>();
		private final Deque<Integer> ints = new ArrayDeque<>();

		ScriptedRandom withDoubles(double... values) {
			for (double v : values) {
				doubles.add(v);
			}
			return this;
		}

		ScriptedRandom withInts(int... values) {
			for (int v : values) {
				ints.add(v);
			}
			return this;
		}

		@Override
		public double nextDouble() {
			return doubles.removeFirst();
		}

		@Override
		public int nextInt(int bound) {
			int value = ints.removeFirst();
			if (value < 0 || value >= bound) {
				throw new IllegalStateException("scripted int " + value + " outside bound " + bound);
			}
			return value;
		}
	}

	private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
		YamlConfiguration config = new YamlConfiguration();
		config.loadFromString(text);
		return config;
	}

	@Test
	void missingSectionGivesAnEmptyTableWithDefaults() {
		BattleLootTable table = BattleLootTable.load(null);
		assertTrue(table.isEmpty());
		assertEquals("act_1", table.getActiveTable());
		assertEquals(10, table.getMinutesPerRoll());
		assertEquals(12, table.getMaxRolls());
		assertTrue(table.getCoins().isEmpty());
	}

	@Test
	void loadsTheActiveTableAndSortsCoinsLargestFirst() throws Exception {
		YamlConfiguration config = yaml("""
				loot:
				  minutes_per_roll: 15
				  max_rolls: 8
				  active_table: act_2
				  coins:
				    - {item: m.currency.gold_coin, value: 1}
				    - {item: m.currency.pouch_of_coins, value: 100}
				    - {item: m.currency.stack_of_coins, value: 10}
				    - {item: "  ", value: 5}
				    - {item: m.currency.zero, value: 0}
				    - {item: m.currency.bad, value: lots}
				  tables:
				    act_1:
				      - {item: m.materials.ignitium, amount: 3}
				    act_2:
				      - {item: m.materials.mythrilite, amount: 2-4, percent: 25}
				      - {amount: 3}
				      - {item: denars, amount: "13"}
				""");
		BattleLootTable table = BattleLootTable.load(config.getConfigurationSection("loot"));
		assertEquals("act_2", table.getActiveTable());
		assertEquals(15, table.getMinutesPerRoll());
		assertEquals(8, table.getMaxRolls());
		assertEquals(List.of(
				new BattleLootTable.Coin("m.currency.pouch_of_coins", 100),
				new BattleLootTable.Coin("m.currency.stack_of_coins", 10),
				new BattleLootTable.Coin("m.currency.gold_coin", 1)), table.getCoins());
		assertEquals(List.of(
				new BattleLootTable.Entry("m.materials.mythrilite", 0.25, 2, 4),
				new BattleLootTable.Entry("denars", 1.0, 13, 13)), table.getEntries());
	}

	@Test
	void blankActiveTableAndBadRollSettingsFallBack() throws Exception {
		YamlConfiguration config = yaml("""
				loot:
				  active_table: " "
				  minutes_per_roll: 0
				  max_rolls: -3
				  tables:
				    act_1:
				      - {item: v.iron_ingot}
				""");
		BattleLootTable table = BattleLootTable.load(config.getConfigurationSection("loot"));
		assertEquals("act_1", table.getActiveTable());
		assertEquals(1, table.getMinutesPerRoll());
		assertEquals(1, table.getMaxRolls());
		assertEquals(List.of(new BattleLootTable.Entry("v.iron_ingot", 1.0, 1, 1)), table.getEntries());
	}

	@Test
	void rollsFollowTheTimeFoughtRoundedAndCapped() {
		BattleLootTable table = new BattleLootTable("act_1", List.of(), List.of(), 10, 12);
		assertEquals(1, table.rollsFor(null));
		assertEquals(1, table.rollsFor(0L));
		assertEquals(1, table.rollsFor(-60L));
		assertEquals(1, table.rollsFor(120L));
		assertEquals(1, table.rollsFor(899L));
		assertEquals(2, table.rollsFor(900L));
		assertEquals(2, table.rollsFor(20 * 60L));
		assertEquals(6, table.rollsFor(60 * 60L));
		assertEquals(12, table.rollsFor(120 * 60L));
		assertEquals(12, table.rollsFor(10 * 3600L));
	}

	@Test
	void rollMergesItemsAcrossRollsAndSkipsMissedChances() {
		BattleLootTable table = new BattleLootTable("act_1", List.of(
				new BattleLootTable.Entry("m.materials.ignitium", 1.0, 3, 3),
				new BattleLootTable.Entry("m.materials.ignitium", 0.15, 1, 1),
				new BattleLootTable.Entry("m.materials.raw_tin", 0.08, 1, 1),
				new BattleLootTable.Entry("v.iron_ingot", 1.0, 2, 4),
				new BattleLootTable.Entry("m.materials.nothing", 1.0, 0, 0)), List.of(), 10, 12);
		// roll 1: ignitium bonus hits (0.1 < 0.15), tin misses (0.5), iron 2 + 1
		// roll 2: bonus misses (0.15 is not below 0.15), tin hits (0.07), iron 2 + 2
		ScriptedRandom random = new ScriptedRandom().withDoubles(0.1, 0.5, 0.15, 0.07).withInts(1, 2);
		BattleLootTable.Payout payout = table.roll(random, 2);
		assertEquals(Map.of("m.materials.ignitium", 7, "v.iron_ingot", 7, "m.materials.raw_tin", 1),
				payout.goods());
		assertEquals(List.of("m.materials.ignitium", "v.iron_ingot", "m.materials.raw_tin"),
				List.copyOf(payout.goods().keySet()));
		assertTrue(payout.coins().isEmpty());
		assertEquals(0, payout.denars());
	}

	@Test
	void denarsArePaidInTheLargestCoinsFirst() {
		BattleLootTable table = new BattleLootTable("act_1",
				List.of(new BattleLootTable.Entry("DENARS", 1.0, 13, 13)),
				List.of(new BattleLootTable.Coin("gold", 1), new BattleLootTable.Coin("stack", 10),
						new BattleLootTable.Coin("pouch", 100), new BattleLootTable.Coin("handful", 5)),
				10, 12);
		BattleLootTable.Payout payout = table.roll(new Random(1), 12);
		assertEquals(Map.of("pouch", 1, "stack", 5, "handful", 1, "gold", 1), payout.coins());
		assertEquals(156, payout.denars());
		assertTrue(payout.goods().isEmpty());
	}

	@Test
	void denarsBelowTheSmallestCoinAreNotPaid() {
		BattleLootTable table = new BattleLootTable("act_1",
				List.of(new BattleLootTable.Entry("denars", 1.0, 13, 13)),
				List.of(new BattleLootTable.Coin("handful", 5)), 10, 12);
		BattleLootTable.Payout payout = table.roll(new Random(1), 1);
		assertEquals(Map.of("handful", 2), payout.coins());
		assertEquals(10, payout.denars());
	}

	@Test
	void parsingHelpersTolerateBadValues() {
		assertNull(BattleLootTable.string(null));
		assertNull(BattleLootTable.string("   "));
		assertEquals("x", BattleLootTable.string(" x "));
		assertEquals(7, BattleLootTable.integer(7.9, 0));
		assertEquals(4, BattleLootTable.integer(" 4 ", 0));
		assertEquals(9, BattleLootTable.integer(null, 9));
		assertEquals(9, BattleLootTable.integer("many", 9));
		assertEquals(0.08, BattleLootTable.percent(8), 1e-9);
		assertEquals(0.155, BattleLootTable.percent("15.5"), 1e-9);
		assertEquals(1.0, BattleLootTable.percent(null), 1e-9);
		assertEquals(1.0, BattleLootTable.percent("often"), 1e-9);
		assertEquals(1.0, BattleLootTable.percent(250), 1e-9);
		assertEquals(0.0, BattleLootTable.percent(-5), 1e-9);
		assertArrayEquals(new int[] { 3, 3 }, BattleLootTable.range(3));
		assertArrayEquals(new int[] { 0, 0 }, BattleLootTable.range(-2));
		assertArrayEquals(new int[] { 1, 1 }, BattleLootTable.range(null));
		assertArrayEquals(new int[] { 5, 5 }, BattleLootTable.range("5"));
		assertArrayEquals(new int[] { 2, 4 }, BattleLootTable.range("4 - 2"));
		assertArrayEquals(new int[] { 1, 1 }, BattleLootTable.range("a-b"));
		assertArrayEquals(new int[] { 0, 0 }, BattleLootTable.range("-3"));
	}
}
