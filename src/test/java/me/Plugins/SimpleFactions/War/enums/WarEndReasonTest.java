package me.Plugins.SimpleFactions.War.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WarEndReasonTest {

	@Test
	void jsonRoundTrip() {
		for (WarEndReason reason : WarEndReason.values()) {
			assertEquals(reason, WarEndReason.fromJson(reason.toJson()));
		}
		assertEquals("white_peace", WarEndReason.WHITE_PEACE.toJson());
		assertEquals("attacker_victory", WarEndReason.ATTACKER_VICTORY.toJson());
		assertEquals("defender_victory", WarEndReason.DEFENDER_VICTORY.toJson());
		assertEquals("admin_end", WarEndReason.ADMIN_END.toJson());
	}

	@Test
	void fromJson_rejectsRemovedAliases() {
		assertNull(WarEndReason.fromJson("auto_white_peace"));
		assertNull(WarEndReason.fromJson("surrender"));
	}
}
