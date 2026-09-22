package net.tfminecraft.simplefactions.war.campaign.progression;



import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.core.Side;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.military.BattlePoolService;
import net.tfminecraft.simplefactions.war.battle.military.PoolMode;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.ObjectiveHolder;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.war.campaign.schedule.CampaignScheduleService;
import net.tfminecraft.simplefactions.war.campaign.schedule.ScheduledCampaignBattle;
import net.tfminecraft.simplefactions.war.enums.CampaignBattleKind;

class CampaignCapabilityServiceTest {
	private static final int BATTLE_PROVINCE = 20;
	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		Cache.warFirstBattleAtBorder = true;
		Cache.warProvincesBetweenBattles = 1;
		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getCapital()).thenReturn(5);
	}

	@Test
	void stepsToCapitulationTarget_countsAxisDistance() {
		War war = baseWar();
		assertEquals(1, CampaignCapabilityService.stepsToCapitulationTarget(war, CampaignCoalition.AGGRESSOR));
		assertEquals(2, CampaignCapabilityService.stepsToCapitulationTarget(war, CampaignCoalition.DEFENDER));
	}

	@Test
	void battleOffensiveCoalition_followsInitiativeHolder() {
		War war = baseWar();
		assertEquals(CampaignCoalition.AGGRESSOR, CampaignCapabilityService.battleOffensiveCoalition(war));
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		assertEquals(CampaignCoalition.DEFENDER, CampaignCapabilityService.battleOffensiveCoalition(war));
	}

	@Test
	void nextBattleProvince_scheduleDriven_returnsSlotsInOrder() {
		War war = baseWar();
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(18, CampaignBattleKind.SIEGE, false, "fort_a")));
		war.setCampaignScheduleIndex(0);

		assertEquals(20, CampaignCapabilityService.nextBattleProvince(war).getAsInt());

		CampaignScheduleService.advanceIndex(war);

		assertEquals(18, CampaignCapabilityService.nextBattleProvince(war).getAsInt());
	}

	@Test
	void nextBattleProvince_matchesBorderFirstBattleFixture() {
		War war = baseWar();
		assertEquals(BATTLE_PROVINCE, CampaignCapabilityService.nextBattleProvince(war).getAsInt());
	}

	@Test
	void nextBattleProvince_afterFirstBattle_usesCadenceSpacing() {
		War war = baseWar();
		war.setCampaignBattlesFought(1);
		war.setCursorIndex(2);
		assertEquals(30, CampaignCapabilityService.nextBattleProvince(war).getAsInt());
	}

	@Test
	void nextBattleProvince_counterPush_returnsProvinceLeftOfCursor() {
		War war = baseWar();
		war.setCampaignBattlesFought(1);
		war.setCursorIndex(2);
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		assertEquals(10, CampaignCapabilityService.nextBattleProvince(war).getAsInt());
	}

	@Test
	void nextBattleProvince_counterPush_usesCounterScheduleWhenPresent() {
		War war = baseWar();
		war.setCampaignCounterSchedule(List.of(
				new ScheduledCampaignBattle(10, CampaignBattleKind.FIELD, false, null),
				new ScheduledCampaignBattle(5, CampaignBattleKind.FIELD, true, null)));
		war.setInitiativeHolderCoalition(CampaignCoalition.DEFENDER);
		war.setPushTarget(CampaignPushTarget.TOWARD_AGGRESSOR_CAPITAL);
		assertEquals(10, CampaignCapabilityService.nextBattleProvince(war).getAsInt());
	}

	@Test
	void nextBattleProvince_retake_returnsObjectiveProvince() {
		War war = baseWar();
		war.setCampaignBattlesFought(2);
		war.setCursorIndex(3);
		war.setObjectiveHeldBy(ObjectiveHolder.ATTACKER);
		war.setPushTarget(CampaignPushTarget.RETAKE_OBJECTIVE);
		assertEquals(30, CampaignCapabilityService.nextBattleProvince(war).getAsInt());
	}

	@Test
	void nextBattleProvince_emptyWhileChoicePending() {
		War war = baseWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleChoiceResolved(false);
		assertFalse(CampaignCapabilityService.nextBattleProvince(war).isPresent());
	}

	@Test
	void canReachTarget_falseWhenFuelTooLow() {
		War war = baseWar();
		war.setInitiativeAttacker(0);
		assertFalse(CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void canReachTarget_trueWhenAtCapitulationTargetEvenWithNoFuel() {
		War war = baseWar();
		war.setCursorIndex(3);
		war.setInitiativeAttacker(0);
		assertEquals(0, CampaignCapabilityService.stepsToCapitulationTarget(war, CampaignCoalition.AGGRESSOR));
		assertTrue(CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void canReachTarget_doesNotRequireCommittedRegiments() {
		War war = baseWar();
		war.setInitiativeAttacker(4);
		assertTrue(CampaignCapabilityService.canReachTarget(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void offensiveAndDefensiveRegiments_delegateToBattlePoolService() {
		War war = baseWar();
		Side attackers = war.getAttackers();
		Side defenders = war.getDefenders();
		try (MockedStatic<BattlePoolService> pools = mockStatic(BattlePoolService.class)) {
			pools.when(() -> BattlePoolService.totalCommittedRegiments(
					eq(war), eq(BATTLE_PROVINCE), eq(attackers), eq(PoolMode.OFFENSIVE))).thenReturn(10);
			pools.when(() -> BattlePoolService.totalCommittedRegiments(
					eq(war), eq(BATTLE_PROVINCE), eq(defenders), eq(PoolMode.DEFENSIVE))).thenReturn(6);

			assertEquals(
					10,
					CampaignCapabilityService.offensiveRegiments(war, BATTLE_PROVINCE, CampaignCoalition.AGGRESSOR));
			assertEquals(
					6,
					CampaignCapabilityService.defensiveRegiments(war, BATTLE_PROVINCE, CampaignCoalition.DEFENDER));
			pools.verify(() -> BattlePoolService.totalCommittedRegiments(
					eq(war), eq(BATTLE_PROVINCE), eq(attackers), eq(PoolMode.OFFENSIVE)));
			pools.verify(() -> BattlePoolService.totalCommittedRegiments(
					eq(war), eq(BATTLE_PROVINCE), eq(defenders), eq(PoolMode.DEFENSIVE)));
		}
	}

	private War baseWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPushTarget(CampaignPushTarget.TOWARD_OBJECTIVE);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setObjectiveHeldBy(ObjectiveHolder.DEFENDER);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		return war;
	}
}
