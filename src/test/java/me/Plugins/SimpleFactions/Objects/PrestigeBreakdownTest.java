package me.Plugins.SimpleFactions.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PrestigeBreakdownTest {

	private static Modifier find(List<Modifier> modifiers, String type) {
		for (Modifier m : modifiers) {
			if (m.getType().equalsIgnoreCase(type)) return m;
		}
		return null;
	}

	private static long countBonusLines(List<Modifier> modifiers) {
		return modifiers.stream().filter(m -> m.getType().endsWith("% Bonus")).count();
	}

	/**
	 * The reported "weird prestige" bug: the old code summed a list that already held the
	 * previous bonus, so repeated recomputes crept toward pct/(1-pct).
	 */
	@Test
	void build_isIdempotent() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 300, 210, 0, 900, 1000, 40, 10);
		double first = PrestigeBreakdown.total(modifiers);
		for (int i = 0; i < 10; i++) {
			modifiers = PrestigeBreakdown.build(modifiers, 300, 210, 0, 900, 1000, 40, 10);
			assertEquals(first, PrestigeBreakdown.total(modifiers), 1e-9);
		}
	}

	@Test
	void build_bonusDoesNotCompound() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 0, 10);
		double bonus = find(modifiers, "10.0% Bonus").getAmount();
		// Members 100 + Wealth 0 = 100 base, so exactly 10 rather than 11.1
		assertEquals(10.0, bonus, 1e-9);

		modifiers = PrestigeBreakdown.build(modifiers, 100, 0, 0, 0, 0, 0, 10);
		assertEquals(10.0, find(modifiers, "10.0% Bonus").getAmount(), 1e-9);
	}

	@Test
	void build_bonusIncludesSubjects() {
		List<Modifier> without = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 0, 10);
		List<Modifier> with = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 100, 10);
		assertEquals(10.0, find(without, "10.0% Bonus").getAmount(), 1e-9);
		assertEquals(20.0, find(with, "10.0% Bonus").getAmount(), 1e-9);
	}

	@Test
	void build_bonusIncludesTrade() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 100, 0, 50, 0, 0, 0, 10);
		assertEquals(50.0, find(modifiers, "Trade").getAmount(), 1e-9);
		assertEquals(15.0, find(modifiers, "10.0% Bonus").getAmount(), 1e-9);
		assertEquals(165.0, PrestigeBreakdown.total(modifiers), 1e-9);
	}

	@Test
	void build_bonusPercentChange_singleLine() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 0, 10);
		modifiers = PrestigeBreakdown.build(modifiers, 100, 0, 0, 0, 0, 0, 20);
		assertEquals(1, countBonusLines(modifiers));
		assertNull(find(modifiers, "10.0% Bonus"));
		assertEquals(20.0, find(modifiers, "20.0% Bonus").getAmount(), 1e-9);
	}

	@Test
	void build_bonusRemoved_noLine() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 0, 10);
		modifiers = PrestigeBreakdown.build(modifiers, 100, 0, 0, 0, 0, 0, 0);
		assertEquals(0, countBonusLines(modifiers));
		assertEquals(100.0, PrestigeBreakdown.total(modifiers), 1e-9);
	}

	@Test
	void build_retainsPersistentAndCountsItInBonus() {
		List<Modifier> persistent = new ArrayList<>();
		persistent.add(new Modifier("debug", 50.0, true));

		List<Modifier> modifiers = PrestigeBreakdown.build(persistent, 100, 0, 0, 0, 0, 0, 10);
		assertEquals(50.0, find(modifiers, "debug").getAmount(), 1e-9);
		assertEquals(15.0, find(modifiers, "10.0% Bonus").getAmount(), 1e-9);
		assertEquals(165.0, PrestigeBreakdown.total(modifiers), 1e-9);
	}

	@Test
	void build_dropsNonPersistentInput() {
		List<Modifier> stale = new ArrayList<>();
		stale.add(new Modifier("Provinces", 9999.0, false));
		stale.add(new Modifier("Trade", 9999.0, false));

		List<Modifier> modifiers = PrestigeBreakdown.build(stale, 100, 0, 0, 0, 0, 0, 0);
		assertEquals(100.0, PrestigeBreakdown.total(modifiers), 1e-9);
	}

	@Test
	void build_zeroSuppression() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 0, 0);
		assertNull(find(modifiers, "Trade"));
		assertNull(find(modifiers, "Provinces"));
		assertNull(find(modifiers, "Titles"));
		assertNull(find(modifiers, "Subjects"));
		// Wealth keeps an explicit zero row; the prestige GUI relies on it.
		assertEquals(0.0, find(modifiers, "Wealth").getAmount(), 1e-9);
		assertEquals(100.0, find(modifiers, "Members").getAmount(), 1e-9);
	}

	@Test
	void build_ordersSubjectsBeforeBonus() {
		List<Modifier> modifiers = PrestigeBreakdown.build(List.of(), 100, 0, 0, 0, 0, 40, 10);
		int subjects = -1;
		int bonus = -1;
		for (int i = 0; i < modifiers.size(); i++) {
			if (modifiers.get(i).getType().equals("Subjects")) subjects = i;
			if (modifiers.get(i).getType().endsWith("% Bonus")) bonus = i;
		}
		assertTrue(subjects >= 0 && bonus > subjects);
	}

	@Test
	void total_handlesNullAndEmpty() {
		assertEquals(0.0, PrestigeBreakdown.total(null), 1e-9);
		assertEquals(0.0, PrestigeBreakdown.total(List.of()), 1e-9);
	}
}
