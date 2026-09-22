package me.Plugins.SimpleFactions.War.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.events.BattleEndedEvent;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBattleService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidWarbandService;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;
import me.Plugins.SimpleFactions.installation.Installation;
import me.Plugins.SimpleFactions.installation.InstallationKind;
import me.Plugins.SimpleFactions.installation.handler.InstallationHandler;

class WarCombatTeardownServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private Instant raidWindow;
	private World world;

	@BeforeEach
	void setUp() {
		Cache.worldName = "world";
		Cache.warVoteCloseHour = 16;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;
		Cache.campaignRaidMusterSeconds = 60;
		Cache.campaignRaidDurationSeconds = 600;
		Cache.battleCampaignTemplateRaid = "campaign_raid_template";
		WarManager.get().clear();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		BattleTemplateLoader.resetForTests();

		YamlConfiguration raidTemplateConfig = new YamlConfiguration();
		raidTemplateConfig.set("type", "raid");
		raidTemplateConfig.set("defender_respawn_mode", "infinite");
		raidTemplateConfig.set("keep_inventory", true);
		raidTemplateConfig.set("campaign_raid", true);
		BattleTemplateLoader.putForTests(new BattleTemplate("campaign_raid_template", raidTemplateConfig));

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
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

		world = mock(World.class);
		when(world.getName()).thenReturn("world");
		when(world.getHighestBlockYAt(100, 100)).thenReturn(64);
		when(world.getHighestBlockYAt(200, 200)).thenReturn(64);
	}

	@AfterEach
	void tearDown() {
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		WarManager.get().clear();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		BattleTemplateLoader.resetForTests();
	}

	@Test
	void teardownCombatForWar_endsStartedRaidBattleWithoutBattleEndedEvent() {
		BossBar bossBar = mock(BossBar.class);
		PluginManager pluginManager = mock(PluginManager.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);

			Instant fightStart = raidWindow.plusSeconds(60);
			CampaignRaidService.transitionToFighting(war, fightStart);
			CampaignRaid raid = CampaignRaidService.getActive(war);
			Battle battle = CampaignRaidBattleService.createAndStart(war, raid, fightStart);
			assertTrue(battle.hasStarted());

			WarCombatTeardownService.teardownCombatForWar(war);

			assertFalse(battle.hasStarted());
			assertTrue(BattleManager.get().isEmpty());
			assertNull(CampaignRaidWarbandService.getAttackerWarband(raid));
			assertNull(CampaignRaidWarbandService.getDefenderWarband(raid));
			verify(pluginManager, never()).callEvent(any(BattleEndedEvent.class));
		}
	}

	@Test
	void teardownCombatForWar_removesCampaignAndRaidBattles() {
		Battle campaignBattle = new Battle("campaign_w1_p20");
		campaignBattle.setWarId(1);
		campaignBattle.setBattleType(BattleType.FIELD);
		BattleManager.addBattle(campaignBattle);

		Battle raidBattle = new Battle("cr_battle_1_2026-08-21");
		raidBattle.setWarId(1);
		raidBattle.setBattleType(BattleType.RAID);
		raidBattle.setCampaignRaid(true);
		BattleManager.addBattle(raidBattle);

		assertTrue(BattleManager.getAllByWarId(1).size() >= 2);

		WarCombatTeardownService.teardownCombatForWar(war);

		assertTrue(BattleManager.getAllByWarId(1).isEmpty());
	}

	@Test
	void endWar_purgesOngoingRaid() {
		BossBar bossBar = mock(BossBar.class);
		PluginManager pluginManager = mock(PluginManager.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
			bukkit.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(any(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);

			Instant fightStart = raidWindow.plusSeconds(60);
			CampaignRaidService.transitionToFighting(war, fightStart);
			CampaignRaid raid = CampaignRaidService.getActive(war);
			CampaignRaidBattleService.createAndStart(war, raid, fightStart);
			assertTrue(BattleManager.get().stream().anyMatch(Battle::hasStarted));

			WarManager.endWar(war);

			assertNull(CampaignRaidService.getActive(war));
			assertTrue(BattleManager.get().isEmpty());
			assertFalse(war.isActive());
		}
	}
}
