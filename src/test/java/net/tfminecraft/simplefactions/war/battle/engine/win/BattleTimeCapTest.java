package net.tfminecraft.simplefactions.war.battle.engine.win;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.engine.core.Battle;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleEndSupport;
import net.tfminecraft.simplefactions.war.battle.engine.core.BattleFactory;
import net.tfminecraft.simplefactions.war.battle.enums.BattleEndReason;
import net.tfminecraft.simplefactions.war.battle.enums.BattleType;
import net.tfminecraft.simplefactions.war.battle.template.BattleTemplate;

class BattleTimeCapTest {
	@AfterEach
	void restoreCap() {
		Cache.battleTimeCapEnabled = false;
		Cache.battleTimeCapMinutes = 120;
	}

	@Test
	void disabled_doesNotEnd() {
		withBossBar(() -> {
			Cache.battleTimeCapEnabled = false;
			Battle battle = campaignBattle(BattleType.FIELD, "cap_off");
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(1);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(9);

			try (MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
				BattleTimeCap.check(battle, battle.getStartedAt().plusSeconds(121 * 60L));
				end.verifyNoInteractions();
			}
			assertTrue(battle.hasStarted());
		});
	}

	@Test
	void beforeDeadline_doesNotEnd() {
		withBossBar(() -> {
			Cache.battleTimeCapEnabled = true;
			Battle battle = campaignBattle(BattleType.FIELD, "cap_early");

			try (MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
				BattleTimeCap.check(battle, battle.getStartedAt().plusSeconds(119 * 60L));
				end.verifyNoInteractions();
			}
		});
	}

	@Test
	void moreLives_winsWhenCapElapses() {
		withBossBar(() -> {
			Cache.battleTimeCapEnabled = true;
			Cache.battleTimeCapMinutes = 120;
			Battle battle = campaignBattle(BattleType.SIEGE, "cap_siege");
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(2);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(7);

			try (MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
				BattleTimeCap.check(battle, battle.getStartedAt().plusSeconds(120 * 60L));
				end.verify(() -> BattleEndSupport.endBattle(
						battle, BattleTemplate.DEFENDER_SIDE, BattleEndReason.TIMER));
			}
		});
	}

	@Test
	void equalLives_isADraw() {
		withBossBar(() -> {
			Cache.battleTimeCapEnabled = true;
			Battle battle = campaignBattle(BattleType.FIELD, "cap_draw");
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(4);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(4);

			try (MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
				BattleTimeCap.check(battle, battle.getStartedAt().plusSeconds(120 * 60L));
				end.verify(() -> BattleEndSupport.endBattle(battle, null, BattleEndReason.TIMER));
			}
		});
	}

	@Test
	void raidAndStaffBattles_areNotCapped() {
		withBossBar(() -> {
			Cache.battleTimeCapEnabled = true;
			Battle raid = campaignBattle(BattleType.RAID, "cap_raid");
			Battle staff = campaignBattle(BattleType.FIELD, "cap_staff");
			staff.setWarId(null);
			Instant later = raid.getStartedAt().plusSeconds(500 * 60L);

			try (MockedStatic<BattleEndSupport> end = mockStatic(BattleEndSupport.class)) {
				BattleTimeCap.check(raid, later);
				BattleTimeCap.check(staff, later);
				end.verifyNoInteractions();
			}
		});
	}

	@Test
	void tick_endsCampaignFieldWhenCapElapses() {
		BossBar bossBar = mock(BossBar.class);
		when(bossBar.getPlayers()).thenReturn(new ArrayList<>());
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Cache.battleTimeCapEnabled = true;
			Cache.battleTimeCapMinutes = 120;
			Battle battle = campaignBattle(BattleType.FIELD, "cap_tick");
			battle.getSideById(BattleTemplate.ATTACKER_SIDE).setLives(9);
			battle.getSideById(BattleTemplate.DEFENDER_SIDE).setLives(1);
			battle.setStartedAt(Instant.now().minusSeconds(120 * 60L + 5));

			battle.tick();

			assertFalse(battle.hasStarted());
		}
	}

	private Battle campaignBattle(BattleType type, String id) {
		Battle battle = BattleFactory.createBlank(type, id);
		battle.setWarId(3);
		battle.setStarted(true);
		battle.setStartedAt(Instant.parse("2026-09-26T08:00:00Z"));
		return battle;
	}

	private void withBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);
			action.run();
		}
	}
}
