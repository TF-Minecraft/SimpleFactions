package me.Plugins.SimpleFactions.government;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StabilityModifierTest {

	@Test
	void tick_negativeModifierMovesTowardZeroAndIsNotRemovedYet() {
		StabilityModifier modifier = new StabilityModifier("Forced Market Open", -25, 1);
		assertFalse(modifier.tick());
		assertEquals(-24, modifier.getModifier());
	}

	@Test
	void tick_positiveModifierMovesTowardZero() {
		StabilityModifier modifier = new StabilityModifier("Bonus", 2, 1);
		assertFalse(modifier.tick());
		assertEquals(1, modifier.getModifier());
		assertTrue(modifier.tick());
	}
}
