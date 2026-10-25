package net.tfminecraft.simplefactions.espionage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EspionageStateVacateTest {
	@Test
	void onlyTheSpymasterWhoLeftGivesUpTheOffice() {
		var state = new EspionageState();
		assertFalse(state.vacateFor(EspionageTestFixtures.key("Spy")));
		state.appoint(EspionageTestFixtures.bind(new SpecialPositionAssignment(), "Spy"), 80);
		assertFalse(state.vacateFor(EspionageTestFixtures.key("Other")));
		assertNotNull(state.getSpymaster());
		assertTrue(state.vacateFor(EspionageTestFixtures.key("Spy")));
		assertNull(state.getSpymaster());
	}
}
