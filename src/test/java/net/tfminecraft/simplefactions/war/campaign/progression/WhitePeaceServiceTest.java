package net.tfminecraft.simplefactions.war.campaign.progression;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarEndReason;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

class WhitePeaceServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getCapital()).thenReturn(5);
		Cache.warFirstBattleAtBorder = true;
		Cache.warProvincesBetweenBattles = 1;
	}

	@Test
	void recalculateProposals_marksAggressorWhenUnreachable() {
		War war = baseWar();
		try (MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(true);

			assertEquals(Optional.empty(), WhitePeaceService.recalculateProposals(war));
			assertTrue(war.isWhitePeaceProposedByAttacker());
			assertFalse(war.isWhitePeaceProposedByDefender());
		}
	}

	@Test
	void recalculateProposals_clearsAggressorWhenReachable() {
		War war = baseWar();
		war.setWhitePeaceProposedByAttacker(true);
		try (MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(true);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(true);

			WhitePeaceService.recalculateProposals(war);
			assertFalse(war.isWhitePeaceProposedByAttacker());
		}
	}

	@Test
	void recalculateProposals_marksDefenderUnreachable() {
		War war = baseWar();
		try (MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(true);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(false);

			WhitePeaceService.recalculateProposals(war);
			assertTrue(war.isWhitePeaceProposedByDefender());
		}
	}

	@Test
	void recalculateProposals_shortAxisAtObjective_doesNotAutoEnd() {
		War war = shortAxisWarAtObjective();
		assertEquals(Optional.empty(), WhitePeaceService.recalculateProposals(war));
		assertFalse(war.isWhitePeaceProposedByAttacker());
		assertFalse(war.isWhitePeaceProposedByDefender());
	}

	@Test
	void recalculateProposals_freshWarWithoutRegiments_doesNotAutoEnd() {
		War war = baseWar();
		war.setCursorIndex(2);
		assertEquals(Optional.empty(), WhitePeaceService.recalculateProposals(war));
		assertFalse(war.isWhitePeaceProposedByAttacker());
		assertFalse(war.isWhitePeaceProposedByDefender());
	}

	@Test
	void recalculateProposals_autoEndsWhenBothStrategicallyExhausted() {
		War war = baseWar();
		try (MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(false);

			Optional<WarEndReason> reason = WhitePeaceService.recalculateProposals(war);
			assertEquals(WarEndReason.WHITE_PEACE, reason.orElse(null));
			assertTrue(war.isWhitePeaceProposedByAttacker());
			assertTrue(war.isWhitePeaceProposedByDefender());
		}
	}

	@Test
	void acceptWhitePeace_requiresEnemyProposal() {
		War war = baseWar();
		war.setWhitePeaceProposedByDefender(true);
		assertTrue(WhitePeaceService.acceptWhitePeace(war, attacker));
		assertFalse(WhitePeaceService.acceptWhitePeace(war, defender));
	}

	@Test
	void shouldAutoEnd_whenBothProposed() {
		War war = baseWar();
		war.setWhitePeaceProposedByAttacker(true);
		war.setWhitePeaceProposedByDefender(true);
		assertTrue(WhitePeaceService.shouldAutoEnd(war));
	}

	@Test
	void recalculateProposals_keepsForcedOfferWhenReachable() {
		War war = baseWar();
		war.setForcedWhitePeaceByAttacker(true);
		try (MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(true);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(true);

			WhitePeaceService.recalculateProposals(war);
			assertTrue(war.isWhitePeaceProposedByAttacker());
			assertFalse(war.isWhitePeaceProposedByDefender());
		}
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(10);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(1);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		return war;
	}

	private War shortAxisWarAtObjective() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(10);
		war.setCampaignStartProvinceId(5);
		war.setCampaignProvinces(List.of(5, 10));
		war.setCursorIndex(1);
		war.setCampaignBattlesFought(1);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		return war;
	}
}
