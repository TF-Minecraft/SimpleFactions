package net.tfminecraft.simplefactions.war.campaign.vote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.runtime.CampaignClock;

class BattleVoterEligibilityTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private War war;

	@BeforeEach
	void setUp() {
		CampaignClock.reset();
		Cache.warVoteCloseHour = 16;

		attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
	}

	@AfterEach
	void tearDown() {
		CampaignClock.reset();
	}

	@Test
	void canToggleVote_trueBeforeVoteClose() {
		Instant beforeClose = BattleWindowService.atScheduleHour(BATTLE_DAY, 12);

		assertTrue(BattleVoterEligibility.canToggleVote(war, attacker, beforeClose));
	}

	@Test
	void canToggleVote_falseAfterVoteCloseWhileStillVoting() {
		Instant atClose = BattleWindowService.atScheduleHour(BATTLE_DAY, 16);

		assertTrue(BattleVoterEligibility.isEligibleVoter(war, attacker));
		assertFalse(BattleVoterEligibility.canToggleVote(war, attacker, atClose));
	}
}
