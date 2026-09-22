package me.Plugins.SimpleFactions.Managers.Inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import me.Plugins.SimpleFactions.enums.SFGUI;

class InventoryUpdaterTest {

	@ParameterizedTest
	@EnumSource(value = SFGUI.class, names = {
			"GUILD_VIEW",
			"GUILD_LIST",
			"FACTION_GUILDS",
			"LAW_PROPOSAL_SELECT",
			"LAW_SELECT",
			"FAVOUR_REPRESS_SELECT",
			"PROPOSALS",
			"CAUSES_VIEW",
			"CAUSE_VIEW",
			"LEDGER_VIEW",
			"COMPANY_VIEW"
	})
	void expensiveScreensSkipPeriodicRefresh(SFGUI type) {
		assertTrue(InventoryUpdater.skipsPeriodicRefresh(type));
	}

	@ParameterizedTest
	@EnumSource(value = SFGUI.class, names = {
			"FACTION_VIEW",
			"FACTION_LIST",
			"UPGRADE_VIEW",
			"WAR_LIST",
			"MOVEMENT_LIST",
			"COMPANY_SLOTS_VIEW",
			"LAW_VIEW",
			"GOVERNMENT_VIEW"
	})
	void liveScreensStayOnPeriodicRefresh(SFGUI type) {
		assertFalse(InventoryUpdater.skipsPeriodicRefresh(type));
	}

	@Test
	void nullTypeDoesNotSkip() {
		assertFalse(InventoryUpdater.skipsPeriodicRefresh(null));
	}
}
