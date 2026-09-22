package me.Plugins.SimpleFactions.War.resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;

class WarResolutionServiceTest {
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getCapital()).thenReturn(5);
		when(defender.getCapital()).thenReturn(30);
		Cache.warFirstBattleAtBorder = true;
		Cache.warProvincesBetweenBattles = 1;
	}

	@Test
	void evaluateAndMaybeEnd_mutualWhitePeaceProposalsEndsWar() {
		War war = baseWar();

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
				MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.isValidWar(war)).thenReturn(true);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(false);
			capability.when(() -> CampaignCapabilityService.nextBattleProvince(war))
					.thenReturn(java.util.OptionalInt.empty());

			ArgumentCaptor<WarEndReason> reasonCaptor = ArgumentCaptor.forClass(WarEndReason.class);
			warManager.when(() -> WarManager.endWar(any(), reasonCaptor.capture())).then(inv -> null);

			Optional<WarEndReason> result = WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());

			assertEquals(WarEndReason.WHITE_PEACE, result.orElse(null));
			assertEquals(WarEndReason.WHITE_PEACE, reasonCaptor.getValue());
		}
	}

	@Test
	void evaluateAndMaybeEnd_offensiveStalemateEndsWhitePeace() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setCursorIndex(2);

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
				MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.isValidWar(war)).thenReturn(true);
			capability.when(() -> CampaignCapabilityService.nextBattleProvince(war))
					.thenReturn(java.util.OptionalInt.of(20));
			capability.when(() -> CampaignCapabilityService.canMountOffensive(
					war, CampaignCoalition.AGGRESSOR, 20)).thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canMountOffensive(
					war, CampaignCoalition.DEFENDER, 20)).thenReturn(false);

			ArgumentCaptor<WarEndReason> reasonCaptor = ArgumentCaptor.forClass(WarEndReason.class);
			warManager.when(() -> WarManager.endWar(any(), reasonCaptor.capture())).then(inv -> null);

			Optional<WarEndReason> result = WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());

			assertEquals(WarEndReason.WHITE_PEACE, result.orElse(null));
			assertEquals(WarEndReason.WHITE_PEACE, reasonCaptor.getValue());
		}
	}

	@Test
	void evaluateAndMaybeEnd_idlePhaseDoesNotStalemate() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.IDLE);

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
				MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.isValidWar(war)).thenReturn(true);
			capability.when(() -> CampaignCapabilityService.nextBattleProvince(war))
					.thenReturn(java.util.OptionalInt.of(20));
			capability.when(() -> CampaignCapabilityService.canMountOffensive(
					war, CampaignCoalition.AGGRESSOR, 20)).thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canMountOffensive(
					war, CampaignCoalition.DEFENDER, 20)).thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(true);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(true);
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);

			Optional<WarEndReason> result = WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());

			assertTrue(result.isEmpty());
			warManager.verifyNoInteractions();
		}
	}

	@Test
	void evaluateAndMaybeEnd_choicePendingDoesNotStalemate() {
		War war = baseWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
				MockedStatic<CampaignCapabilityService> capability = mockStatic(CampaignCapabilityService.class)) {
			capability.when(() -> CampaignCapabilityService.isValidWar(war)).thenReturn(true);
			capability.when(() -> CampaignCapabilityService.nextBattleProvince(war))
					.thenReturn(java.util.OptionalInt.of(20));
			capability.when(() -> CampaignCapabilityService.canMountOffensive(
					war, CampaignCoalition.AGGRESSOR, 20)).thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canMountOffensive(
					war, CampaignCoalition.DEFENDER, 20)).thenReturn(false);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR))
					.thenReturn(true);
			capability.when(() -> CampaignCapabilityService.canReachTarget(war, CampaignCoalition.DEFENDER))
					.thenReturn(true);
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);

			Optional<WarEndReason> result = WarResolutionService.evaluateAndMaybeEnd(war, ResolutionContext.none());

			assertTrue(result.isEmpty());
			warManager.verifyNoInteractions();
		}
	}

	@Test
	void tryEndAfterBattle_aggressorWinsAtDefenderCapital() {
		assertVictoryEndsWar(30, CampaignCoalition.AGGRESSOR, WarEndReason.ATTACKER_VICTORY);
	}

	@Test
	void tryEndAfterBattle_defenderWinsAtDefenderCapitalContinues() {
		War war = baseWar();
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);
			assertTrue(WarResolutionService.tryEndAfterBattle(
					war, 30, CampaignCoalition.DEFENDER, war.getPushTarget(), war.getObjectiveHeldBy()).isEmpty());
		}
	}

	@Test
	void tryEndAfterBattle_defenderWinsAtAttackerCapital() {
		assertVictoryEndsWar(5, CampaignCoalition.DEFENDER, WarEndReason.DEFENDER_VICTORY);
	}

	@Test
	void tryEndAfterBattle_aggressorWinsAtAttackerCapitalContinues() {
		War war = baseWar();
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);
			assertTrue(WarResolutionService.tryEndAfterBattle(
					war, 5, CampaignCoalition.AGGRESSOR, war.getPushTarget(), war.getObjectiveHeldBy()).isEmpty());
		}
	}

	@Test
	void tryEndAfterBattle_failedRetakeEndsWar() {
		War war = baseWar();
		war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
		war.setObjectiveProvinceId(30);
		war.setObjectiveHeldBy(ObjectiveHolder.ATTACKER);

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			ArgumentCaptor<WarEndReason> reasonCaptor = ArgumentCaptor.forClass(WarEndReason.class);
			warManager.when(() -> WarManager.endWar(any(), reasonCaptor.capture())).then(inv -> null);

			Optional<WarEndReason> result = WarResolutionService.tryEndAfterBattle(
					war,
					30,
					CampaignCoalition.AGGRESSOR,
					CampaignPushTarget.RETAKE_OBJECTIVE,
					ObjectiveHolder.ATTACKER);

			assertEquals(WarEndReason.ATTACKER_VICTORY, result.orElse(null));
			assertEquals(WarEndReason.ATTACKER_VICTORY, reasonCaptor.getValue());
		}
	}

	@Test
	void tryEndAfterBattle_successfulRetakeContinues() {
		War war = baseWar();
		war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
		war.setObjectiveProvinceId(30);
		war.setObjectiveHeldBy(ObjectiveHolder.ATTACKER);

		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);
			assertTrue(WarResolutionService.tryEndAfterBattle(
					war,
					30,
					CampaignCoalition.DEFENDER,
					CampaignPushTarget.RETAKE_OBJECTIVE,
					ObjectiveHolder.ATTACKER).isEmpty());
		}
	}

	@Test
	void surrender_attackerLeaderYieldsDefenderVictory() {
		War war = baseWar();
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			ArgumentCaptor<WarEndReason> reasonCaptor = ArgumentCaptor.forClass(WarEndReason.class);
			warManager.when(() -> WarManager.endWar(any(), reasonCaptor.capture())).then(inv -> null);

			assertTrue(WarResolutionService.surrender(war, attacker));
			assertEquals(WarEndReason.DEFENDER_VICTORY, reasonCaptor.getValue());
		}
	}

	@Test
	void surrender_defenderLeaderYieldsAttackerVictory() {
		War war = baseWar();
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			ArgumentCaptor<WarEndReason> reasonCaptor = ArgumentCaptor.forClass(WarEndReason.class);
			warManager.when(() -> WarManager.endWar(any(), reasonCaptor.capture())).then(inv -> null);

			assertTrue(WarResolutionService.surrender(war, defender));
			assertEquals(WarEndReason.ATTACKER_VICTORY, reasonCaptor.getValue());
		}
	}

	@Test
	void surrender_rejectsNonWarLeader() {
		War war = baseWar();
		Faction outsider = mock(Faction.class);
		when(outsider.getId()).thenReturn("other");
		assertFalse(WarResolutionService.surrender(war, outsider));
	}

	@Test
	void tryEndAfterBattle_pillageAttackerWinAtSettlementEndsWar() {
		assertVictoryEndsWarOn(pillageWar(20), 20, CampaignCoalition.AGGRESSOR, WarEndReason.ATTACKER_VICTORY);
	}

	@Test
	void tryEndAfterBattle_pillageDefenderWinAtSettlementEndsWar() {
		assertVictoryEndsWarOn(pillageWar(20), 20, CampaignCoalition.DEFENDER, WarEndReason.DEFENDER_VICTORY);
	}

	private void assertVictoryEndsWar(int provinceId, CampaignCoalition winner, WarEndReason expected) {
		assertVictoryEndsWarOn(baseWar(), provinceId, winner, expected);
	}

	private void assertVictoryEndsWarOn(War war, int provinceId, CampaignCoalition winner, WarEndReason expected) {
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			ArgumentCaptor<WarEndReason> reasonCaptor = ArgumentCaptor.forClass(WarEndReason.class);
			warManager.when(() -> WarManager.endWar(any(), reasonCaptor.capture())).then(inv -> null);

			Optional<WarEndReason> result = WarResolutionService.tryEndAfterBattle(
					war,
					provinceId,
					winner,
					war.getPushTarget(),
					war.getObjectiveHeldBy());

			assertEquals(expected, result.orElse(null));
			assertEquals(expected, reasonCaptor.getValue());
		}
	}

	private War pillageWar(int objective) {
		War war = baseWar();
		war.setGoal(WarGoalType.PILLAGE);
		war.setWarType(WarType.PILLAGE);
		war.setObjectiveProvinceId(objective);
		return war;
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
}
