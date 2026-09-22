package me.Plugins.SimpleFactions.prestige;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class TradePrestigeTest {

	private double previousPerPoint;
	private double previousCap;
	private double previousFalloff;

	@BeforeEach
	void setUp() {
		previousPerPoint = Cache.prestigePerTradePower;
		previousCap = Cache.prestigeFromTradeSoftCap;
		previousFalloff = Cache.prestigeFromTradeFalloff;
		Cache.prestigePerTradePower = 0.1;
		Cache.prestigeFromTradeSoftCap = 2000;
		Cache.prestigeFromTradeFalloff = 0.1;
	}

	@AfterEach
	void tearDown() {
		Cache.prestigePerTradePower = previousPerPoint;
		Cache.prestigeFromTradeSoftCap = previousCap;
		Cache.prestigeFromTradeFalloff = previousFalloff;
	}

	@Test
	void fromTradePower_isLinearBelowTheCap() {
		assertEquals(0.0, TradePrestige.fromTradePower(0), 1e-9);
		assertEquals(100.0, TradePrestige.fromTradePower(1000), 1e-9);
		assertEquals(2000.0, TradePrestige.fromTradePower(20000), 1e-9);
	}

	@Test
	void fromTradePower_slowsAfterTheCapAndKeepsGrowing() {
		double atCap = TradePrestige.fromTradePower(20000);
		double aBitPast = TradePrestige.fromTradePower(25000);
		double farPast = TradePrestige.fromTradePower(200000);
		assertEquals(2000.0, atCap, 1e-9);
		assertTrue(aBitPast > atCap, "expected growth just past the cap");
		assertTrue(farPast > aBitPast, "expected continued growth far past the cap");
		assertTrue(farPast < 2900.0, "expected to stay near 2k, was " + farPast);
	}

	@Test
	void diminish_isSmoothAtTheCap() {
		double justBelow = TradePrestige.diminish(1999.0, 2000.0, 0.1);
		double atCap = TradePrestige.diminish(2000.0, 2000.0, 0.1);
		double justAbove = TradePrestige.diminish(2001.0, 2000.0, 0.1);
		assertEquals(1999.0, justBelow, 1e-9);
		assertEquals(2000.0, atCap, 1e-9);
		assertTrue(justAbove > atCap && justAbove < 2001.0);
		assertEquals(1.0, justAbove - atCap, 0.01);
	}

	@Test
	void diminish_hardCapsWhenFalloffIsZero() {
		assertEquals(2000.0, TradePrestige.diminish(50000.0, 2000.0, 0.0), 1e-9);
	}

	@Test
	void diminish_staysLinearWhenFalloffIsDisabled() {
		assertEquals(8000.0, TradePrestige.diminish(8000.0, 2000.0, 1.0), 1e-9);
	}

	@Test
	void fromTradePower_disabledRateIsZero() {
		Cache.prestigePerTradePower = 0;
		assertEquals(0.0, TradePrestige.fromTradePower(50000), 1e-9);
	}
}
