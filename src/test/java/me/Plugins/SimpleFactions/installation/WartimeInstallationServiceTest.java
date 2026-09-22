package me.Plugins.SimpleFactions.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class WartimeInstallationServiceTest {

	private List<Faction> previousFactions;

	@BeforeEach
	void setUp() {
		previousFactions = FactionManager.factions;
		FactionManager.factions = new ArrayList<>();
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions = previousFactions;
	}

	@Test
	void occupy_enemyTile_movesInstallToWarLeaderAndSnapshotsOriginal() {
		Fixture vassal = faction("vassal", "Alice");
		Fixture attacker = faction("attacker", "Bob");
		Fixture defender = faction("defender", "Carol");
		register(vassal, attacker, defender);
		vassal.handler.acceptTransferred(port("port-1", 42));
		War war = new War(1, attacker.faction, defender.faction);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			stubNeutralRelations(relations);
			WartimeInstallationService.occupy(war, attacker.faction, 42);
		}

		assertNull(vassal.handler.getById("port-1"));
		assertNotNull(attacker.handler.getById("port-1"));
		assertEquals("vassal", war.getWartimeInstallationOwners().get("port-1"));
	}

	@Test
	void occupy_recapture_returnsInstallToOriginalNotWarLeader() {
		Fixture vassal = faction("vassal", "Alice");
		Fixture attacker = faction("attacker", "Bob");
		Fixture defender = faction("defender", "Carol");
		register(vassal, attacker, defender);
		vassal.handler.acceptTransferred(port("port-1", 42));
		War war = new War(1, attacker.faction, defender.faction);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			stubNeutralRelations(relations);
			WartimeInstallationService.occupy(war, attacker.faction, 42);
			relations.when(() -> RelationManager.sameRealm(defender.faction, vassal.faction)).thenReturn(true);
			relations.when(() -> RelationManager.getSubjects(defender.faction)).thenReturn(List.of(vassal.faction));
			WartimeInstallationService.occupy(war, defender.faction, 42);
		}

		assertNotNull(vassal.handler.getById("port-1"));
		assertNull(defender.handler.getById("port-1"));
		assertNull(attacker.handler.getById("port-1"));
		assertEquals("vassal", war.getWartimeInstallationOwners().get("port-1"));
	}

	@Test
	void revert_thenLandTransfer_movesInstallOffDefenderToWinner() {
		Fixture defender = faction("defender", "Alice");
		Fixture attacker = faction("attacker", "Bob");
		register(defender, attacker);
		defender.handler.acceptTransferred(port("port-1", 42));
		War war = new War(1, attacker.faction, defender.faction);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			stubNeutralRelations(relations);
			WartimeInstallationService.occupy(war, attacker.faction, 42);
			WartimeInstallationService.revert(war);
			InstallationTransferService.transfer(defender.faction, attacker.faction, 42);
		}

		assertNull(defender.handler.getById("port-1"));
		assertNotNull(attacker.handler.getById("port-1"));
		assertTrue(war.getWartimeInstallationOwners().isEmpty());
	}

	private static void stubNeutralRelations(MockedStatic<RelationManager> relations) {
		relations.when(() -> RelationManager.sameRealm(any(), any())).thenReturn(false);
		relations.when(() -> RelationManager.getSubjects(any())).thenReturn(List.of());
	}

	private static void register(Fixture... fixtures) {
		for (Fixture fx : fixtures) {
			FactionManager.factions.add(fx.faction);
		}
	}

	private static Installation port(String id, int province) {
		return new Installation(id, "Harbour", InstallationKind.PORT, province, 0, 0, 1L);
	}

	private static Fixture faction(String id, String leader) {
		Fixture fx = new Fixture();
		fx.faction = mock(Faction.class);
		fx.handler = new InstallationHandler(fx.faction);
		when(fx.faction.getId()).thenReturn(id);
		when(fx.faction.getLeader()).thenReturn(leader);
		when(fx.faction.getRGB()).thenReturn("#ffffff");
		when(fx.faction.getInstallationHandler()).thenReturn(fx.handler);
		return fx;
	}

	private static final class Fixture {
		Faction faction;
		InstallationHandler handler;
	}
}
