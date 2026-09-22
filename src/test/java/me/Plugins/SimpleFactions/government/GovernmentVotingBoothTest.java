package me.Plugins.SimpleFactions.government;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class GovernmentVotingBoothTest {

	@Test
	void formatAndParse_roundTripTwoBooths() {
		String first = Government.formatVotingBooth("world", 10, 64, -3);
		String second = Government.formatVotingBooth("nether", 0, 80, 1);
		assertEquals("world,10,64,-3", first);
		assertEquals("nether,0,80,1", second);

		assertArrayEquals(new String[] {"world", "10", "64", "-3"}, Government.parseVotingBoothParts(first));
		assertArrayEquals(new String[] {"nether", "0", "80", "1"}, Government.parseVotingBoothParts(second));

		List<String> stored = List.of(first, second);
		assertEquals(2, stored.size());
		assertEquals(first, stored.get(0));
		assertEquals(second, stored.get(1));
	}

	@Test
	void parseVotingBoothParts_skipsMalformed() {
		assertNull(Government.parseVotingBoothParts(null));
		assertNull(Government.parseVotingBoothParts(""));
		assertNull(Government.parseVotingBoothParts("world,1,2"));
		assertNull(Government.parseVotingBoothParts(",1,2,3"));
		assertNull(Government.parseVotingBoothParts("world,a,2,3"));
		assertNull(Government.formatVotingBooth(null, 1, 2, 3));
		assertNull(Government.formatVotingBooth(" ", 1, 2, 3));
	}
}
