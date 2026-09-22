package me.Plugins.SimpleFactions.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModifierScaleTest {

	@Test
	void equalPrestige_isAtEqual() {
		double value = ModifierScale.relativePrestige(100, 100, -4, 10, 20);
		assertEquals(10.0, value, 1e-9);
	}

	@Test
	void slightlyStronger_staysNearEqual() {
		double value = ModifierScale.relativePrestige(101, 100, -4, 10, 20);
		assertTrue(value > 10.0);
		assertTrue(value < 11.0);
	}

	@Test
	void muchWeaker_nearAtWeaker() {
		double value = ModifierScale.relativePrestige(1, 1000, -4, 10, 20);
		assertTrue(value < -3.0);
	}

	@Test
	void muchStronger_clampsAtStronger() {
		double value = ModifierScale.relativePrestige(10000, 100, -4, 10, 20);
		assertEquals(20.0, value, 1e-9);
	}

	@Test
	void zeroOurPrestige_doesNotExplode() {
		double value = ModifierScale.relativePrestige(50, 0, -4, 10, 20);
		assertEquals(20.0, value, 1e-9);
	}
}
