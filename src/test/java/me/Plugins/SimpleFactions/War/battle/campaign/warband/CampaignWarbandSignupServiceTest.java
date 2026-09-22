package me.Plugins.SimpleFactions.War.battle.campaign.warband;


import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandSignupService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaid;
import me.Plugins.SimpleFactions.War.campaign.raid.CampaignRaidState;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;
import me.Plugins.SimpleFactions.War.core.War;

class CampaignWarbandSignupServiceTest {
	private static final LocalDate BATTLE_DAY = LocalDate.of(2026, 8, 21);
	private static final int PROVINCE_ID = 20;

	private Faction attacker;
	private Faction defender;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		WarDevMode.resetForTests();
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleMinSideLives = 1;
		Cache.warRaidWindowStartHour = 19;
		Cache.warRaidWindowEndHour = 20;

		attacker = mock(Faction.class);
		defender = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(defender.getId()).thenReturn("def");
		when(attacker.getName()).thenReturn("Attacker");
		when(defender.getName()).thenReturn("Defender");
		when(attacker.getLeader()).thenReturn("Alice");
		when(defender.getLeader()).thenReturn("Carol");
	}

	@Test
	void isSignupOpen_falseDuringRaidCallHour() {
		War war = battleDayWar();

		assertFalse(CampaignWarbandSignupService.isSignupOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 19)));
	}

	@Test
	void isSignupOpen_falseOffBattleDay() {
		War war = battleDayWar();
		war.setBattleDay(BATTLE_DAY.plusDays(1));

		assertFalse(CampaignWarbandSignupService.isSignupOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 20)));
	}

	@Test
	void isSignupOpen_falseBeforeSignupOnBattleDay() {
		War war = battleDayWar();

		assertFalse(CampaignWarbandSignupService.isSignupOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 10)));
	}

	@Test
	void isSignupOpen_trueAtSignupHour() {
		War war = battleDayWar();

		assertTrue(CampaignWarbandSignupService.isSignupOpen(
				war, BattleWindowService.atScheduleHour(BATTLE_DAY, 20)));
	}


	@Test
	void signupMember_blockedDuringRaidCall() {
		War war = battleDayWar();
		Warband shell = createShellOnBattle(war);
		UUID aliceId = UUID.randomUUID();
		Instant raidCall = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			mockBukkitPlayerLookup(bukkit);
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			assertEquals(CampaignWarbandSignupService.SIGNUP_BLOCKED_DURING_RAID,
					CampaignWarbandSignupService.signupMember(
							aliceId, "Alice", shell, attacker, null, raidCall));
			assertFalse(shell.hasMember(aliceId));
		}
	}

	@Test
	void signupMember_allowedAfterRaidCall() {
		War war = battleDayWar();
		Warband shell = createShellOnBattle(war);
		UUID aliceId = UUID.randomUUID();
		Instant signupHour = BattleWindowService.atScheduleHour(BATTLE_DAY, 20);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			mockBukkitPlayerLookup(bukkit);
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			assertNull(CampaignWarbandSignupService.signupMember(
					aliceId, "Alice", shell, attacker, null, signupHour));
			assertTrue(shell.hasMember(aliceId));
		}
	}

	@Test
	void signupMember_raidWarbandNotBlocked() {
		War war = battleDayWar();
		CampaignRaid raid = new CampaignRaid();
		raid.setId("harbor_raid");
		raid.setDisplayName("Harbor Raid");
		raid.setWarId(1);
		raid.setState(CampaignRaidState.MUSTER);
		war.setActiveCampaignRaid(raid);
		Warband raidShell = Warband.createRaidShell(
				"harbor_raid_attacker",
				war.getAttackers(),
				BattleTemplate.ATTACKER_SIDE);
		addWarbandToBattle(war, raidShell);
		WarbandManager.addWarband(raidShell);
		UUID aliceId = UUID.randomUUID();
		Instant raidCall = BattleWindowService.atScheduleHour(BATTLE_DAY, 19);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			mockBukkitPlayerLookup(bukkit);
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			wars.when(WarManager::getActive).thenReturn(List.of(war));
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			assertNull(CampaignWarbandSignupService.signupMember(
					aliceId, "Alice", raidShell, attacker, null, raidCall));
			assertTrue(raidShell.hasMember(aliceId));
		}
	}

	private War battleDayWar() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		war.setBattleDay(BATTLE_DAY);
		return war;
	}

	private Warband createShellOnBattle(War war) {
		Warband shell = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
		addWarbandToBattle(war, shell);
		WarbandManager.addWarband(shell);
		return shell;
	}

	private void addWarbandToBattle(War war, Warband shell) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class))).thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(BarColor.class),
					org.mockito.ArgumentMatchers.any(BarStyle.class),
					org.mockito.ArgumentMatchers.any())).thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1");
			battle.setWarId(1);
			battle.setProvinceId(PROVINCE_ID);
			battle.setLocked(false);
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).addBand(shell);
			BattleManager.addBattle(battle);
		}
	}

	private void mockBukkitPlayerLookup(MockedStatic<Bukkit> bukkit) {
		bukkit.when(() -> Bukkit.getPlayer(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(null);
	}
}
