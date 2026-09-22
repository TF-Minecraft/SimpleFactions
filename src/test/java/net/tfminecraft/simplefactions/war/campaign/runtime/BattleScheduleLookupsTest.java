package net.tfminecraft.simplefactions.war.campaign.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

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
