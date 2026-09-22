package me.Plugins.SimpleFactions.War.battle.engine.rules;


import me.Plugins.SimpleFactions.War.battle.engine.rules.BattleItemDurability;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BattleItemDurabilityTest {

	@Test
	void subOneScaledDamageUsesProbability() {
		assertEquals(1, BattleItemDurability.apply(1, 0.2, () -> 0.0));
		assertEquals(0, BattleItemDurability.apply(1, 0.2, () -> 0.2));
		assertEquals(0, BattleItemDurability.apply(1, 0.2, () -> 0.9));
	}

	@Test
	void largerDamageIsRoundedToTwentyPercent() {
		assertEquals(2, BattleItemDurability.apply(10, 0.2, () -> 0.0));
	}

	@Test
	void zeroMultiplierCancelsWear() {
		assertEquals(0, BattleItemDurability.apply(5, 0.0, () -> 0.0));
	}

	@Test
	void fullMultiplierKeepsDamage() {
		assertEquals(4, BattleItemDurability.apply(4, 1.0, () -> 0.0));
	}

	@Test
	void clampsMultiplierToUnitRange() {
		assertEquals(0, BattleItemDurability.apply(3, -1.0, () -> 0.0));
		assertEquals(3, BattleItemDurability.apply(3, 2.0, () -> 0.0));
	}
}
