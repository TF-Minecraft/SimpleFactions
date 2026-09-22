package net.tfminecraft.simplefactions.war.campaign.runtime;



import net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.tfminecraft.simplefactions.army.Military;
import net.tfminecraft.simplefactions.army.Regiment;
import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.SimpleFactions;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.battle.campaign.CampaignBattleLaunchService;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignPostBattleChoiceService.PostBattleChoicePhase;
import net.tfminecraft.simplefactions.war.enums.CampaignPhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;

class BattleScheduleTickServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private static Instant voteCloseInstant() {
		return BattleWindowService.atScheduleHour(BATTLE_DAY, 16);
	}

	private Faction attacker;
	private Faction defender;

	private SimpleFactions pluginBackup;

	@BeforeEach
	void setUp() {
		pluginBackup = SimpleFactions.plugin;
		CampaignClock.resetForTests();
		BattleScheduleTickService.resetHourGateForTests();
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

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getMembers()).thenReturn(List.of("Alice", "Bob"));
		when(defender.getMembers()).thenReturn(List.of("Carol", "Dave"));
		mockMilitary(attacker);
		mockMilitary(defender);
	}

	@AfterEach
	void tearDown() {
		CampaignClock.resetForTests();
		BattleScheduleTickService.resetHourGateForTests();
		WarManager.get().clear();
	}

	@AfterEach
	void restorePlugin() {
		SimpleFactions.plugin = pluginBackup;
	}

	@Test
	void onClockOffsetChanged_resetsHourGate() {
		Instant hourStart = Instant.parse("2026-08-21T12:00:00Z");
		assertTrue(BattleScheduleTickService.shouldRunForHour(hourStart));
		assertFalse(BattleScheduleTickService.shouldRunForHour(hourStart));
		BattleScheduleTickService.onClockOffsetChanged();
		assertTrue(BattleScheduleTickService.shouldRunForHour(hourStart));
	}

	@Test
	void tick_withSpoofedClock_closesVoteAtCloseHour() {
		War war = votingWar();
		addCrossSideVotes(war, 21);

		Instant voteClose = voteCloseInstant();
		CampaignClock.add(Duration.between(Instant.now(), voteClose));
		BattleScheduleTickService.onClockOffsetChanged();

		withMockBossBar(() -> {
			WarManager.addWar(war);
			BattleScheduleTickService.tick(CampaignClock.now());
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertEquals(
					BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21),
					war.getScheduledBattleAt());
		});
	}

	@Test
	void shouldRunForHour_onlyOncePerUtcHour() {
		assertTrue(BattleScheduleTickService.shouldRunForHour(Instant.parse("2026-08-21T12:00:00Z")));
		assertFalse(BattleScheduleTickService.shouldRunForHour(Instant.parse("2026-08-21T12:30:00Z")));
		assertTrue(BattleScheduleTickService.shouldRunForHour(Instant.parse("2026-08-21T13:00:00Z")));
	}

	@Test
	void processWar_appliesPostBattleChoiceDeadlineAtNoon() {
		War war = defenderChoiceWar();
		withMockBossBarAndPools(() -> {
			assertTrue(BattleScheduleTickService.processWar(
					war, BattleWindowService.atScheduleHour(BATTLE_DAY, 12)));
			assertTrue(war.isPostBattleChoiceResolved());
			assertEquals(BattleSchedulePhase.VOTING, war.getBattleSchedulePhase());
		});
	}

	@Test
	void processWar_closesVoteAtSixteenWhenQuorumMet() {
		War war = votingWar();
		addCrossSideVotes(war, 21);

		withMockBossBar(() -> {
			assertTrue(BattleScheduleTickService.processWar(
					war, voteCloseInstant()));
			assertEquals(BattleSchedulePhase.SCHEDULED, war.getBattleSchedulePhase());
			assertEquals(
					BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21),
					war.getScheduledBattleAt());
		});
	}

	@Test
	void processWar_skipsNonVotingPhase() {
		War war = votingWar();
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);

		assertFalse(BattleScheduleTickService.processWar(
				war, voteCloseInstant()));
	}

	@Test
	void tick_startsScheduledBattleWhenDue() {
		War war = scheduledWar();

		try (MockedStatic<net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService> forfeit =
				mockStatic(net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService> fieldWin =
						mockStatic(net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService.class)) {
			forfeit.when(() -> net.tfminecraft.simplefactions.war.campaign.progression.postbattle.CampaignOffensiveForfeitService
					.applyIfBattleOffensiveCannotAttack(any(), any(Integer.class))).thenReturn(false);
			fieldWin.when(() -> net.tfminecraft.simplefactions.war.battle.engine.win.FieldWinService.checkFieldWin(any()))
					.then(inv -> null);
			withMockBossBar(() -> {
				SimpleFactions plugin = mock(SimpleFactions.class);
				when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
				SimpleFactions.plugin = plugin;
				WarManager.get().add(war);
				CampaignBattleLaunchService.prepareScheduledBattle(war);
				BattleScheduleTickService.tick(war.getScheduledBattleAt());
				assertTrue(BattleManager.getByWarId(war.getId()).hasStarted());
			});
		}
	}

	private War scheduledWar() {
		War war = votingWar();
		war.setInitiativeHolderCoalition(CampaignCoalition.AGGRESSOR);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.NONE);
		war.setPostBattleChoiceResolved(true);
		war.setBattleSchedulePhase(BattleSchedulePhase.SCHEDULED);
		war.setScheduledBattleHour(21);
		war.setScheduledBattleAt(BattleWindowService.computeScheduledBattleAt(BATTLE_DAY, 21));
		war.setScheduledBattleProvinceId(20);
		return war;
	}

	private void withMocksForChoiceResolution(War war, Runnable runnable) {
		try (MockedStatic<WarManager> warManager = mockStatic(WarManager.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattlePoolService> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.class)) {
			warManager.when(() -> WarManager.persist(any())).then(inv -> null);
			warManager.when(() -> WarManager.getById(war.getId())).thenReturn(war);
			warManager.when(() -> WarManager.endWar(any(), any())).then(inv -> null);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any(), any())).thenReturn(5);
			runnable.run();
		}
	}

	private void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		PluginManager pluginManager = mock(PluginManager.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			action.run();
		}
	}

	private void withMockBossBarAndPools(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<net.tfminecraft.simplefactions.war.battle.military.BattlePoolService> pool =
						mockStatic(net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any())).thenReturn(5);
			pool.when(() -> net.tfminecraft.simplefactions.war.battle.military.BattlePoolService.totalCommittedRegiments(
					any(), any(Integer.class), any(), any())).thenReturn(5);
			action.run();
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
		war.setInitiativeHolder(net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole.ATTACKER);
		war.setCampaignPhase(CampaignPhase.INVASION);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		return war;
	}

	private War defenderChoiceWar() {
		War war = votingWar();
		war.setInitiativeHolder(net.tfminecraft.simplefactions.war.campaign.progression.BelligerentRole.ATTACKER);
		war.setPostBattleChoicePhase(PostBattleChoicePhase.WINNER_PUSH_HOLD);
		war.setPostBattleWinnerCoalition(CampaignCoalition.DEFENDER);
		war.setLastBattleOffensiveCoalition(CampaignCoalition.AGGRESSOR);
		war.setPostBattleChoiceResolved(false);
		return war;
	}

	private void addCrossSideVotes(War war, int hour) {
		war.getBattleVotes().put(BattleScheduleLookups.spoofMemberUuid("Alice"), Set.of(hour));
		war.getBattleVotes().put(BattleScheduleLookups.spoofMemberUuid("Bob"), Set.of(hour));
		war.getBattleVotes().put(BattleScheduleLookups.spoofMemberUuid("Carol"), Set.of(hour));
		war.getBattleVotes().put(BattleScheduleLookups.spoofMemberUuid("Dave"), Set.of(hour));
	}

	private void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		Regiment professional = mock(Regiment.class);
		when(professional.getId()).thenReturn("professional");
		when(professional.isLevy()).thenReturn(false);
		when(professional.isOffensive()).thenReturn(true);
		when(professional.getCurrentSlots()).thenReturn(10);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(military.getRegiments()).thenReturn(List.of(professional));
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
		when(faction.getLeader()).thenReturn("leader");
		when(faction.getName()).thenReturn("faction");
	}
}
