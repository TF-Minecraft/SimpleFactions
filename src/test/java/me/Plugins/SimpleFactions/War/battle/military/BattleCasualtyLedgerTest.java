package me.Plugins.SimpleFactions.War.battle.military;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleFactory;
import me.Plugins.SimpleFactions.War.battle.engine.core.BattleSide;
import me.Plugins.SimpleFactions.War.battle.enums.BattleType;
import me.Plugins.SimpleFactions.War.battle.template.BattleTemplate;

class BattleCasualtyLedgerTest {
	@AfterEach
	void tearDown() {
		BattleCasualtyLedger.resetForTests();
	}

	@Test
	void recordSideCasualty_incrementsAttacker() {
		withMockBossBar(() -> {
			Battle battle = campaignFieldBattle(1);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);

			BattleCasualtyLedger.recordSideCasualty(battle, attacker);

			assertEquals(1, BattleCasualtyLedger.getSideCasualties(battle).get("attacker"));
		});
	}

	@Test
	void recordSideCasualty_incrementsDefender() {
		withMockBossBar(() -> {
			Battle battle = campaignFieldBattle(2);
			BattleSide defender = battle.getSideById(BattleTemplate.DEFENDER_SIDE);

			BattleCasualtyLedger.recordSideCasualty(battle, defender);
			BattleCasualtyLedger.recordSideCasualty(battle, defender);

			assertEquals(2, BattleCasualtyLedger.getSideCasualties(battle).get("defender"));
		});
	}

	@Test
	void recordSideCasualty_skipsManualBattle() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.FIELD, "manual");
			setStarted(battle, true);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);

			BattleCasualtyLedger.recordSideCasualty(battle, attacker);

			assertTrue(BattleCasualtyLedger.getSideCasualties(battle).isEmpty());
			assertTrue(BattleCasualtyLedger.tracksCasualties(battle) == false);
		});
	}

	@Test
	void recordSideCasualty_skipsRaid() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.RAID, "raid");
			battle.setWarId(3);
			setStarted(battle, true);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);

			BattleCasualtyLedger.recordSideCasualty(battle, attacker);

			assertTrue(BattleCasualtyLedger.getSideCasualties(battle).isEmpty());
		});
	}

	@Test
	void recordSideCasualty_skipsNotStarted() {
		withMockBossBar(() -> {
			Battle battle = campaignFieldBattle(4);
			setStarted(battle, false);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);

			BattleCasualtyLedger.recordSideCasualty(battle, attacker);

			assertTrue(BattleCasualtyLedger.getSideCasualties(battle).isEmpty());
		});
	}

	@Test
	void clear_resetsTotals() {
		withMockBossBar(() -> {
			Battle battle = campaignFieldBattle(5);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleCasualtyLedger.recordSideCasualty(battle, attacker);

			BattleCasualtyLedger.clear(battle);

			assertTrue(BattleCasualtyLedger.getSideCasualties(battle).isEmpty());
		});
	}

	@Test
	void getSideCasualties_returnsCopy() {
		withMockBossBar(() -> {
			Battle battle = campaignFieldBattle(6);
			BattleSide attacker = battle.getSideById(BattleTemplate.ATTACKER_SIDE);
			BattleCasualtyLedger.recordSideCasualty(battle, attacker);

			Map<String, Integer> casualties = new HashMap<>(BattleCasualtyLedger.getSideCasualties(battle));
			casualties.put("attacker", 99);

			assertEquals(1, BattleCasualtyLedger.getSideCasualties(battle).get("attacker"));
		});
	}

	@Test
	void tracksCasualties_siegeCampaignBattle() {
		withMockBossBar(() -> {
			Battle battle = BattleFactory.createBlank(BattleType.SIEGE, "siege");
			battle.setWarId(7);
			setStarted(battle, true);

			assertTrue(BattleCasualtyLedger.tracksCasualties(battle));
		});
	}

	private static Battle campaignFieldBattle(int warId) {
		Battle battle = BattleFactory.createBlank(BattleType.FIELD, "campaign_w" + warId);
		battle.setWarId(warId);
		setStarted(battle, true);
		return battle;
	}

	private static void setStarted(Battle battle, boolean started) {
		try {
			java.lang.reflect.Field field = Battle.class.getDeclaredField("started");
			field.setAccessible(true);
			field.setBoolean(battle, started);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static void withMockBossBar(Runnable action) {
		BossBar bossBar = mock(BossBar.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
					.thenReturn(bossBar);
			bukkit.when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any()))
					.thenReturn(bossBar);
			action.run();
		}
	}
}
