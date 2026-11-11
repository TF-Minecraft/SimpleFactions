package net.tfminecraft.simplefactions.war.campaign.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.mercenary.contract.ContractFixture;
import net.tfminecraft.simplefactions.mercenary.contract.MercenaryContract;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.buildup.BuildupService;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarVoting;

/** A mercenary under contract votes on the battle day and hour for the faction that hired them. */
class MercenaryBattleVotesTest {
	private ContractFixture fixture;
	private Faction enemy;
	private War war;

	@BeforeEach
	void setUp() {
		fixture = ContractFixture.formed(2);
		when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
		when(net.tfminecraft.simplefactions.mercenary.contract.MercenaryLoyalty.hostFaction(fixture.company)
				.getRelations()).thenReturn(new HashMap<>());
		enemy = ContractFixture.faction("enemy_realm");
		when(enemy.getRelations()).thenReturn(new HashMap<>());
		var empty = mock(net.tfminecraft.simplefactions.objects.handler.GuildHandler.class);
		when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
		when(enemy.getGuildHandler()).thenReturn(empty);
		FactionManager.factions.add(enemy);
		Cache.warBattleWindowStartHour = 21;
		Cache.warBattleWindowEndHour = 26;
		Cache.warVoteCloseHour = 16;
		Cache.warBuildupDayOptions = 5;

		war = new War(1, fixture.hirer, enemy);
		war.setBattleDay(LocalDate.of(2026, 11, 20));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
	}

	@AfterEach
	void tearDown() {
		ContractFixture.tearDown();
	}

	private void hireWithSigrun() {
		MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
		assertTrue(contract.activate());
		fixture.company.enlist("Sigrun");
	}

	private static UUID sigrun() {
		return BattleScheduleLookups.spoofMemberUuid("Sigrun");
	}

	@Test
	void aContractedMercenaryVotesForTheirHirer() {
		hireWithSigrun();

		assertSame(fixture.hirer, BattleScheduleLookups.votingFaction(war, sigrun()));
		assertSame(fixture.hirer, WarVoting.factionOf(war, sigrun()));
		assertNull(BattleScheduleLookups.votingFaction(war, BattleScheduleLookups.spoofMemberUuid("Stranger")));
		assertNull(BattleScheduleLookups.votingFaction(null, sigrun()));
		assertNull(BattleScheduleLookups.votingFaction(war, null));
	}

	@Test
	void withoutAContractTheCompanyHasNoVote() {
		fixture.company.enlist("Sigrun");

		assertNull(BattleScheduleLookups.votingFaction(war, sigrun()));
	}

	@Test
	void aMercenarysHourVoteCountsForTheHirersSide() {
		hireWithSigrun();

		assertEquals(VoteResults.BattleVoteToggleResult.ADDED,
				BattleVoteService.toggleVote(war, sigrun(), 22, BattleScheduleLookups.votingFaction(war, sigrun()), true));

		Map<Integer, VoteResults.BattleHourTally> tally =
				BattleVoteService.buildHourTally(war, BattleScheduleLookups.uuidToFactionForWar(war));
		assertEquals(1, tally.get(22).attackerCount());
		assertEquals(0, tally.get(22).defenderCount());
	}

	@Test
	void aMercenarysDayVoteCountsForTheHirersSide() {
		hireWithSigrun();
		war.setBattleSchedulePhase(BattleSchedulePhase.BUILDUP);
		war.setDayVoteFirstDay(LocalDate.of(2026, 11, 20));
		Faction voting = BattleScheduleLookups.votingFaction(war, sigrun());

		assertTrue(BuildupService.canToggleDayVote(war, voting));
		BuildupService.toggleDayVote(war, sigrun(), 1, voting, true);

		var tally = BuildupService.buildDayTally(war, BattleScheduleLookups.uuidToFactionForWar(war));
		assertEquals(1, tally.get(1).attackerCount());
	}

	@Test
	void thePlayedCharactersOwnFactionComesFirst() {
		hireWithSigrun();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(sigrun());
		try (MockedStatic<Members> members = mockStatic(Members.class, CALLS_REAL_METHODS)) {
			members.when(() -> Members.key(player)).thenReturn("Sigrun");
			// Sigrun's own faction is not in the war: they vote for the hirer.
			assertSame(fixture.hirer, BattleScheduleLookups.voterFaction(war, player));

			when(enemy.isMember("Sigrun")).thenReturn(true);
			assertSame(enemy, BattleScheduleLookups.voterFaction(war, player));
		}
		assertNull(BattleScheduleLookups.voterFaction(null, player));
		assertNull(BattleScheduleLookups.voterFaction(war, null));
	}

	@Test
	void signingAContractRefreshesTheWebsitesVotersForTheHirersWars() {
		MercenaryContract contract = fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis());
		Faction outsider = ContractFixture.faction("outsider");
		War elsewhere = new War(2, enemy, outsider);
		var government = mock(net.tfminecraft.simplefactions.government.Government.class);
		when(government.isCouncilMember("signer")).thenReturn(true);
		when(fixture.hirer.getGovernment()).thenReturn(government);
		try (MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<WarHistoryEvents> history = mockStatic(WarHistoryEvents.class)) {
			wars.when(WarManager::getActive).thenReturn(List.of(war, elsewhere));

			assertTrue(fixture.company.getContractHandler().accept(contract.getId(), fixture.hirer, "signer").ok());

			history.verify(() -> WarHistoryEvents.votesChanged(war));
			history.verify(() -> WarHistoryEvents.votesChanged(elsewhere), never());
		}
	}

	@Test
	void aMercenaryCountsTowardQuorumOnlyWhileUnderContract() {
		int min = Cache.warBattleVotingMinPlayers;
		boolean dev = Cache.warBattleVotingDevMinPlayersEnabled;
		try {
			Cache.warBattleVotingMinPlayers = 1;
			Cache.warBattleVotingDevMinPlayersEnabled = false;
			hireWithSigrun();
			war.getBattleVotes().put(sigrun(), new java.util.HashSet<>(Set.of(22)));
			war.getBattleVotes().put(BattleScheduleLookups.spoofMemberUuid("Stranger"), new java.util.HashSet<>(Set.of(22)));

			assertEquals(1, BattleQuorumService.meetsQuorum(war, war.getBattleVotes(),
					BattleScheduleLookups.spoofMemberNameToUuid(), BattleScheduleLookups.uuidToFactionForWar(war)).distinctVoters());

			fixture.company.getContractHandler().getActive().get(0)
					.finish(net.tfminecraft.simplefactions.mercenary.contract.ContractStatus.TERMINATED);
			assertEquals(0, BattleQuorumService.meetsQuorum(war, war.getBattleVotes(),
					BattleScheduleLookups.spoofMemberNameToUuid(), BattleScheduleLookups.uuidToFactionForWar(war)).distinctVoters());
		} finally {
			Cache.warBattleVotingMinPlayers = min;
			Cache.warBattleVotingDevMinPlayersEnabled = dev;
		}
	}

	@Test
	void theTallyIgnoresAMercenaryWhoseContractEnded() {
		hireWithSigrun();
		war.getBattleVotes().put(sigrun(), new java.util.HashSet<>(Set.of(22)));
		fixture.company.getContractHandler().getActive().get(0)
				.finish(net.tfminecraft.simplefactions.mercenary.contract.ContractStatus.TERMINATED);

		var tally = BattleVoteService.buildHourTally(war, BattleScheduleLookups.uuidToFactionForWar(war));
		assertEquals(0, tally.get(22).attackerCount());
		assertNull(BattleScheduleLookups.votingFaction(war, sigrun()));
	}
}
