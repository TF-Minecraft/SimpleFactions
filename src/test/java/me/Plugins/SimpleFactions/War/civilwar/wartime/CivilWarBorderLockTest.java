package me.Plugins.SimpleFactions.War.civilwar.wartime;



import me.Plugins.SimpleFactions.War.civilwar.CivilWarSnapshot;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarCopy;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarBorderLock;
import me.Plugins.SimpleFactions.War.civilwar.wartime.CivilWarWartimeVassalEnd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.government.movement.Action;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;

class CivilWarBorderLockTest {
	private final List<Faction> savedFactions = new java.util.ArrayList<>();

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
	void involvedIds_hostRebelsAndNestedVassals() {
		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		snapshot.setTempRebelFactionId("rebels");
		snapshot.setWartimeVassalEnds(List.of(new CivilWarWartimeVassalEnd("vassal", "host", "march")));
		War war = mock(War.class);
		when(war.getCivilWarSnapshot()).thenReturn(snapshot);

		Faction root = mock(Faction.class);
		when(root.getId()).thenReturn("vassal");
		Faction nested = mock(Faction.class);
		when(nested.getId()).thenReturn("nested");
		FactionManager.factions.add(root);
		FactionManager.factions.add(nested);

		try (MockedStatic<RelationManager> relations = mockStatic(RelationManager.class)) {
			relations.when(() -> RelationManager.isOnOverlordPath(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
					.thenReturn(false);
			relations.when(() -> RelationManager.isOnOverlordPath(nested, root)).thenReturn(true);

			Set<String> ids = CivilWarBorderLock.involvedIds(war);
			assertTrue(ids.contains("host"));
			assertTrue(ids.contains("rebels"));
			assertTrue(ids.contains("vassal"));
			assertTrue(ids.contains("nested"));
		}
	}

	@Test
	void foreignBacker_notInvolved() {
		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		War war = mock(War.class);
		when(war.getCivilWarSnapshot()).thenReturn(snapshot);
		Set<String> ids = CivilWarBorderLock.involvedIds(war);
		assertFalse(ids.contains("backer"));
		assertEquals(Set.of("host"), ids);
	}

	@Test
	void isLocked_trueForHostOfActiveCivilWar() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		CivilWarSnapshot snapshot = new CivilWarSnapshot();
		snapshot.setHostFactionId("host");
		War war = mock(War.class);
		when(war.isActive()).thenReturn(true);
		when(war.getCivilWarSnapshot()).thenReturn(snapshot);
		when(war.getMovementId()).thenReturn("mov");

		Faction backer = mock(Faction.class);
		when(backer.getId()).thenReturn("backer");

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war));
			assertTrue(CivilWarBorderLock.isLocked(host));
			assertFalse(CivilWarBorderLock.isLocked(backer));
		}
	}

	@Test
	void hostBlockedByDeJureOrTransfer_defenderAndPayload() {
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		War deJure = mock(War.class);
		when(deJure.isActive()).thenReturn(true);
		when(deJure.getGoal()).thenReturn(WarGoalType.DE_JURE_ANNEX);
		when(deJure.getDefenderLeaderId()).thenReturn("host");

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(deJure));
			assertTrue(CivilWarBorderLock.hostBlockedByDeJureOrTransfer(host));
		}

		War transfer = mock(War.class);
		when(transfer.isActive()).thenReturn(true);
		when(transfer.getGoal()).thenReturn(WarGoalType.TRANSFER_SUBJECT);
		when(transfer.getDefenderLeaderId()).thenReturn("other");
		when(transfer.getSubjectFactionId()).thenReturn("host");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(transfer));
			assertTrue(CivilWarBorderLock.hostBlockedByDeJureOrTransfer(host));
		}
	}

	@Test
	void refuseStart_alreadyInCivilWar() {
		Movement movement = mock(Movement.class);
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		when(movement.getFaction()).thenReturn(host);
		when(movement.getLeader()).thenReturn("Alice");
		when(movement.getAllSupportingFactions()).thenReturn(List.of());
		Cause cause = mock(Cause.class);
		when(cause.getAction()).thenReturn(Action.CHANGE_LEADER);
		when(movement.getCauses()).thenReturn(List.of(cause));

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			CivilWarSnapshot snapshot = new CivilWarSnapshot();
			snapshot.setHostFactionId("host");
			War war = mock(War.class);
			when(war.isActive()).thenReturn(true);
			when(war.getCivilWarSnapshot()).thenReturn(snapshot);
			wars.when(WarManager::getActive).thenReturn(List.of(war));
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(null);

			assertEquals(CivilWarCopy.ALREADY_IN_CIVIL_WAR, CivilWarBorderLock.refuseStart(movement, host));
		}
	}

	@Test
	void refuseStart_hostIsTransferPayload() {
		Movement movement = mock(Movement.class);
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		when(movement.getAllSupportingFactions()).thenReturn(List.of());
		when(movement.getLeader()).thenReturn("Alice");
		War transfer = mock(War.class);
		when(transfer.isActive()).thenReturn(true);
		when(transfer.getGoal()).thenReturn(WarGoalType.TRANSFER_SUBJECT);
		when(transfer.getSubjectFactionId()).thenReturn("host");

		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(transfer));
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(null);
			assertEquals(CivilWarCopy.HOST_IS_WAR_PAYLOAD, CivilWarBorderLock.refuseStart(movement, host));
		}
	}

	@Test
	void refuseStart_nullWhenClear() {
		Movement movement = mock(Movement.class);
		Faction host = mock(Faction.class);
		when(host.getId()).thenReturn("host");
		when(movement.getAllSupportingFactions()).thenReturn(List.of());
		when(movement.getLeader()).thenReturn("Alice");
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of());
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(null);
			assertNull(CivilWarBorderLock.refuseStart(movement, host));
		}
	}
}
