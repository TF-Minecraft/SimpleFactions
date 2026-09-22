package me.Plugins.SimpleFactions.War.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarGoalTypeTest {

	@Test
	void fromJson_mapsV2Goals() {
		assertEquals(WarGoalType.DE_JURE_ANNEX, WarGoalType.fromJson("de_jure_annex"));
		assertEquals(WarGoalType.SUBJUGATE, WarGoalType.fromJson("subjugate"));
		assertEquals(WarGoalType.TRANSFER_SUBJECT, WarGoalType.fromJson("transfer_subject"));
		assertEquals(WarGoalType.WAR, WarGoalType.fromJson("war"));
		assertEquals(WarGoalType.TRIBUTARY, WarGoalType.fromJson("tributary"));
		assertEquals(WarGoalType.USURP, WarGoalType.fromJson("usurp"));
		assertEquals(WarGoalType.OPEN_MARKET, WarGoalType.fromJson("open_market"));
		assertEquals(WarGoalType.CHANGE_GOVERNMENT, WarGoalType.fromJson("change_government"));
		assertEquals(WarGoalType.PILLAGE, WarGoalType.fromJson("pillage"));
		assertEquals(WarGoalType.OVERTHROW, WarGoalType.fromJson("overthrow"));
		assertEquals(WarGoalType.CHANGE_LAW, WarGoalType.fromJson("change_law"));
		assertEquals(WarGoalType.CHANGE_TAX, WarGoalType.fromJson("change_tax"));
		assertEquals(WarGoalType.FORCE_PEACE, WarGoalType.fromJson("force_peace"));
	}

	@Test
	void isMovementOrigin_overthrowLawTaxAndForcePeace() {
		assertTrue(WarGoalType.OVERTHROW.isMovementOrigin());
		assertTrue(WarGoalType.CHANGE_LAW.isMovementOrigin());
		assertTrue(WarGoalType.CHANGE_TAX.isMovementOrigin());
		assertTrue(WarGoalType.FORCE_PEACE.isMovementOrigin());
		assertFalse(WarGoalType.CHANGE_GOVERNMENT.isMovementOrigin());
		assertFalse(WarGoalType.PILLAGE.isMovementOrigin());
	}

	@Test
	void toJson_roundTrips() {
		for (WarGoalType type : WarGoalType.values()) {
			assertEquals(type, WarGoalType.fromJson(type.toJson()));
		}
	}

	@Test
	void fromJson_nullSafe() {
		assertNull(WarGoalType.fromJson(null));
		assertNull(WarGoalType.fromJson(""));
		assertNull(WarGoalType.fromJson("unknown"));
	}
}
