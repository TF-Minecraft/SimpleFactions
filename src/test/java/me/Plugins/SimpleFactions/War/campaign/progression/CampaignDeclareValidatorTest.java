package me.Plugins.SimpleFactions.War.campaign.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Objects.Faction;

class CampaignDeclareValidatorTest {
	@Test
	void validateAttackerCanDeclare_failsWithoutOffensiveArmy() {
		Faction attacker = attackerWithOffensiveManpower(0);
		assertFalse(CampaignDeclareValidator.validateAttackerCanDeclare(attacker).isValid());
	}

	@Test
	void validateAttackerCanDeclare_passesWithProfessionalOffensiveArmy() {
		Faction attacker = attackerWithOffensiveManpower(5);
		assertTrue(CampaignDeclareValidator.validateAttackerCanDeclare(attacker).isValid());
	}

	@Test
	void validateAttackerCanDeclare_passesWithLevyOffensiveArmy() {
		Faction attacker = attackerWithOffensiveManpower(1);
		assertTrue(CampaignDeclareValidator.validateAttackerCanDeclare(attacker).isValid());
	}

	@Test
	void validateAttackerCanDeclare_failsWhenMilitaryMissing() {
		Faction attacker = mock(Faction.class);
		when(attacker.getMilitary()).thenReturn(null);
		assertFalse(CampaignDeclareValidator.validateAttackerCanDeclare(attacker).isValid());
	}

	private static Faction attackerWithOffensiveManpower(int manpower) {
		Faction attacker = mock(Faction.class);
		Military military = mock(Military.class);
		when(attacker.getMilitary()).thenReturn(military);
		when(military.getManpower(true)).thenReturn(manpower);
		return attacker;
	}
}
