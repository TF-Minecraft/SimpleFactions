package me.Plugins.SimpleFactions.Managers.Inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueueCancelPayloadTest {

	@Test
	void militaryPayloadRoundTrip() {
		String payload = QueueCancelPayload.military("faction_a", 2);
		var parsed = QueueCancelPayload.parse(payload);
		assertTrue(parsed.isPresent());
		assertEquals(QueueCancelPayload.Type.MILITARY, parsed.get().type());
		assertEquals("faction_a", parsed.get().ownerId());
		assertEquals(2, parsed.get().index());
	}

	@Test
	void guildUpgradePayloadRoundTrip() {
		String payload = QueueCancelPayload.guildUpgrade("guild_1", 1);
		var parsed = QueueCancelPayload.parse(payload);
		assertTrue(parsed.isPresent());
		assertEquals(QueueCancelPayload.Type.GUILD_UPGRADE, parsed.get().type());
		assertEquals("guild_1", parsed.get().ownerId());
		assertEquals(1, parsed.get().index());
	}

	@Test
	void installationPayloadRoundTrip() {
		String payload = QueueCancelPayload.installation("faction_a", "fort_1");
		var parsed = QueueCancelPayload.parse(payload);
		assertTrue(parsed.isPresent());
		assertEquals(QueueCancelPayload.Type.INSTALLATION, parsed.get().type());
		assertEquals("faction_a", parsed.get().ownerId());
		assertEquals("fort_1", parsed.get().detail());
	}

	@Test
	void invalidPayloadRejected() {
		assertFalse(QueueCancelPayload.parse("not-a-payload").isPresent());
		assertFalse(QueueCancelPayload.parse("military:only-two").isPresent());
		assertFalse(QueueCancelPayload.parse("military:faction:not-a-number").isPresent());
	}
}
