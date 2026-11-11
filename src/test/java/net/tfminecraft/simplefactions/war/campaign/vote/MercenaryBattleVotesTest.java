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
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally;
import net.tfminecraft.simplefactions.war.campaign.availability.AvailabilityTally.HourCount;
import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleScheduleLookups;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.history.WarHistoryEvents;
import net.tfminecraft.simplefactions.war.history.WarVoting;

/** A mercenary under contract marks when they can fight for the faction that hired them. */
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
	void aMercenarysMarksCountForTheHirersSideAndTheyAddToItsSize() {
		LocalDate day = LocalDate.of(2026, 11, 20);
		int before = AvailabilityTally.sideSize(war, BelligerentRole.ATTACKER);
		hireWithSigrun();
		war.getAvailability().setMarks(sigrun(), day, Set.of(22));

		HourCount at22 = AvailabilityTally.count(war, day).get(1);
		assertEquals(22, at22.hour());
		assertEquals(1, at22.attackers());
		assertEquals(0, at22.defenders());
		assertEquals(before + fixture.company.getEnlisted().size(), AvailabilityTally.sideSize(war, BelligerentRole.ATTACKER));
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
	void theTallyIgnoresAMercenaryWhoseContractEnded() {
		hireWithSigrun();
		LocalDate day = LocalDate.of(2026, 11, 20);
		war.getAvailability().setMarks(sigrun(), day, Set.of(22));
		fixture.company.getContractHandler().getActive().get(0)
				.finish(net.tfminecraft.simplefactions.mercenary.contract.ContractStatus.TERMINATED);

		assertEquals(0, AvailabilityTally.count(war, day).get(1).attackers());
		assertNull(BattleScheduleLookups.votingFaction(war, sigrun()));
	}
}
