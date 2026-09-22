package me.Plugins.SimpleFactions.War.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class BattleScheduleLookupsTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of("Alice", "Bob"));
		when(defender.getMembers()).thenReturn(List.of("Carol"));
	}

	@Test
	void spoofMemberUuid_isDistinctPerName() {
		assertNotEquals(
				BattleScheduleLookups.spoofMemberUuid("Alice"),
				BattleScheduleLookups.spoofMemberUuid("Bob"));
	}

	@Test
	void uuidToFactionForWar_resolvesSpoofVotesToSideFaction() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);

		var lookup = BattleScheduleLookups.uuidToFactionForWar(war);
		assertEquals(attacker, lookup.apply(BattleScheduleLookups.spoofMemberUuid("Alice")));
		assertEquals(defender, lookup.apply(BattleScheduleLookups.spoofMemberUuid("Carol")));
		assertNotNull(lookup.apply(BattleScheduleLookups.spoofMemberUuid("Bob")));
	}
}
