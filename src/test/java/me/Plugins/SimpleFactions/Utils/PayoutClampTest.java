package me.Plugins.SimpleFactions.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PayoutClampTest {

	@Test
	void fullWhenAffordable() {
		assertEquals(1.0, PayoutClamp.scaleFactor(100.0, 20.0), 1e-9);
	}

	@Test
	void proportionalWhenShort() {
		assertEquals(0.25, PayoutClamp.scaleFactor(5.0, 20.0), 1e-9);
	}

	@Test
	void zeroWhenBrokeOrNothingNeeded() {
		assertEquals(0.0, PayoutClamp.scaleFactor(0.0, 20.0), 1e-9);
		assertEquals(0.0, PayoutClamp.scaleFactor(10.0, 0.0), 1e-9);
		assertEquals(0.0, PayoutClamp.scaleFactor(-5.0, 20.0), 1e-9);
	}
}
