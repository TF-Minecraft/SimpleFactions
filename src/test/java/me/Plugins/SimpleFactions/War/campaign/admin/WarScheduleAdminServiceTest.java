package me.Plugins.SimpleFactions.War.campaign.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.campaign.progression.BelligerentRole;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService;
import me.Plugins.SimpleFactions.War.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import me.Plugins.SimpleFactions.War.battle.campaign.CampaignBattleOutcomeService;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.CampaignPhase;
import me.Plugins.SimpleFactions.War.enums.WarEndReason;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.vote.BattleQuorumService;

class WarScheduleAdminServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.battleCampaignTemplateField = "";
		Cache.warBattleWindowStartHour = 20;
		Cache.warBattleWindowEndHour = 24;
		Cache.warVoteCloseHour = 16;
		Cache.warDefenderChoiceDeadlineHour = 12;
		Cache.warBattleVotingMinPlayers = 4;
		Cache.warBattleVotingRequireSmallestSideFull = true;
		Cache.warBattleVotingPassIfEither = true;
		Cache.warBattleVotingDevMinPlayersEnabled = false;
		Cache.warFirstBattleAtBorder = true;
		Cache.warBattleLivesPerRegiment = 4;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of("Alice", "Bob"));
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave"));
		mockMilitary(attacker);
		mockMilitary(defender);
	}

	@Test
	void openVote_setsVotingAndClearsSchedule() {
		War war = scheduledWar();
		WarScheduleAdminResult result = WarScheduleAdminService.openVote(war);
		assertTrue(result.success());
		assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		assertFalse(war.isDefenderChoiceResolved());
	}

	@Test
	void skipDay_advancesBattleDayOnly() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.skipDay(war);
		assertTrue(result.success());
		assertEquals(BATTLE_DAY.plusDays(1), war.getBattleDay());
		assertEquals(0, war.getPostponementsThisCycle());
	}

	@Test
	void forceQuorum_setsFlag() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.forceQuorum(war);
		assertTrue(result.success());
		assertTrue(war.isForceQuorumNextClose());
	}

	@Test
	void castVote_addsSpoofSelectionsForAllEligibleMembers() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.castVote(war, 21, "both");
		assertTrue(result.success());
		assertEquals(4, BattleQuorumService.countDistinctVoters(war));
		assertTrue(result.message().contains("4 voters total"));
	}

	@Test
	void castVote_rejectsInvalidHour() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.castVote(war, 15, "both");
		assertFalse(result.success());
	}

	@Test
	void setScheduled_appliesInstantAndProvince() {
		War war = votingWar();
		withMockBossBar(() -> {
			WarScheduleAdminResult result = WarScheduleAdminService.setScheduled(
					war,
					"2026-08-21T19:00:00Z");
			assertTrue(result.success());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertEquals(
					BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21),
					war.getScheduledBattleAt());
			assertEquals(Integer.valueOf(20), war.getScheduledBattleProvinceId());
		});
	}

	@Test
	void setScheduled_rejectsInvalidIso() {
		War war = votingWar();
		WarScheduleAdminResult result = WarScheduleAdminService.setScheduled(war, "not-an-instant");
		assertFalse(result.success());
	}

	@Test
	void battleCreate_rejectsWhenNotSingleGreenProvince() {
		War war = votingWar();
		war.setInitiativeAttacker(0);
		WarScheduleAdminResult result = WarScheduleAdminService.battleCreate(war);
		assertFalse(result.success());
		assertTrue(result.message().contains("single next battle province"));
	}

	@Test
	void battleCreate_createsCampaignBattleFromGreenProvince() {
		War war = votingWar();
		withMockBossBar(() -> {
			WarScheduleAdminResult result = WarScheduleAdminService.battleCreate(war);
			assertTrue(result.success());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertNotNull(BattleManager.getByWarId(war.getId()));
		});
	}

	@Test
	void battleCreate_seedsPhantomsWhenDevmodeOn() {
		me.Plugins.SimpleFactions.War.core.WarDevMode.setEnabled(true);
		Cache.warDevmodePhantomCount = 10;
		War war = votingWar();
		try (MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
				mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			withMockBossBar(() -> {
				assertTrue(WarScheduleAdminService.battleCreate(war).success());
				Battle battle = BattleManager.getByWarId(war.getId());
				me.Plugins.SimpleFactions.War.battle.warband.Warband attackerBand =
						WarbandManager.getByString(
								me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService.campaignWarbandId(
										battle.getDisplayName(),
										me.Plugins.SimpleFactions.War.battle.template.BattleTemplate.ATTACKER_SIDE));
				me.Plugins.SimpleFactions.War.battle.warband.Warband defenderBand =
						WarbandManager.getByString(
								me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService.campaignWarbandId(
										battle.getDisplayName(),
										me.Plugins.SimpleFactions.War.battle.template.BattleTemplate.DEFENDER_SIDE));
				assertNotNull(attackerBand);
				assertNotNull(defenderBand);
				assertTrue(attackerBand.getDummyMemberCount() > 0);
				assertTrue(defenderBand.getDummyMemberCount() > 0);
				assertEquals(
						me.Plugins.SimpleFactions.War.core.WarDevMode.dummyDisplayName(
								attackerBand.getId(), 0),
						attackerBand.getLeaderDisplayName());
			});
		}
		me.Plugins.SimpleFactions.War.core.WarDevMode.resetForTests();
	}

	@Test
	void battleDelete_resetsCampaignBattleAndWarbands() {
		War war = votingWar();
		withMockBossBar(() -> {
			assertTrue(WarScheduleAdminService.battleCreate(war).success());
			Battle battle = BattleManager.getByWarId(war.getId());
			String attackerWarbandId = me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService.campaignWarbandId(
					battle.getDisplayName(),
					me.Plugins.SimpleFactions.War.battle.template.BattleTemplate.ATTACKER_SIDE);
			assertNotNull(WarbandManager.getByString(attackerWarbandId));

			WarScheduleAdminResult result = WarScheduleAdminService.battleDelete(war);
			assertTrue(result.success());
			assertTrue(result.message().contains("Reset campaign battle"));
			battle = BattleManager.getByWarId(war.getId());
			assertNotNull(battle);
			assertNotNull(WarbandManager.getByString(attackerWarbandId));
			assertEquals(1, battle.getSideById(
					me.Plugins.SimpleFactions.War.battle.template.BattleTemplate.ATTACKER_SIDE).getBands().size());
			assertEquals(1, battle.getSideById(
					me.Plugins.SimpleFactions.War.battle.template.BattleTemplate.DEFENDER_SIDE).getBands().size());
		});
	}

	@Test
	void devModeReminderLines_disabledWhenDevmodeOff() {
		me.Plugins.SimpleFactions.War.core.WarDevMode.resetForTests();
		assertEquals(1, WarScheduleAdminService.devModeReminderLines().size());
		assertTrue(WarScheduleAdminService.devModeReminderLines().get(0).contains("disabled"));
	}

	@Test
	void devModeReminderLines_emptyWhenDevmodeOn() {
		me.Plugins.SimpleFactions.War.core.WarDevMode.setEnabled(true);
		assertTrue(WarScheduleAdminService.devModeReminderLines().isEmpty());
		me.Plugins.SimpleFactions.War.core.WarDevMode.resetForTests();
	}

	@Test
	void battleDelete_rejectsStartedBattle() {
		War war = votingWar();
		try (MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
				mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			withMockBossBar(() -> {
				assertTrue(WarScheduleAdminService.battleCreate(war).success());
				assertTrue(WarScheduleAdminService.battleStart(war).success());
				WarScheduleAdminResult result = WarScheduleAdminService.battleDelete(war);
				assertFalse(result.success());
				assertTrue(result.message().contains("running"));
			});
		}
	}

	@Test
	void battleStart_startsExistingCampaignBattle() {
		War war = votingWar();
		try (MockedStatic<me.Plugins.SimpleFactions.War.battle.military.BattlePoolService> pool =
				mockStatic(me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.class)) {
			pool.when(() -> me.Plugins.SimpleFactions.War.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			withMockBossBar(() -> {
				assertTrue(WarScheduleAdminService.battleCreate(war).success());
				WarScheduleAdminResult result = WarScheduleAdminService.battleStart(war);
				assertTrue(result.success());
				assertTrue(BattleManager.getByWarId(war.getId()).hasStarted());
			});
		}
	}

	@Test
	void winBattle_reportsActualEndReasonWhenWarEnds() {
		War war = votingWar();
		try (MockedStatic<CampaignBattleOutcomeService> outcome =
				mockStatic(CampaignBattleOutcomeService.class)) {
			outcome.when(() -> CampaignBattleOutcomeService.applyCampaignBattleOutcome(
					eq(war), eq(BelligerentRole.ATTACKER), any(Integer.class)))
					.thenReturn(new CampaignBattleOutcomeService.CampaignBattleApplyResult(
							true, false, Optional.of(WarEndReason.ATTACKER_VICTORY)));
			outcome.when(() -> CampaignBattleOutcomeService.finalizeCampaignBattleAfterOutcome(war))
					.then(inv -> null);

			WarScheduleAdminResult result = WarScheduleAdminService.winBattle(war, BelligerentRole.ATTACKER);
			assertTrue(result.success());
			assertTrue(result.message().contains("attacker victory"));
			assertFalse(result.message().toLowerCase().contains("white peace"));
		}
	}

	@Test
	void winBattle_blockedWhilePostBattleChoicePending() {
		War war = votingWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleWinnerCoalition(CampaignCoalition.DEFENDER);
		war.setPostBattleChoiceResolved(false);

		WarScheduleAdminResult result = WarScheduleAdminService.winBattle(war, BelligerentRole.DEFENDER);
		assertFalse(result.success());
		assertTrue(result.message().contains("Post-battle choice pending"));
	}

	@Test
	void battleChoice_hold_proposesPeaceAndKeepsWinnerInitiative() {
		War war = votingWar();
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleWinnerCoalition(CampaignCoalition.DEFENDER);
		war.setPostBattleChoiceResolved(false);
		try (MockedStatic<me.Plugins.SimpleFactions.Managers.WarManager> warManager =
				mockStatic(me.Plugins.SimpleFactions.Managers.WarManager.class)) {
			warManager.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.persist(any())).then(inv -> null);
			warManager.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.getById(war.getId())).thenReturn(war);
			warManager.when(() -> me.Plugins.SimpleFactions.Managers.WarManager.endWar(any(), any())).then(inv -> null);

			WarScheduleAdminResult result = WarScheduleAdminService.battleChoice(war, "hold");
			assertTrue(result.success());
			assertEquals(BelligerentRole.DEFENDER, war.getInitiativeHolder());
			assertTrue(CampaignPostBattleChoiceService.needsLoserResponse(war));
		}
	}

	private War votingWar() {
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setObjectiveProvinceId(30);
		war.setCampaignStartProvinceId(20);
		war.setCampaignProvinces(List.of(5, 10, 20, 30));
		war.setCursorIndex(2);
		war.setInitiativeAttacker(4);
		war.setInitiativeDefender(4);
		war.setInitiativeHolder(BelligerentRole.ATTACKER);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	private War scheduledWar() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleAt(BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21));
		war.setScheduledBattleHour(21);
		war.setScheduledBattleProvinceId(20);
		return war;
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
		when(faction.getLeader()).thenReturn("leader");
		when(faction.getName()).thenReturn("faction");
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			action.run();
		}
	}
}
