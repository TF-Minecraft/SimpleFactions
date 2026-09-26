package net.tfminecraft.simplefactions.guild.income;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class TradeUpkeepTest {

	private double previousMax;

	@BeforeEach
	void setUp() {
		previousMax = Cache.maxTradeUpkeep;
		Cache.maxTradeUpkeep = 0.75;
	}

	@AfterEach
	void tearDown() {
		Cache.maxTradeUpkeep = previousMax;
	}

	@Test
	void rate_isTradeFactorTimesUpkeepBelowTheLimit() {
		assertEquals(0.0, TradeUpkeep.rate(0.9, 0.0), 1e-9);
		assertEquals(0.36, TradeUpkeep.rate(0.8, 0.45), 1e-9);
	}

	@Test
	void rate_neverExceedsTheLimit() {
		assertEquals(0.75, TradeUpkeep.rate(0.9, 1.2), 1e-9);
		assertEquals(0.75, TradeUpkeep.rate(1.0, 5.0), 1e-9);
	}

	@Test
	void rate_neverGoesNegative() {
		assertEquals(0.0, TradeUpkeep.rate(0.9, -0.3), 1e-9);
	}

	@Test
	void rate_zeroLimitDisablesIt() {
		assertEquals(1.8, TradeUpkeep.rate(0.9, 2.0, 0.0), 1e-9);
	}

	@Test
	void sanitizeMax_negativeFallsBackToTheDefault() {
		assertEquals(TradeUpkeep.DEFAULT_MAX, TradeUpkeep.sanitizeMax(-0.1), 1e-9);
		assertEquals(0.0, TradeUpkeep.sanitizeMax(0.0), 1e-9);
		assertEquals(0.6, TradeUpkeep.sanitizeMax(0.6), 1e-9);
	}
}
