package me.Plugins.SimpleFactions.government.movement.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Database.Database;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.civilwar.CivilWarStartService;
import me.Plugins.SimpleFactions.government.movement.Movement;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeService;
import me.Plugins.SimpleFactions.government.movement.MovementOutcomeSource;
import me.Plugins.SimpleFactions.government.movement.Pool;
import me.Plugins.SimpleFactions.government.movement.cause.Cause;
import me.Plugins.SimpleFactions.government.proposal.Proposal;

class MovementAdminServiceTest {

	@Test
	void joinSupporter_citizen_callsJoin() {
		Movement movement = mock(Movement.class);
		Pool supporters = new Pool();
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getSupporters()).thenReturn(supporters);
		when(movement.getFaction()).thenReturn(mock(Faction.class));
		when(movement.canJoin(eq("Bob"), isNull(), eq(false))).thenReturn(true);
		doAnswer(invocation -> {
			supporters.addCitizen("Bob");
			return null;
		}).when(movement).join(eq("Bob"), isNull());

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			MovementAdminService.Result result = MovementAdminService.joinSupporter("alice_movement", "citizen", "Bob");
			assertTrue(result.ok());
			verify(movement).join("Bob", null);
		}
	}

	@Test
	void joinSupporter_guild_callsJoin() {
		Movement movement = mock(Movement.class);
		Guild guild = mock(Guild.class);
		Pool supporters = new Pool();
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getSupporters()).thenReturn(supporters);
		when(movement.getFaction()).thenReturn(mock(Faction.class));
		when(movement.canJoin(eq(guild), isNull(), eq(false))).thenReturn(true);
		doAnswer(invocation -> {
			supporters.addGuild(guild);
			return null;
		}).when(movement).join(eq(guild), isNull());

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			factions.when(() -> FactionManager.getGuildByString("iron")).thenReturn(guild);
			MovementAdminService.Result result = MovementAdminService.joinSupporter("alice_movement", "guild", "iron");
			assertTrue(result.ok());
			verify(movement).join(guild, null);
		}
	}

	@Test
	void joinSupporter_vassal_callsJoin() {
		Movement movement = mock(Movement.class);
		Faction vassal = mock(Faction.class);
		Pool supporters = new Pool();
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getSupporters()).thenReturn(supporters);
		when(movement.getFaction()).thenReturn(mock(Faction.class));
		when(movement.canJoin(eq(vassal), isNull(), eq(false))).thenReturn(true);
		doAnswer(invocation -> {
			supporters.addFaction(vassal);
			return null;
		}).when(movement).join(eq(vassal), isNull());

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			factions.when(() -> FactionManager.getByString("subject")).thenReturn(vassal);
			MovementAdminService.Result result = MovementAdminService.joinSupporter("alice_movement", "vassal", "subject");
			assertTrue(result.ok());
			verify(movement).join(vassal, null);
		}
	}

	@Test
	void joinBacker_callsJoinAsForeignBacker() {
		Movement movement = mock(Movement.class);
		Faction backer = mock(Faction.class);
		when(backer.getId()).thenReturn("foreign");
		List<Faction> backers = new ArrayList<>();
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getForeignBackers()).thenReturn(backers);
		when(movement.getFaction()).thenReturn(mock(Faction.class));
		doAnswer(invocation -> {
			backers.add(backer);
			return null;
		}).when(movement).joinAsForeignBacker(backer);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			factions.when(() -> FactionManager.getByString("foreign")).thenReturn(backer);
			MovementAdminService.Result result = MovementAdminService.joinBacker("alice_movement", "foreign");
			assertTrue(result.ok());
			verify(movement).joinAsForeignBacker(backer);
		}
	}

	@Test
	void unknownMovement_fails() {
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getMovementById("missing")).thenReturn(null);
			MovementAdminService.Result result = MovementAdminService.joinSupporter("missing", "citizen", "Bob");
			assertFalse(result.ok());
			assertTrue(result.message().contains("Unknown movement"));
		}
	}

	@Test
	void unknownGuild_fails() {
		Movement movement = mock(Movement.class);
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			factions.when(() -> FactionManager.getGuildByString("nope")).thenReturn(null);
			MovementAdminService.Result result = MovementAdminService.joinSupporter("alice_movement", "guild", "nope");
			assertFalse(result.ok());
			verify(movement, never()).join(any(), any());
		}
	}

	@Test
	void ineligible_doesNotJoin() {
		Movement movement = mock(Movement.class);
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getSupporters()).thenReturn(new Pool());
		when(movement.joinBlockReason(eq("Bob"), isNull(), eq(true)))
				.thenReturn("§cBob is not a citizen of Lantan.");

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			MovementAdminService.Result result = MovementAdminService.joinSupporter("alice_movement", "citizen", "Bob");
			assertFalse(result.ok());
			assertEquals("§cBob is not a citizen of Lantan.", result.message());
			verify(movement, never()).join(any(), any());
		}
	}

	@Test
	void joinCause_callsJoinWithCause() {
		Movement movement = mock(Movement.class);
		Cause cause = mock(Cause.class);
		Pool pool = new Pool();
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getCauses()).thenReturn(List.of(cause));
		when(cause.getPool()).thenReturn(pool);
		when(cause.getIndex()).thenReturn(0);
		when(movement.getFaction()).thenReturn(mock(Faction.class));
		when(movement.canJoin(eq("Bob"), eq(cause), eq(false))).thenReturn(true);
		doAnswer(invocation -> {
			pool.addCitizen("Bob");
			return null;
		}).when(movement).join(eq("Bob"), eq(cause));

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			MovementAdminService.Result result =
					MovementAdminService.joinCause("alice_movement", "0", "citizen", "Bob");
			assertTrue(result.ok());
			verify(movement).join("Bob", cause);
		}
	}

	@Test
	void demands_accept_bypassesOrganizationAndAppliesOutcome() {
		Movement movement = mock(Movement.class);
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.getCauses()).thenReturn(List.of());
		when(movement.getFaction()).thenReturn(mock(Faction.class));

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> outcomes = mockStatic(MovementOutcomeService.class);
				MockedStatic<CivilWarStartService> start = mockStatic(CivilWarStartService.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			MovementAdminService.Result result = MovementAdminService.demands("alice_movement", "accept");
			assertTrue(result.ok());
			outcomes.verify(() -> MovementOutcomeService.apply(movement, MovementOutcomeSource.ACCEPTED));
			start.verifyNoInteractions();
			verify(movement, never()).getOrganization();
		}
	}

	@Test
	void demands_reject_startsCivilWarWithoutOrganization() {
		Movement movement = mock(Movement.class);
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.getCauses()).thenReturn(List.of());
		when(movement.getFaction()).thenReturn(mock(Faction.class));

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<MovementOutcomeService> outcomes = mockStatic(MovementOutcomeService.class);
				MockedStatic<CivilWarStartService> start = mockStatic(CivilWarStartService.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			start.when(() -> CivilWarStartService.start(movement)).thenReturn(null);
			MovementAdminService.Result result = MovementAdminService.demands("alice_movement", "reject");
			assertTrue(result.ok());
			start.verify(() -> CivilWarStartService.start(movement));
			outcomes.verifyNoInteractions();
			verify(movement, never()).getOrganization();
		}
	}

	@Test
	void demands_unknownMovement_fails() {
		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class)) {
			factions.when(() -> FactionManager.getMovementById("missing")).thenReturn(null);
			MovementAdminService.Result result = MovementAdminService.demands("missing", "accept");
			assertFalse(result.ok());
		}
	}

	@Test
	void target_setsWantedLeaderWithoutOnlineCheck() {
		Movement movement = mock(Movement.class);
		Faction host = mock(Faction.class);
		Cause cause = mock(Cause.class);
		Proposal proposal = mock(Proposal.class);
		when(movement.getId()).thenReturn("alice_movement");
		when(movement.isFrozen()).thenReturn(false);
		when(movement.getFaction()).thenReturn(host);
		when(movement.getCauses()).thenReturn(List.of(cause));
		when(cause.getProposal()).thenReturn(proposal);
		when(proposal.needsTarget()).thenReturn(true);
		when(host.canBecomeLeader("dummy_11")).thenReturn(true);

		try (MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedConstruction<Database> ignored = mockConstruction(Database.class)) {
			factions.when(() -> FactionManager.getMovementById("alice_movement")).thenReturn(movement);
			MovementAdminService.Result result = MovementAdminService.target("alice_movement", "0", "dummy_11");
			assertTrue(result.ok());
			verify(proposal).setTarget("dummy_11");
		}
	}
}
