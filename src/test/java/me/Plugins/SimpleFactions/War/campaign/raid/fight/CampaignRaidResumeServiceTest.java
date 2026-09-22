package me.Plugins.SimpleFactions.War.campaign.raid.fight;



import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidFightScheduler;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidResumeService;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBossBarService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.campaign.BattleNamingService;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.persistence.BattlePersistenceService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.BattleSchedulePhase;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignRaidResumeServiceTest {
	private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private Faction attacker;
	private Faction defender;
	private War war;
	private CampaignRaid raid;
	private BossBar sideBossBar;
	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		cleanPersistenceDirs();
		Cache.campaignRaidDurationSeconds = 600;
		CampaignClock.reset();
		CampaignRaidBossBarService.resetForTests();
		CampaignRaidFightScheduler.resetForTests();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarManager.get().clear();

		sideBossBar = mock(BossBar.class);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(sideBossBar);
		bukkit.when(() -> Bukkit.createBossBar(
				anyString(), any(BarColor.class), any(BarStyle.class), any()))
				.thenReturn(sideBossBar);

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		FactionManager.factions.add(attacker);
		FactionManager.factions.add(defender);

		war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		war.setBattleDay(LocalDate.of(2026, 8, 21));
		war.setBattleSchedulePhase(BattleSchedulePhase.VOTING);
		war.setOccupiedByAttacker(new ArrayList<>());
		war.setOccupiedByDefender(new ArrayList<>());
		WarManager.addWar(war);

		raid = new CampaignRaid();
		raid.setId("def_port_raid");
		raid.setDisplayName("Def Port Raid");
		raid.setWarId(1);
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		raid.setState(CampaignRaidState.FIGHTING);
		raid.setFightEndsAt(Instant.parse("2026-08-21T20:10:00Z"));
		raid.setBattleId("def_port_raid");
		war.setActiveCampaignRaid(raid);
	}

	@AfterEach
	void tearDown() {
		if (bukkit != null) {
			bukkit.close();
		}
		cleanPersistenceDirs();
		FactionManager.factions.remove(attacker);
		FactionManager.factions.remove(defender);
		WarManager.get().clear();
		CampaignRaidBossBarService.resetForTests();
		CampaignRaidFightScheduler.resetForTests();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
	}

	@Test
	void applyLoadedBattle_linksActiveRaidAndRestoresStartedAt() {
		Battle battle = startedRaidBattle(false);
		battle.setStartedAt(null);

		CampaignRaidResumeService.applyLoadedBattle(battle);

		assertTrue(battle.isCampaignRaid());
		assertEquals(Instant.parse("2026-08-21T20:00:00Z"), battle.getStartedAt());
	}

	@Test
	void resumeAll_restoresRaidBossBarsAfterReload() {
		BossBar timeBar = mock(BossBar.class);
		BossBar raidersBar = mock(BossBar.class);
		Instant fightStart = Instant.now().minusSeconds(300);
		Instant fightEnd = fightStart.plusSeconds(600);
		raid.setFightEndsAt(fightEnd);

		Battle battle = startedRaidBattle(false);
		battle.setStartedAt(fightStart);
		Warband atk = battle.getSideById(BattleTemplate.ATTACKER_SIDE).getBands().get(0);
		WarbandManager.addWarband(atk);
		Warband def = battle.getSideById(BattleTemplate.DEFENDER_SIDE).getBands().get(0);
		WarbandManager.addWarband(def);
		BattleManager.addBattle(battle);

		Player attackerPlayer = mock(Player.class);
		when(attackerPlayer.isOnline()).thenReturn(true);
		bukkit.when(() -> Bukkit.createBossBar(
				eq("Def Port Raid"),
				eq(BarColor.BLUE),
				eq(BarStyle.SOLID))).thenReturn(timeBar);
		bukkit.when(() -> Bukkit.createBossBar(
				contains("Raiders remaining"),
				eq(BarColor.RED),
				eq(BarStyle.SOLID))).thenReturn(raidersBar);
		bukkit.when(() -> Bukkit.getPlayer(ATTACKER)).thenReturn(attackerPlayer);

		CampaignRaidResumeService.resumeAll();

		assertTrue(battle.isCampaignRaid());
		verify(timeBar).setTitle(contains("Def Port Raid"));
		verify(raidersBar).setTitle(eq("Raiders remaining: 1"));
	}

	@Test
	void loadAll_roundTripsCampaignRaidFlagAndResumesBossBars() {
		BossBar timeBar = mock(BossBar.class);
		BossBar raidersBar = mock(BossBar.class);
		Instant fightStart = Instant.now().minusSeconds(300);
		Instant fightEnd = fightStart.plusSeconds(600);
		raid.setFightEndsAt(fightEnd);

		Battle battle = startedRaidBattle(true);
		battle.setStartedAt(fightStart);
		Warband atk = Warband.createRaidShell(
				BattleNamingService.campaignWarbandId(raid.getDisplayName(), BattleTemplate.ATTACKER_SIDE),
				war.getAttackers(),
				BattleTemplate.ATTACKER_SIDE);
		atk.addMember(ATTACKER);
		WarbandManager.addWarband(atk);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(atk);
		BattleManager.addBattle(battle);

		bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
				.thenReturn(timeBar, raidersBar);

		BattlePersistenceService.saveAll();

		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		CampaignRaidBossBarService.resetForTests();

		BattlePersistenceService.loadAll();

		Battle loaded = BattleManager.getByString("def_port_raid");
		assertNotNull(loaded);
		assertTrue(loaded.isCampaignRaid());
		assertEquals(fightStart, loaded.getStartedAt());
	}

	private static void cleanPersistenceDirs() {
		deleteIfExists(new File("plugins/SimpleFactions/Battles"));
		deleteIfExists(new File("plugins/SimpleFactions/Warbands"));
	}

	private static void deleteIfExists(File dir) {
		if (!dir.exists()) {
			return;
		}
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				file.delete();
			}
		}
	}

	private Battle startedRaidBattle(boolean campaignRaidFlag) {
		Battle battle = BattleFactory.createBlank(BattleType.RAID, "def_port_raid");
		battle.setWarId(1);
		battle.setDisplayName("Def Port Raid");
		battle.setCampaignRaid(campaignRaidFlag);
		battle.setStarted(true);
		Warband atk = Warband.createRaidShell(
				BattleNamingService.campaignWarbandId(raid.getDisplayName(), BattleTemplate.ATTACKER_SIDE),
				war.getAttackers(),
				BattleTemplate.ATTACKER_SIDE);
		atk.addMember(ATTACKER);
		Warband def = Warband.createRaidShell(
				BattleNamingService.campaignWarbandId(raid.getDisplayName(), BattleTemplate.DEFENDER_SIDE),
				war.getDefenders(),
				BattleTemplate.DEFENDER_SIDE);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(atk);
		battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(def);
		return battle;
	}
}
