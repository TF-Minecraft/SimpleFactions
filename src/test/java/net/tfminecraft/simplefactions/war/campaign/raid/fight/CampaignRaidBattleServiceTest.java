package net.tfminecraft.simplefactions.war.campaign.raid.fight;




import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidWarbandService;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaid;
import net.tfminecraft.simplefactions.war.campaign.raid.CampaignRaidService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidBattleService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidFightScheduler;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidBattleEndService;
import net.tfminecraft.simplefactions.war.campaign.raid.fight.CampaignRaidMusterScheduler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

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

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.loaders.BattleTemplateLoader;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.managers.WarManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleManager;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.events.BattleEndedEvent;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;
import net.tfminecraft.simplefactions.war.battle.warband.Warband;
import net.tfminecraft.simplefactions.war.battle.warband.WarbandManager;
import net.tfminecraft.simplefactions.war.campaign.runtime.BattleWindowService;
import net.tfminecraft.simplefactions.war.core.War;
import net.tfminecraft.simplefactions.war.enums.BattleSchedulePhase;
import net.tfminecraft.simplefactions.war.enums.WarGoalType;
import net.tfminecraft.simplefactions.war.enums.WarType;
import net.tfminecraft.simplefactions.installation.Installation;
import net.tfminecraft.simplefactions.installation.InstallationKind;
import net.tfminecraft.simplefactions.installation.handler.InstallationHandler;

class CampaignRaidBattleServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);

	private Faction attacker;
	private Faction defender;
	private War war;
	private CampaignRaid raid;
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
		WarbandManager.resetForTests();
		BattleManager.resetForTests();
		CampaignRaidMusterScheduler.resetForTests();
		CampaignRaidFightScheduler.resetForTests();
		BattleTemplateLoader.resetForTests();

		YamlConfiguration raidTemplateConfig = new YamlConfiguration();
		raidTemplateConfig.set("type", "raid");
		raidTemplateConfig.set("defender_respawn_mode", "infinite");
		raidTemplateConfig.set("keep_inventory", true);
		raidTemplateConfig.set("campaign_raid", true);
		BattleTemplateLoader.putForTests(
				new net.tfminecraft.simplefactions.war.battle.template.BattleTemplate(
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
		raid = CampaignRaidService.getActive(war);

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
		WarbandManager.resetForTests();
		BattleManager.resetForTests();
		CampaignRaidMusterScheduler.resetForTests();
		CampaignRaidFightScheduler.resetForTests();
		BattleTemplateLoader.resetForTests();
	}

	@Test
	void createAndStart_buildsCampaignRaidBattleWithoutCapturePoints() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			mockBukkit(bukkit, bossBar);
			Instant fightStart = raidWindow.plusSeconds(60);
			CampaignRaidService.transitionToFighting(war, fightStart);
			raid = CampaignRaidService.getActive(war);

			Battle battle = CampaignRaidBattleService.createAndStart(war, raid, fightStart);
			assertNotNull(battle);
			assertTrue(battle.isCampaignRaid());
			assertTrue(battle.hasStarted());
			assertTrue(battle.getPoints().isEmpty());
			assertEquals("def_port_raid", battle.getId());
			assertEquals("def_port_raid", raid.getBattleId());
			assertEquals("Def Port Raid", battle.getDisplayName());

			Warband atk = CampaignRaidWarbandService.getAttackerWarband(raid);
			assertNotNull(atk);
			assertTrue(battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().contains(atk));
			Warband def = CampaignRaidWarbandService.getDefenderWarband(raid);
			assertNotNull(def);
			assertTrue(battle.getSideById(BattleTemplate.DEFENDER_SIDE).getBands().contains(def));
		}
	}

	@Test
	void fightScheduler_timerEndsBattleWithNoWinner() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			mockBukkit(bukkit, bossBar);
			Instant fightStart = raidWindow.plusSeconds(60);
			CampaignRaidService.transitionToFighting(war, fightStart);
			raid = CampaignRaidService.getActive(war);
			CampaignRaidBattleService.createAndStart(war, raid, fightStart);

			Instant afterTimer = fightStart.plusSeconds(Cache.campaignRaidDurationSeconds + 1);
			CampaignRaidFightScheduler.onFightEnd(war, afterTimer);

			CampaignRaidBattleEndService.handleBattleEnded(
					new BattleEndedEvent(
							raid.getBattleId(),
							BattleType.RAID,
							1,
							null,
							Map.of(),
							Set.of(),
							BattleEndReason.TIMER,
							true));

			assertNull(CampaignRaidService.getActive(war));
			assertTrue(BattleManager.get().isEmpty());
		}
	}

	@Test
	void getByWarId_skipsCampaignRaidBattle() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			mockBukkit(bukkit, bossBar);
			Instant fightStart = raidWindow.plusSeconds(60);
			CampaignRaidService.transitionToFighting(war, fightStart);
			raid = CampaignRaidService.getActive(war);
			CampaignRaidBattleService.createAndStart(war, raid, fightStart);

			Battle campaignField = new Battle("campaign_w1_p20");
			campaignField.setWarId(1);
			campaignField.setBattleType(BattleType.FIELD);
			BattleManager.addBattle(campaignField);

			assertEquals(campaignField, BattleManager.getByWarId(1));
		}
	}

	private void mockBukkit(MockedStatic<Bukkit> bukkit, BossBar bossBar) {
		PluginManager pluginManager = mock(PluginManager.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
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
	}
}
