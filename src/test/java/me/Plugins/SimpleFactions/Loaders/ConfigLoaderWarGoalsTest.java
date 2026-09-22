package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;
import me.Plugins.SimpleFactions.War.enums.WarGoalType;

class ConfigLoaderWarGoalsTest {
	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-config-war-goals-");
	}

	@AfterEach
	void tearDown() throws IOException {
		if (tempDir != null) {
			Files.walk(tempDir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> path.toFile().delete());
		}
	}

	@Test
	void loadConfig_readsInitiativeFactorAndGoalMaxBattles() throws IOException {
		Path file = writeConfig("""
				war:
				  initiative_factor: 2.0
				  goals:
				    SUBJUGATE:
				      max_battles: 5
				    de_jure_annex:
				      max_battles: 3
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(2.0, Cache.warInitiativeFactor);
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.SUBJUGATE));
		assertEquals(3, Cache.warGoalMaxBattles.get(WarGoalType.DE_JURE_ANNEX));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.TRANSFER_SUBJECT));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.WAR));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.TRIBUTARY));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.USURP));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.OPEN_MARKET));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.CHANGE_GOVERNMENT));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.PILLAGE));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.OVERTHROW));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.CHANGE_LAW));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.CHANGE_TAX));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.FORCE_PEACE));
	}

	@Test
	void loadConfig_prefersMaxBattlesPerLeg() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    SUBJUGATE:
				      max_battles_per_leg: 6
				      max_battles: 2
				    TRANSFER_SUBJECT:
				      max_battles_per_leg: 5
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.SUBJUGATE));
		assertEquals(4, Cache.warGoalMaxBattles.get(WarGoalType.TRANSFER_SUBJECT));
		assertTrue(Cache.openMarketDefenderMustNotHave.isEmpty());
		assertTrue(Cache.openMarketAttackerMustNotHave.isEmpty());
		assertEquals("", Cache.openMarketApplyDefenderLaw);
	}

	@Test
	void loadConfig_readsOpenMarketLawIdsFromEnumName() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    OPEN_MARKET:
				      defender_must_not_have:
				        - free_trade
				        - " "
				      attacker_must_not_have:
				        - isolationism
				      apply_defender_law: free_trade
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(List.of("free_trade"), Cache.openMarketDefenderMustNotHave);
		assertEquals(List.of("isolationism"), Cache.openMarketAttackerMustNotHave);
		assertEquals("free_trade", Cache.openMarketApplyDefenderLaw);
	}

	@Test
	void loadConfig_readsOpenMarketLawIdsFromJsonId() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    open_market:
				      defender_must_not_have: [free_trade]
				      attacker_must_not_have: [isolationism]
				      apply_defender_law:  free_trade
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(List.of("free_trade"), Cache.openMarketDefenderMustNotHave);
		assertEquals(List.of("isolationism"), Cache.openMarketAttackerMustNotHave);
		assertEquals("free_trade", Cache.openMarketApplyDefenderLaw);
	}

	@Test
	void loadConfig_missingOpenMarketKeys_areEmpty() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    SUBJUGATE:
				      max_battles_per_leg: 4
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertTrue(Cache.openMarketDefenderMustNotHave.isEmpty());
		assertTrue(Cache.openMarketAttackerMustNotHave.isEmpty());
		assertEquals("", Cache.openMarketApplyDefenderLaw);
		assertEquals(3, Cache.pillageRangeProvinces);
		assertEquals(10, Cache.pillageLootDays);
		assertEquals(-100, Cache.pillageTradeHitPercent);
		assertEquals(10, Cache.pillageTradeHitDays);
	}

	@Test
	void loadConfig_readsPillageMaxBattlesPerLeg() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    PILLAGE:
				      range_provinces: 3
				      max_battles_per_leg: 1
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(1, Cache.warGoalMaxBattles.get(WarGoalType.PILLAGE));
		assertEquals(3, Cache.pillageRangeProvinces);
	}

	@Test
	void loadConfig_readsPillageRangeFromEnumName() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    PILLAGE:
				      range_provinces: 5
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(5, Cache.pillageRangeProvinces);
	}

	@Test
	void loadConfig_readsPillageRangeFromJsonId() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    pillage:
				      range_provinces: 2
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(2, Cache.pillageRangeProvinces);
	}

	@Test
	void loadConfig_negativePillageRange_defaultsToThree() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    PILLAGE:
				      range_provinces: -1
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(3, Cache.pillageRangeProvinces);
	}

	@Test
	void loadConfig_readsPillageApplyKeysFromEnumName() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    PILLAGE:
				      loot_days: 7
				      trade_hit_percent: -50
				      trade_hit_days: 5
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(7, Cache.pillageLootDays);
		assertEquals(-50, Cache.pillageTradeHitPercent);
		assertEquals(5, Cache.pillageTradeHitDays);
	}

	@Test
	void loadConfig_readsPillageApplyKeysFromJsonId() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    pillage:
				      loot_days: 4
				      trade_hit_percent: -80
				      trade_hit_days: 8
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(4, Cache.pillageLootDays);
		assertEquals(-80, Cache.pillageTradeHitPercent);
		assertEquals(8, Cache.pillageTradeHitDays);
	}

	@Test
	void loadConfig_negativePillageLootAndHitDays_defaultToTen() throws IOException {
		Path file = writeConfig("""
				war:
				  goals:
				    PILLAGE:
				      loot_days: -1
				      trade_hit_days: -3
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(10, Cache.pillageLootDays);
		assertEquals(10, Cache.pillageTradeHitDays);
		assertEquals(-100, Cache.pillageTradeHitPercent);
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
