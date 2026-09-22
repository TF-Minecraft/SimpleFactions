package me.Plugins.SimpleFactions.War.campaign.vote;

import static me.Plugins.SimpleFactions.War.campaign.vote.VoteResults.QuorumResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.Participant;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleSideMembers;

class BattleQuorumServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;
		Cache.warBattleVotingMinPlayers = 4;
		Cache.warBattleVotingRequireSmallestSideFull = true;
		Cache.warBattleVotingPassIfEither = true;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of("Alice", "Bob"));
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave", "Eve", "Frank", "Grace"));
	}

	@Test
	void meetsQuorum_passesViaMinPlayers() {
		War war = baseWar();
		UUID u1 = UUID.randomUUID();
		UUID u2 = UUID.randomUUID();
		UUID u3 = UUID.randomUUID();
		UUID u4 = UUID.randomUUID();
		war.getBattleVotes().put(u1, Set.of(21));
		war.getBattleVotes().put(u2, Set.of(21));
		war.getBattleVotes().put(u3, Set.of(21));
		war.getBattleVotes().put(u4, Set.of(21));

		QuorumResult result = BattleQuorumService.meetsQuorum(war, name -> null);
		assertTrue(result.passed());
		assertTrue(result.passMinPlayers());
		assertFalse(result.passSmallestSideFull());
	}

	@Test
	void meetsQuorum_passesViaSmallestSideFull() {
		War war = baseWar();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		war.getBattleVotes().put(alice, Set.of(21));
		war.getBattleVotes().put(bob, Set.of(22));

		Function<String, UUID> memberNameToUuid = name -> switch (name) {
			case "Alice" -> alice;
			case "Bob" -> bob;
			default -> null;
		};

		QuorumResult result = BattleQuorumService.meetsQuorum(war, memberNameToUuid);
		assertTrue(result.passed());
		assertFalse(result.passMinPlayers());
		assertTrue(result.passSmallestSideFull());
		assertEquals(2, result.attackerEligible());
		assertEquals(5, result.defenderEligible());
	}

	@Test
	void meetsQuorum_failsWhenNeitherThresholdMet() {
		War war = baseWar();
		UUID alice = UUID.randomUUID();
		war.getBattleVotes().put(alice, Set.of(21));

		QuorumResult result = BattleQuorumService.meetsQuorum(
				war,
				name -> "Alice".equalsIgnoreCase(name) ? alice : null);
		assertFalse(result.passed());
	}

	@Test
	void meetsQuorum_passIfEitherFalse_requiresBoth() {
		Cache.warBattleVotingPassIfEither = false;
		War war = baseWar();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		war.getBattleVotes().put(alice, Set.of(21));
		war.getBattleVotes().put(bob, Set.of(21));

		Function<String, UUID> memberNameToUuid = name -> switch (name) {
			case "Alice" -> alice;
			case "Bob" -> bob;
			default -> null;
		};

		QuorumResult result = BattleQuorumService.meetsQuorum(war, memberNameToUuid);
		assertFalse(result.passed());
		assertFalse(result.passMinPlayers());
		assertTrue(result.passSmallestSideFull());
	}

	@Test
	void battleSideMembers_calledAllyCountsTowardEligibleRoster() {
		Faction ally = mock(Faction.class);
		when(ally.getId()).thenReturn("ally");
		when(ally.getMembers()).thenReturn(List.of("AllyMember"));

		War war = baseWar();
		Participant attackerParticipant = war.getAttackers().getMainParticipants().get(0);
		attackerParticipant.getAllies().put(ally, true);

		assertTrue(BattleSideMembers.collectEligibleMemberNames(war.getAttackers()).contains("AllyMember"));
		assertEquals(BelligerentRole.ATTACKER, BattleSideMembers.resolveSide(war, ally));
	}

	@Test
	void battleSideMembers_uncalledAllyExcluded() {
		Faction ally = mock(Faction.class);
		when(ally.getId()).thenReturn("ally");
		when(ally.getMembers()).thenReturn(List.of("AllyMember"));

		War war = baseWar();
		Participant attackerParticipant = war.getAttackers().getMainParticipants().get(0);
		attackerParticipant.getAllies().put(ally, false);

		assertFalse(BattleSideMembers.collectEligibleMemberNames(war.getAttackers()).contains("AllyMember"));
		assertFalse(war.getAttackers().isParticipating(ally));
	}

	@Test
	void meetsQuorum_usesDevMinPlayersWhenConfigured() {
		Cache.warBattleVotingMinPlayers = 4;
		Cache.warBattleVotingDevMinPlayers = 1;
		Cache.warBattleVotingDevMinPlayersEnabled = true;

		War war = baseWar();
		UUID voter = UUID.randomUUID();
		war.getBattleVotes().put(voter, Set.of(21));

		QuorumResult result = BattleQuorumService.meetsQuorum(war, name -> null);
		assertTrue(result.passed());
		assertTrue(result.passMinPlayers());
		assertEquals(1, BattleQuorumService.effectiveMinPlayers());
	}

	@Test
	void effectiveMinPlayers_usesMinPlayersWhenDevKeyAbsent() {
		Cache.warBattleVotingMinPlayers = 4;
		Cache.warBattleVotingDevMinPlayersEnabled = false;

		assertEquals(4, BattleQuorumService.effectiveMinPlayers());
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		return war;
	}
}
