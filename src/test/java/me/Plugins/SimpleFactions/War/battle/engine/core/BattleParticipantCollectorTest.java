package me.Plugins.SimpleFactions.War.battle.engine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.warband.Warband;

class BattleParticipantCollectorTest {
	@Test
	void collect_twoSides_allRealMembers() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			UUID attackerLeader = UUID.randomUUID();
			UUID attackerMember = UUID.randomUUID();
			UUID defenderLeader = UUID.randomUUID();
			Warband attackers = Warband.createWithMemberIds("alpha", attackerLeader, false, attackerMember);
			Warband defenders = Warband.createWithMemberIds("bravo", defenderLeader, false);
			battle.getSideById("attacker").addBand(attackers);
			battle.getSideById("defender").addBand(defenders);

			Set<UUID> ids = BattleParticipantCollector.collect(battle);

			assertEquals(3, ids.size());
			assertTrue(ids.contains(attackerLeader));
			assertTrue(ids.contains(attackerMember));
			assertTrue(ids.contains(defenderLeader));
		}
	}

	@Test
	void collect_excludesDummyMembers() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			UUID leader = UUID.randomUUID();
			UUID realMember = UUID.randomUUID();
			UUID dummy = UUID.randomUUID();
			Warband warband = Warband.createWithMemberIds("alpha", leader, false, realMember);
			warband.addDummyMembers(List.of(dummy), Map.of(dummy, "Phantom"));
			battle.getSideById("attacker").addBand(warband);

			Set<UUID> ids = BattleParticipantCollector.collect(battle);

			assertEquals(2, ids.size());
			assertTrue(ids.contains(leader));
			assertTrue(ids.contains(realMember));
		}
	}

	@Test
	void collect_dedupesAcrossWarbands() {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<org.bukkit.Bukkit> bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> org.bukkit.Bukkit.createBossBar(
					Mockito.anyString(), Mockito.any(BarColor.class), Mockito.any(BarStyle.class), Mockito.any()))
					.thenReturn(bossBar);

			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "test");
			UUID shared = UUID.randomUUID();
			Warband first = Warband.createWithMemberIds("alpha", shared, false);
			Warband second = Warband.createWithMemberIds("bravo", UUID.randomUUID(), false, shared);
			battle.getSideById("attacker").addBand(first);
			battle.getSideById("attacker").addBand(second);

			Set<UUID> ids = BattleParticipantCollector.collect(battle);

			assertEquals(2, ids.size());
			assertTrue(ids.contains(shared));
		}
	}
}
