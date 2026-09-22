package me.Plugins.SimpleFactions.War.campaign.raid.fight;



import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidService;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.raid.fight.CampaignRaidBossBarService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.engine.raid.RaidAttackerEliminationService;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.progression.CampaignCoalitionService.CampaignCoalition;
import me.Plugins.SimpleFactions.War.campaign.runtime.CampaignClock;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;
import me.Plugins.SimpleFactions.War.enums.WarType;

class CampaignRaidBossBarServiceTest {
	private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ATTACKER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@BeforeEach
	void setUp() {
		Cache.campaignRaidDurationSeconds = 600;
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		RaidAttackerEliminationService.resetForTests();
		CampaignRaidBossBarService.resetForTests();
		CampaignClock.reset();
		WarManager.get().clear();
	}

	@AfterEach
	void tearDown() {
		CampaignRaidBossBarService.resetForTests();
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		RaidAttackerEliminationService.resetForTests();
		WarManager.get().clear();
	}

	@Test
	void onFightStarted_createsTimeAndRaiderBars() {
		BossBar timeBar = mock(BossBar.class);
		BossBar raidersBar = mock(BossBar.class);
		Player attackerOne = mock(Player.class);
		Player attackerTwo = mock(Player.class);
		when(attackerOne.isOnline()).thenReturn(true);
		when(attackerTwo.isOnline()).thenReturn(true);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.eq("Fort Raid"),
					Mockito.eq(BarColor.BLUE),
					Mockito.eq(BarStyle.SOLID))).thenReturn(timeBar);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.contains("Raiders remaining"),
					Mockito.eq(BarColor.RED),
					Mockito.eq(BarStyle.SOLID))).thenReturn(raidersBar);
			bukkit.when(() -> Bukkit.getPlayer(ATTACKER)).thenReturn(attackerOne);
			bukkit.when(() -> Bukkit.getPlayer(ATTACKER_2)).thenReturn(attackerTwo);

			Battle battle = raidBattleWithAttackers(2);
			CampaignRaid raid = fightingRaid(battle, Instant.parse("2026-08-21T20:10:00Z"));

			CampaignRaidBossBarService.onFightStarted(battle, raid);

			Mockito.verify(timeBar).setVisible(true);
			Mockito.verify(raidersBar).setVisible(true);
			Mockito.verify(timeBar).setProgress(Mockito.anyDouble());
			Mockito.verify(raidersBar).setProgress(1.0);
		}
	}

	@Test
	void clear_removesBarsOnEnd() {
		BossBar timeBar = mock(BossBar.class);
		BossBar raidersBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.eq(BarColor.BLUE), Mockito.eq(BarStyle.SOLID)))
					.thenReturn(timeBar);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.eq(BarColor.RED), Mockito.eq(BarStyle.SOLID)))
					.thenReturn(raidersBar);

			Battle battle = raidBattleWithAttackers(1);
			CampaignRaid raid = fightingRaid(battle, Instant.parse("2026-08-21T20:10:00Z"));
			CampaignRaidBossBarService.onFightStarted(battle, raid);

			CampaignRaidBossBarService.clear(battle);

			Mockito.verify(timeBar).removeAll();
			Mockito.verify(raidersBar).removeAll();
			Mockito.verify(timeBar).setVisible(false);
			Mockito.verify(raidersBar).setVisible(false);
		}
	}

	@Test
	void tickCampaignRaid_clearsWhenRaidNoLongerFighting() {
		BossBar timeBar = mock(BossBar.class);
		BossBar raidersBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.eq(BarColor.BLUE), Mockito.eq(BarStyle.SOLID)))
					.thenReturn(timeBar);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.eq(BarColor.RED), Mockito.eq(BarStyle.SOLID)))
					.thenReturn(raidersBar);

			Battle battle = raidBattleWithAttackers(1);
			CampaignRaid raid = fightingRaid(battle, Instant.parse("2026-08-21T20:10:00Z"));
			War war = WarManager.getById(1);
			CampaignRaidBossBarService.onFightStarted(battle, raid);

			CampaignRaidService.endRaid(war, CampaignClock.now());
			CampaignRaidBossBarService.tickCampaignRaid(battle);

			Mockito.verify(timeBar).removeAll();
			Mockito.verify(raidersBar).removeAll();
		}
	}

	@Test
	void countActiveAttackers_excludesMarkedOut() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.eq(BarColor.BLUE), Mockito.eq(BarStyle.SOLID)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(
					Mockito.anyString(), Mockito.eq(BarColor.RED), Mockito.eq(BarStyle.SOLID)))
					.thenReturn(bossBar);

			Battle battle = raidBattleWithAttackers(2);
			RaidAttackerEliminationService.markOut(battle, ATTACKER);

			Player online = mock(Player.class);
			when(online.isOnline()).thenReturn(true);
			bukkit.when(() -> Bukkit.getPlayer(ATTACKER)).thenReturn(online);
			bukkit.when(() -> Bukkit.getPlayer(ATTACKER_2)).thenReturn(null);

			assertEquals(0, RaidAttackerEliminationService.countActiveAttackers(battle));
			assertTrue(RaidAttackerEliminationService.isAttackerSideEliminated(battle));
		}
	}

	private static Battle raidBattleWithAttackers(int attackerCount) {
		Faction attacker = mock(Faction.class);
		Faction defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");
		War war = new War(1, attacker, defender);
		war.setGoal(WarGoalType.SUBJUGATE);
		war.setWarType(WarType.SUBJUGATE);
		WarManager.addWar(war);

		Battle battle = BattleFactory.createBlank(BattleType.RAID, "fort_raid");
		battle.setCampaignRaid(true);
		battle.setWarId(1);
		battle.setDisplayName("Fort Raid");
		battle.setStarted(true);
		battle.setStartedAt(Instant.parse("2026-08-21T20:00:00Z"));

		Warband atk = Warband.createRaidShell("fort_raid_attacker", war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		if (attackerCount >= 1) {
			atk.addMember(ATTACKER);
		}
		if (attackerCount >= 2) {
			atk.addMember(ATTACKER_2);
		}
		Warband def = Warband.createRaidShell("fort_raid_defender", war.getDefenders(), BattleTemplate.DEFENDER_SIDE);
		battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(atk);
		battle.getSideById(BattleTemplate.DEFENDER_SIDE).addBand(def);
		BattleManager.addBattle(battle);
		WarbandManager.addWarband(atk);
		WarbandManager.addWarband(def);
		return battle;
	}

	private static CampaignRaid fightingRaid(Battle battle, Instant fightEndsAt) {
		CampaignRaid raid = new CampaignRaid();
		raid.setId("fort_raid");
		raid.setDisplayName("Fort Raid");
		raid.setWarId(1);
		raid.setAttackerCoalition(CampaignCoalition.AGGRESSOR);
		raid.setState(CampaignRaidState.FIGHTING);
		raid.setFightEndsAt(fightEndsAt);
		raid.setBattleId(battle.getId());
		War war = WarManager.getById(1);
		war.setActiveCampaignRaid(raid);
		return raid;
	}
}
