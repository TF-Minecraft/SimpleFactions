package net.tfminecraft.simplefactions.map.provinces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class ProsperitySoftCapTest {

	private double previousCap;
	private double previousScale;

	@BeforeEach
	void setUp() {
		previousCap = Cache.prosperitySoftCap;
		previousScale = Cache.prosperitySoftCapScale;
		Cache.prosperitySoftCap = 80;
		Cache.prosperitySoftCapScale = 20;
	}

	@AfterEach
	void tearDown() {
		Cache.prosperitySoftCap = previousCap;
		Cache.prosperitySoftCapScale = previousScale;
	}

	@Test
	void apply_leavesProsperityUpToTheCapAlone() {
		assertEquals(0.0, ProsperitySoftCap.apply(0), 1e-9);
		assertEquals(42.5, ProsperitySoftCap.apply(42.5), 1e-9);
		assertEquals(80.0, ProsperitySoftCap.apply(80), 1e-9);
	}

	@Test
	void apply_slowsPastTheCapWithoutACeiling() {
		assertEquals(120.0, ProsperitySoftCap.apply(140), 1e-9);
		assertEquals(160.0, ProsperitySoftCap.apply(240), 1e-9);
		double far = ProsperitySoftCap.apply(10_000);
		assertTrue(far > 600, "expected continued growth far past the cap, was " + far);
	}

	@Test
	void diminish_isSmoothAtTheCap() {
		double atCap = ProsperitySoftCap.diminish(80.0, 80.0, 20.0);
		double justAbove = ProsperitySoftCap.diminish(80.001, 80.0, 20.0);
		assertEquals(0.001, justAbove - atCap, 1e-6);
	}

	@Test
	void diminish_marginalValueHalvesAtCapPlusThreeScales() {
		double step = 1e-4;
		double at = ProsperitySoftCap.diminish(140.0, 80.0, 20.0);
		double next = ProsperitySoftCap.diminish(140.0 + step, 80.0, 20.0);
		assertEquals(0.5, (next - at) / step, 1e-3);
	}

	@Test
	void diminish_zeroCapOrScaleDisablesIt() {
		assertEquals(500.0, ProsperitySoftCap.diminish(500.0, 0.0, 20.0), 1e-9);
		assertEquals(500.0, ProsperitySoftCap.diminish(500.0, 80.0, 0.0), 1e-9);
	}
}
