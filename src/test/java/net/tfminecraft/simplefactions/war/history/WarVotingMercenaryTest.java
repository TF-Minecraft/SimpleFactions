package net.tfminecraft.simplefactions.war.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.gson.JsonObject;

import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.mercenary.contract.ContractFixture;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;

/** The website learns which accounts may vote; a contracted mercenary is one of them. */
class WarVotingMercenaryTest {
	private ContractFixture fixture;
	private War war;
	private final UUID sigrun = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		fixture = ContractFixture.formed(2);
		when(fixture.hirer.getRelations()).thenReturn(new HashMap<>());
		Faction enemy = ContractFixture.faction("enemy_realm");
		var empty = mock(net.tfminecraft.simplefactions.objects.handler.GuildHandler.class);
		when(empty.getGuilds()).thenReturn(new java.util.ArrayList<>());
		when(enemy.getGuildHandler()).thenReturn(empty);
		FactionManager.factions.add(enemy);
		assertTrue(fixture.offer(ContractFixture.validTerms(2), System.currentTimeMillis()).activate());
		fixture.company.enlist("Sigrun");
		// Defending this time: the company sides with whoever hired it.
		war = new War(1, enemy, fixture.hirer);
	}

	@AfterEach
	void tearDown() {
		WarVoting.maxAccounts = 2000;
		ContractFixture.tearDown();
	}

	@Test
	void aContractedMercenaryIsEligibleOnTheHirersSide() {
		try (MockedStatic<Members> members = mockStatic(Members.class, CALLS_REAL_METHODS)) {
			members.when(() -> Members.accountId("Sigrun")).thenReturn(Optional.of(sigrun));
			members.when(() -> Members.accountId("Soldier0")).thenReturn(Optional.empty());

			JsonObject eligible = WarVoting.eligible(war);

			assertEquals("defender", eligible.get(sigrun.toString()).getAsString());
		}
	}

	@Test
	void noHirerNoUpdate() {
		try (MockedStatic<WarHistoryEvents> history = org.mockito.Mockito.mockStatic(WarHistoryEvents.class)) {
			WarVoting.mercenaryVotersChanged(null);
			history.verifyNoInteractions();
		}
	}

	@Test
	void theAccountCapAlsoStopsTheMercenaries() {
		WarVoting.maxAccounts = 0;
		try (MockedStatic<Members> members = mockStatic(Members.class, CALLS_REAL_METHODS)) {
			members.when(() -> Members.accountId("Sigrun")).thenReturn(Optional.of(sigrun));

			assertEquals(0, WarVoting.eligible(war).size());
		}
	}
}
