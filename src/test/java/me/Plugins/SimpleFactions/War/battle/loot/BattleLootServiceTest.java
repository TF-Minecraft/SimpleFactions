package me.Plugins.SimpleFactions.War.battle.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BattleLootServiceTest {

	@Test
	void shouldPay_whenBattleHadAWinnerAndLootIsOn() {
		assertTrue(BattleLootService.shouldPay(true, false, true));
	}

	@Test
	void shouldNotPay_whenBattleHadNoWinner() {
		assertFalse(BattleLootService.shouldPay(false, false, true));
	}

	@Test
	void shouldNotPay_forCampaignRaids() {
		assertFalse(BattleLootService.shouldPay(true, true, true));
	}

	@Test
	void shouldNotPay_whenTheBattleToggleIsOff() {
		assertFalse(BattleLootService.shouldPay(true, false, false));
	}

	@Test
	void formatCommand_replacesBothPlaceholderStyles() {
		assertEquals(
				"crates key give Ada battle_key 1",
				BattleLootService.formatCommand("crates key give %player% battle_key 1", "Ada"));
		assertEquals(
				"crates key give Ada battle_key 1",
				BattleLootService.formatCommand("crates key give #player# battle_key 1", "Ada"));
	}

	@Test
	void formatCommand_stripsLeadingSlash() {
		assertEquals("give Ada diamond", BattleLootService.formatCommand("/give %player% diamond", "Ada"));
	}

	@Test
	void formatCommand_returnsNullForNothingRunnable() {
		assertNull(BattleLootService.formatCommand(null, "Ada"));
		assertNull(BattleLootService.formatCommand("   ", "Ada"));
		assertNull(BattleLootService.formatCommand("/", "Ada"));
	}
}
