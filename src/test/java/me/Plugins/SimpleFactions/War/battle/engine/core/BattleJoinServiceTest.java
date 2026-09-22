package me.Plugins.SimpleFactions.War.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.WarManager;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.military.BattlePoolService;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;
import me.Plugins.SimpleFactions.War.battle.warband.WarbandManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;

class BattleJoinServiceTest {
	private static final int PROVINCE_ID = 20;

	@BeforeEach
	void setUp() {
		BattleManager.resetForTests();
		WarbandManager.resetForTests();
		Cache.warBattleLivesPerRegiment = 5;
		Cache.warBattleMinSideLives = 1;
	}

	@Test
	void join_success() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			battle.setLocked(false);
			Warband warband = Warband.createWithMemberIds("alpha", java.util.UUID.randomUUID(), false);

			assertNull(BattleJoinService.join(warband, battle, "attacker"));
			assertEquals(1, battle.getSideById("attacker").getBands().size());
		}
	}

	@Test
	void join_failsWhenLocked() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			battle.setLocked(true);
			Warband warband = Warband.createWithMemberIds("alpha", java.util.UUID.randomUUID(), false);

			assertEquals("Battle is locked", BattleJoinService.join(warband, battle, "attacker"));
		}
	}

	@Test
	void join_failsWithoutWarband() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			battle.setLocked(false);

			assertEquals("You need to lead a warband to join a battle", BattleJoinService.join((Warband) null, battle, "attacker"));
		}
	}

	@Test
	void join_campaignPlayerRedirect() {
		Battle battle = new Battle("campaign_w1");
		battle.setWarId(1);

		assertEquals(
				"Sign up with /warband list - your faction warband is already on this battle",
				BattleJoinService.campaignPlayerJoinRedirect(battle));
		assertNull(BattleJoinService.campaignPlayerJoinRedirect(null));
	}

	@Test
	void join_pendingLeaderShell_enrollsOnBattleSide() {
		Faction attacker = mock(Faction.class);
		when(attacker.getId()).thenReturn("atk");
		when(attacker.getName()).thenReturn("Attacker");
		when(attacker.getLeader()).thenReturn("Alice");
		mockMilitary(attacker);
		War war = new War(1, attacker, mock(Faction.class));
		war.setScheduledBattleProvinceId(PROVINCE_ID);

		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class);
				MockedStatic<WarManager> wars = mockStatic(WarManager.class);
				MockedStatic<FactionManager> factions = mockStatic(FactionManager.class);
				MockedStatic<BattlePoolService> pool = mockStatic(BattlePoolService.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			wars.when(() -> WarManager.getById(1)).thenReturn(war);
			factions.when(() -> FactionManager.getByString("atk")).thenReturn(attacker);
			pool.when(() -> BattlePoolService.totalCommittedRegiments(eq(war), eq(PROVINCE_ID), eq(war.getAttackers())))
					.thenReturn(5);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w1");
			battle.setWarId(1);
			battle.setProvinceId(PROVINCE_ID);
			battle.setLocked(false);
			Warband warband = Warband.createCampaignSideShell(war, war.getAttackers(), "attacker");

			assertNull(BattleJoinService.join(warband, battle, "attacker"));
			assertEquals(1, battle.getSideById("attacker").getBands().size());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void markAsFaction(Warband warband) throws Exception {
		Field factionField = Warband.class.getDeclaredField("faction");
		factionField.setAccessible(true);
		factionField.set(warband, true);
	}

	private static void mockMilitary(Faction faction) {
		Military military = mock(Military.class);
		Regiment levy = mock(Regiment.class);
		when(military.getManpowerNoLevy(anyBoolean())).thenReturn(10);
		when(military.getRegiment("levy")).thenReturn(levy);
		when(levy.getEntries()).thenReturn(List.of());
		when(faction.getMilitary()).thenReturn(military);
	}
}
