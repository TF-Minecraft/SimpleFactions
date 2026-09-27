package net.tfminecraft.simplefactions.map.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class MarkerSizeTest {

	private int previousThreshold;

	@BeforeEach
	void saveThreshold() {
		previousThreshold = Cache.settlementLargePopulationThreshold;
	}

	@AfterEach
	void restoreThreshold() {
		Cache.settlementLargePopulationThreshold = previousThreshold;
	}

	@Test
	void largeIconStartsAboveTheThreshold() {
		Cache.settlementLargePopulationThreshold = 8;

		assertEquals("small", Markers.markerSizeForPopulation(8));
		assertEquals("large", Markers.markerSizeForPopulation(9));
	}
}
