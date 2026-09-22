package me.Plugins.SimpleFactions.War.battle.campaign;


import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandSignupService;
import me.Plugins.SimpleFactions.War.battle.campaign.warband.CampaignWarbandBattleService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
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
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarDevMode;
import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleManager;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;
import me.Plugins.SimpleFactions.War.campaign.runtime.BattleWindowService;

class WarbandCampaignSignupTest {
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
		Cache.warDevmodePhantomCount = 10;

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
	void shellCreate_hasZeroMembersAndPendingLeader() {
		War war = new War(1, attacker, defender);
		Warband shell = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);

		assertEquals(0, shell.getMemberCount());
		assertTrue(shell.isPendingLeader());
		assertEquals("The Attacker Host", shell.getName());
		assertEquals(BattleTemplate.ATTACKER_SIDE, shell.getCampaignSideId());
	}

	@Test
	void firstSignup_setsLeader() {
		War war = campaignWar();
		Warband shell = createShellOnBattle(war);
		UUID aliceId = UUID.randomUUID();

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
					aliceId, "Alice", shell, attacker, null, signupInstant()));
			assertEquals(aliceId, shell.getLeaderId());
			assertTrue(shell.hasMember(aliceId));
		}
	}

	@Test
	void secondSignup_doesNotStealLeader() {
		War war = campaignWar();
		Warband shell = createShellOnBattle(war);
		UUID aliceId = UUID.randomUUID();
		UUID bobId = UUID.randomUUID();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			mockBukkitPlayerLookup(bukkit);
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			factions.when(() -> FactionManager.getByMember("Bob")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			assertNull(CampaignWarbandSignupService.signupMember(
					aliceId, "Alice", shell, attacker, null, signupInstant()));
			assertNull(CampaignWarbandSignupService.signupMember(
					bobId, "Bob", shell, attacker, null, signupInstant()));
			assertEquals(aliceId, shell.getLeaderId());
		}
	}

	@Test
	void warLeaderSignup_promotesToLeader() {
		War war = campaignWar();
		Warband shell = createShellOnBattle(war);
		UUID bobId = UUID.randomUUID();
		UUID aliceId = UUID.randomUUID();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			mockBukkitPlayerLookup(bukkit);
			factions.when(() -> FactionManager.getByMember("Bob")).thenReturn(attacker);
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			assertNull(CampaignWarbandSignupService.signupMember(
					bobId, "Bob", shell, attacker, null, signupInstant()));
			assertNull(CampaignWarbandSignupService.signupMember(
					aliceId, "Alice", shell, attacker, null, signupInstant()));
			assertEquals(aliceId, shell.getLeaderId());
		}
	}

	@Test
	void devmode_phantomsAfterFirstSignupOnly() {
		WarDevMode.setEnabled(true);
		War war = campaignWar();
		Warband shell = createShellOnBattle(war);
		UUID aliceId = UUID.randomUUID();

		assertEquals(0, shell.getDummyMemberCount());

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			mockBukkitPlayerLookup(bukkit);
			factions.when(() -> FactionManager.getByMember("Alice")).thenReturn(attacker);
			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(50);

			assertNull(CampaignWarbandSignupService.signupMember(
					aliceId, "Alice", shell, attacker, null, signupInstant()));
			assertTrue(shell.getDummyMemberCount() > 0);
		}
	}

	@Test
	void isWarSideMainLeader_matchesMainFactionLeaderName() {
		War war = campaignWar();
		Warband shell = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);

		assertTrue(CampaignWarbandBattleService.isWarSideMainLeader(war, shell, "Alice"));
	}

	private War campaignWar() {
		War war = new War(1, attacker, defender);
		war.setScheduledBattleProvinceId(PROVINCE_ID);
		war.setBattleDay(BATTLE_DAY);
		return war;
	}

	private Instant signupInstant() {
		return BattleWindowService.atScheduleHour(BATTLE_DAY, 20);
	}

	private Warband createShellOnBattle(War war) {
		Warband shell = Warband.createCampaignSideShell(war, war.getAttackers(), BattleTemplate.ATTACKER_SIDE);
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
		WarbandManager.addWarband(shell);
		return shell;
	}

	private void mockBukkitPlayerLookup(MockedStatic<Bukkit> bukkit) {
		bukkit.when(() -> Bukkit.getPlayer(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(null);
	}
}
