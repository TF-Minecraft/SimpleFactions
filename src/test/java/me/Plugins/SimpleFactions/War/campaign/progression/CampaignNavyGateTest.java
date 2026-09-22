package me.Plugins.SimpleFactions.War.campaign.progression;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignUiCopy;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.declare.WarValidationResult;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignNavyGateTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	@Test
	void validateDeclareAfterPopulate_rejectsNavalScheduleWithoutPort() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL, false);
		WarValidationResult result = CampaignNavyGate.validateDeclareAfterPopulate(war);
		assertFalse(result.isValid());
		assertEquals(CampaignUiCopy.navyBlockadeDeclareMessage(), result.getMessage());
	}

	@Test
	void validateDeclareAfterPopulate_allowsNavalScheduleWithEmptyPort() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL, true);
		assertTrue(CampaignNavyGate.validateDeclareAfterPopulate(war).isValid());
	}

	@Test
	void validateDeclareAfterPopulate_allowsFieldScheduleWithoutPort() {
		War war = warWithSchedule(CampaignBattleKind.FIELD, false);
		assertTrue(CampaignNavyGate.validateDeclareAfterPopulate(war).isValid());
	}

	@Test
	void validateDeclareAfterPopulate_rejectsNavalInvasionWithoutPort() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL_INVASION, false);
		assertFalse(CampaignNavyGate.validateDeclareAfterPopulate(war).isValid());
	}

	@Test
	void invasionRequiresNavy_ignoresCounterLeg() {
		War war = warWithSchedule(CampaignBattleKind.FIELD, false);
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.NAVAL, false, null)));
		assertFalse(CampaignNavyGate.invasionRequiresNavy(war));
		assertTrue(CampaignNavyGate.validateDeclareAfterPopulate(war).isValid());
	}

	@Test
	void winnerCanContestNextNaval_falseWithoutPortOnNavalSlot() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL, false);
		assertTrue(CampaignNavyGate.nextSlotRequiresNavy(war));
		assertFalse(CampaignNavyGate.winnerCanContestNextNaval(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void winnerCanContestNextNaval_trueWithPortOnNavalSlot() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL, true);
		assertTrue(CampaignNavyGate.winnerCanContestNextNaval(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void applyPushChoice_refusesWhenNavyBlocked() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL, false);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);
		war.setPostBattleWinnerCoalition(CampaignCoalition.AGGRESSOR);
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		try (MockedStatic<CampaignCapabilityService> capability =
						mockStatic(CampaignCapabilityService.class, CALLS_REAL_METHODS);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			capability.when(() -> CampaignCapabilityService.hasOffensiveArmy(any(), any(), anyInt()))
					.thenReturn(true);
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);
			assertFalse(CampaignPostBattleChoiceService.applyPushChoice(war));
			assertEquals(PostBattleChoicePhase.WINNER_PUSH_HOLD, war.getPostBattleChoicePhase());
		}
	}

	@Test
	void resolveMandatoryHoldIfNeeded_holdsWhenNavyBlocked() {
		War war = warWithSchedule(CampaignBattleKind.NAVAL, false);
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		try (MockedStatic<CampaignCapabilityService> capability =
						mockStatic(CampaignCapabilityService.class, CALLS_REAL_METHODS);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			capability.when(() -> CampaignCapabilityService.hasOffensiveArmy(any(), any(), anyInt()))
					.thenReturn(true);
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);
			assertTrue(CampaignPostBattleChoiceService.resolveMandatoryHoldIfNeeded(
					war, CampaignCoalition.AGGRESSOR));
			assertEquals(PostBattleChoicePhase.LOSER_ATTACK_PEACE, war.getPostBattleChoicePhase());
		}
	}

	@Test
	void validateDeclareAfterPopulate_pillageNaturalNavyRejectsFieldScheduleWithoutPort() {
		War war = warWithSchedule(CampaignBattleKind.FIELD, false);
		war.setGoal(WarGoalType.PILLAGE);
		war.setWarType(WarType.PILLAGE);
		war.setPillageNaturalNavyRequired(true);
		WarValidationResult result = CampaignNavyGate.validateDeclareAfterPopulate(war);
		assertFalse(result.isValid());
		assertEquals(CampaignUiCopy.navyBlockadeDeclareMessage(), result.getMessage());
	}

	@Test
	void applyDeadlineIfDue_holdsWhenNavyBlocked() {
		Cache.warDefenderChoiceDeadlineHour = 12;
		War war = warWithSchedule(CampaignBattleKind.NAVAL, false);
		war.setBattleDay(BATTLE_DAY);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);
		war.setPostBattleWinnerCoalition(CampaignCoalition.AGGRESSOR);
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		Instant now = BattleWindowService.atScheduleHour(BATTLE_DAY, 12);
		try (MockedStatic<CampaignCapabilityService> capability =
						mockStatic(CampaignCapabilityService.class, CALLS_REAL_METHODS);
				MockedStatic<WarManager> warManager = mockStatic(WarManager.class)) {
			capability.when(() -> CampaignCapabilityService.hasOffensiveArmy(any(), any(), anyInt()))
					.thenReturn(true);
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);
			assertTrue(CampaignPostBattleChoiceService.applyDeadlineIfDue(war, now));
			assertEquals(PostBattleChoicePhase.LOSER_ATTACK_PEACE, war.getPostBattleChoicePhase());
		}
	}

	private War warWithSchedule(CampaignBattleKind kind, boolean attackerHasPort) {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getCapital()).thenReturn(5);
		InstallationHandler attackerHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(attackerHandler.getAll()).thenReturn(attackerHasPort
				? List.of(new Installation("port-atk", "Harbour", InstallationKind.PORT, 5, 0, 0, 1L))
				: List.of());
		InstallationHandler defenderHandler = mock(InstallationHandler.class);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(defenderHandler.getAll()).thenReturn(List.of());

		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setCampaignBattlesFought(1);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setCampaignBattleSchedule(List.of(new ScheduledCampaignBattle(20, kind, false, null)));
		war.setCampaignScheduleIndex(0);
		return war;
	}
}
