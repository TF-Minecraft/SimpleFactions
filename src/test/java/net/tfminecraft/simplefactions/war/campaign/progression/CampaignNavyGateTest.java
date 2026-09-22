package net.tfminecraft.simplefactions.war.campaign.progression;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignUiCopy;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.declare.WarValidationResult;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

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
