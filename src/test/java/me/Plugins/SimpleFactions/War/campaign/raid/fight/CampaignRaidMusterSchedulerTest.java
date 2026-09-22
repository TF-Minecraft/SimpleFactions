package me.Plugins.SimpleFactions.War.campaign.raid.fight;



import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidFightScheduler;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidMusterScheduler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Loaders.BattleTemplateLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class CampaignRaidMusterSchedulerTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private Instant raidWindow;

	@BeforeEach
	void setUp() {
		CampaignClock.reset();
		Cache.warVoteCloseHour = 16;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
		Cache.campaignRaidMusterSeconds = 60;
		Cache.campaignRaidDurationSeconds = 600;
		Cache.worldName = "world";
		Cache.battleCampaignTemplateRaid = "campaign_raid_template";
		CampaignRaidMusterScheduler.resetForTests();
		CampaignRaidFightScheduler.resetForTests();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		BattleTemplateLoader.resetForTests();

		YamlConfiguration raidTemplateConfig = new YamlConfiguration();
		raidTemplateConfig.set("type", "raid");
		raidTemplateConfig.set("defender_respawn_mode", "infinite");
		raidTemplateConfig.set("campaign_raid", true);
		BattleTemplateLoader.putForTests(
				new me.Plugins.SimpleFactions.War.battle.template.BattleTemplate(
						"campaign_raid_template", raidTemplateConfig));

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");

		Installation atkPort = new Installation("port-atk", "Atk Port", InstallationKind.PORT, 10, 100, 100, 0L);
		Installation defPort = new Installation("port-def", "Def Port", InstallationKind.PORT, 20, 200, 200, 0L);
		InstallationHandler attackerHandler = mock(InstallationHandler.class);
		InstallationHandler defenderHandler = mock(InstallationHandler.class);
		when(attacker.getInstallationHandler()).thenReturn(attackerHandler);
		when(defender.getInstallationHandler()).thenReturn(defenderHandler);
		when(attackerHandler.getById("port-atk")).thenReturn(atkPort);
		when(defenderHandler.getById("port-def")).thenReturn(defPort);

		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(BATTLE_DAY);
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		WarManager.addWar(war);

		raidWindow = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);
		CampaignRaidService.beginMuster(war, attacker, "port-atk", "port-def", raidWindow);
	}

	@AfterEach
	void tearDown() {
		CampaignClock.reset();
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		WarManager.get().clear();
		CampaignRaidMusterScheduler.resetForTests();
		CampaignRaidFightScheduler.resetForTests();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		BattleTemplateLoader.resetForTests();
	}

	@Test
	void onMusterStarted_whenSpoofed_doesNotSchedule() {
		CampaignClock.add(Duration.ofHours(1));
		CampaignRaidMusterScheduler.onMusterStarted(war, raidWindow);

		Instant musterEnd = raidWindow.plusSeconds(60);
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		when(world.getHighestBlockYAt(100, 100)).thenReturn(64);
		when(world.getHighestBlockYAt(200, 200)).thenReturn(64);
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> Bukkit.createBossBar(
					org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class))).thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(
					org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class),
					org.mockito.ArgumentMatchers.any())).thenReturn(bossBar);

			assertTrue(CampaignRaidMusterScheduler.processOverdue(war, musterEnd));
		}

		assertEquals(CampaignRaidState.FIGHTING, CampaignRaidService.getActive(war).getState());
	}

	@Test
	void processOverdue_withSpoofedClock_noDoubleFire() {
		CampaignClock.add(Duration.ofHours(1));
		CampaignRaidMusterScheduler.onMusterStarted(war, raidWindow);

		Instant musterEnd = raidWindow.plusSeconds(60);
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		when(world.getHighestBlockYAt(100, 100)).thenReturn(64);
		when(world.getHighestBlockYAt(200, 200)).thenReturn(64);
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> Bukkit.createBossBar(
					org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class))).thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(
					org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class),
					org.mockito.ArgumentMatchers.any())).thenReturn(bossBar);

			assertTrue(CampaignRaidMusterScheduler.processOverdue(war, musterEnd));
			assertFalse(CampaignRaidMusterScheduler.processOverdue(war, musterEnd));
		}

		assertEquals(CampaignRaidState.FIGHTING, CampaignRaidService.getActive(war).getState());
	}

	@Test
	void processOverdue_startsFightWithNoJoiners() {
		Instant musterEnd = raidWindow.plusSeconds(60);
		assertFalse(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));

		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		when(world.getHighestBlockYAt(100, 100)).thenReturn(64);
		when(world.getHighestBlockYAt(200, 200)).thenReturn(64);
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> Bukkit.createBossBar(
					org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class))).thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(
					org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class),
					org.mockito.ArgumentMatchers.any())).thenReturn(bossBar);

			assertTrue(CampaignRaidMusterScheduler.processOverdue(war, musterEnd));
		}

		CampaignRaid raid = CampaignRaidService.getActive(war);
		assertNotNull(raid);
		assertEquals(CampaignRaidState.FIGHTING, raid.getState());
		assertTrue(CampaignRaidService.isSideQuotaUsed(war, CampaignCoalition.AGGRESSOR));
		assertTrue(raid.getMusterParticipantIds().isEmpty());
		assertFalse(CampaignRaidMusterScheduler.processOverdue(war, musterEnd));
	}

	@Test
	void processOverdue_noOpBeforeMusterEnds() {
		assertFalse(CampaignRaidMusterScheduler.processOverdue(war, raidWindow.plusSeconds(30)));
		assertEquals(CampaignRaidState.MUSTER, CampaignRaidService.getActive(war).getState());
	}
}
