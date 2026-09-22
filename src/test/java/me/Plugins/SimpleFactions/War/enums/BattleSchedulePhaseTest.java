package me.Plugins.SimpleFactions.War.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BattleSchedulePhaseTest {

	@Test
	void fromJson_parsesKnownValues() {
		assertEquals("idle", BattleSchedulePhase.IDLE.toJson());
		assertEquals("voting", BattleSchedulePhase.VOTING.toJson());
		assertEquals("scheduled", BattleSchedulePhase.SCHEDULED.toJson());
		assertEquals("autoresolve_pending", BattleSchedulePhase.AUTORESOLVE_PENDING.toJson());
		assertEquals(BattleSchedulePhase.VOTING, BattleSchedulePhase.fromJson("voting"));
		assertEquals(BattleSchedulePhase.SCHEDULED, BattleSchedulePhase.fromJson("SCHEDULED"));
	}

	@Test
	void fromJson_nullOrBlankDefaultsToIdle() {
		assertEquals(BattleSchedulePhase.IDLE, BattleSchedulePhase.fromJson(null));
		assertEquals(BattleSchedulePhase.IDLE, BattleSchedulePhase.fromJson(""));
	}

	@Test
	void fromJson_unknownReturnsNull() {
		assertNull(BattleSchedulePhase.fromJson("not_a_phase"));
	}
}
