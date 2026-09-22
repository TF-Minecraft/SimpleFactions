package me.Plugins.SimpleFactions.War.campaign.ui;



import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCapabilityService;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushProjection;
import me.Plugins.SimpleFactions.War.campaign.ui.CampaignPushTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.campaign.schedule.ScheduledCampaignBattle;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.CampaignBattleKind;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.ObjectiveHolder;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignPushProjectionTest {
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
	void canMountOffensiveAfterPush_falseWhenWinnerLacksFuel() {
		War war = baseWar();
		war.setInitiativeAttacker(0);
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		assertFalse(CampaignPushProjection.canMountOffensiveAfterPush(war, CampaignCoalition.AGGRESSOR));
	}

	@Test
	void canMountOffensiveAfterPush_falseWhenNextSlotIsNavalWithoutPort() {
		InstallationHandler attackerHandler = mock(InstallationHandler.class);
		InstallationHandler defenderHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(attackerHandler.getAll()).thenReturn(List.of());
		when(defenderHandler.getAll()).thenReturn(List.of());
		War war = baseWar();
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		war.setCampaignBattleSchedule(List.of(
				new ScheduledCampaignBattle(20, CampaignBattleKind.NAVAL, false, null)));
		war.setCampaignScheduleIndex(0);
		try (MockedStatic<CampaignCapabilityService> capability =
				mockStatic(CampaignCapabilityService.class, CALLS_REAL_METHODS)) {
			capability.when(() -> CampaignCapabilityService.hasOffensiveArmy(any(), any(), anyInt()))
					.thenReturn(true);
			assertFalse(CampaignPushProjection.canMountOffensiveAfterPush(war, CampaignCoalition.AGGRESSOR));
		}
	}

	@Test
	void canMountOffensiveAfterPush_projectsCursorAdvanceOnOffensiveWin() {
		War war = baseWar();
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		var projected = CampaignPushProjection.afterPush(war, CampaignCoalition.AGGRESSOR);
		assertEquals(3, projected.cursorIndex());
	}

	private War baseWar() {
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
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		return war;
	}
}
