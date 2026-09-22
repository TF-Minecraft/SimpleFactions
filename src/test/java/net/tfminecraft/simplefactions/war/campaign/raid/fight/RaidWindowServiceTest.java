package net.tfminecraft.simplefactions.war.campaign.raid.fight;


import net.tfminecraft.simplefactions.war.campaign.raid.fight.RaidWindowService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;

class RaidWindowServiceTest {
	@BeforeEach
	void setUp() {
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
	}

	@Test
	void listRaidHours_defaultWindow() {
		assertEquals(List.of(19, 20), RaidWindowService.listRaidHours());
	}

	@Test
	void isRaidHour_acceptsWindowBounds() {
		assertTrue(RaidWindowService.isRaidHour(19));
		assertTrue(RaidWindowService.isRaidHour(20));
		assertFalse(RaidWindowService.isRaidHour(18));
		assertFalse(RaidWindowService.isRaidHour(21));
	}
}
