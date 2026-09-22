package me.Plugins.SimpleFactions.Database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FactionDataRankTest {

	@Test
	void roundTrip_rankAndFoundedAt() {
		FactionData data = new FactionData();
		data.id = "rhodesia";
		data.rank = "powerful_faction";
		data.foundedAt = 1725184500L;

		String json = JsonUtil.GSON.toJson(data);
		FactionData parsed = JsonUtil.GSON.fromJson(json, FactionData.class);

		assertEquals("powerful_faction", parsed.rank);
		assertEquals(1725184500L, parsed.foundedAt);
	}

	@Test
	void foundedAt_usesSpacedKey() {
		FactionData data = new FactionData();
		data.foundedAt = 42L;

		String json = JsonUtil.GSON.toJson(data);
		org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"founded at\""));
	}

	/** Pre-chronicle save files have neither key; both must read back as absent. */
	@Test
	void legacyPayload_leavesRankAndFoundedAtNull() {
		FactionData parsed = JsonUtil.GSON.fromJson("{\"id\":\"old\"}", FactionData.class);

		assertEquals("old", parsed.id);
		assertNull(parsed.rank);
		assertNull(parsed.foundedAt);
	}
}
