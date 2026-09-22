package me.Plugins.SimpleFactions.Managers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.Army.Military;

class WarManagerDeclareTest {
	private final List<Faction> savedFactions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		savedFactions.addAll(FactionManager.factions);
		FactionManager.factions.clear();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.clear();
		FactionManager.factions.addAll(savedFactions);
	}

	@Test
	void declareWar_doesNotCallEndVassalage() {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getRelations()).thenReturn(new HashMap<>());
		when(defender.getRelations()).thenReturn(new HashMap<>());
		when(attacker.getCapital()).thenReturn(5);
		when(defender.getCapital()).thenReturn(30);
		Military military = mock(Military.class);
		when(attacker.getMilitary()).thenReturn(military);
		when(military.getManpower(true)).thenReturn(5);
		FactionManager.factions.add(defender);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class, CALLS_REAL_METHODS)) {
			assertNull(WarManager.declareWar(attacker, defender, WarGoalType.SUBJUGATE, null, null));
			relations.verify(() -> RelationManager.endVassalage(any(), any(), anyBoolean()), never());
		}
	}
}
