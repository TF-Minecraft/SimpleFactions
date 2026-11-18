package net.tfminecraft.simplefactions.loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.simplefactions.Cache;
import net.tfminecraft.simplefactions.war.battle.enums.BattleLootMode;

class ConfigLoaderBattleLootTest {
	private static final String SCHEDULE = """
			war:
			  battle_schedule:
			    vote_close_hour: 16
			    raid_window_start_hour: 19
			    raid_window_end_hour: 20
			    window_start_hour: 21
			    window_end_hour: 24
			    defender_choice_deadline_hour: 12
			""";

	private Path tempDir;

	@BeforeEach
	void setUp() throws IOException {
		tempDir = Files.createTempDirectory("sf-config-");
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
	void loadWar_battleLootDefaults() throws IOException {
		new ConfigLoader().loadWar(writeConfig(SCHEDULE).toFile());

		assertEquals(BattleLootMode.ITEM, Cache.battleLootMode);
		assertTrue(Cache.battleLootCommands.isEmpty());
		assertEquals("m.materials.ignitium", Cache.battleLootItemPath);
		assertEquals(4, Cache.battleLootItemAmount);
	}

	@Test
	void loadWar_battleLootCommandMode() throws IOException {
		Path file = writeConfig(SCHEDULE + """
				  battle_loot:
				    mode: COMMAND
				    commands:
				      - "crates key give %player% battle_key 1"
				      - "broadcast %player% fought"
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(BattleLootMode.COMMAND, Cache.battleLootMode);
		assertEquals(
				List.of("crates key give %player% battle_key 1", "broadcast %player% fought"),
				Cache.battleLootCommands);
	}

	@Test
	void loadWar_battleLootItemMode() throws IOException {
		Path file = writeConfig(SCHEDULE + """
				  battle_loot:
				    mode: item
				    item: m.currency.pouch_of_coins
				    item_amount: 3
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(BattleLootMode.ITEM, Cache.battleLootMode);
		assertEquals("m.currency.pouch_of_coins", Cache.battleLootItemPath);
		assertEquals(3, Cache.battleLootItemAmount);
	}

	@Test
	void loadWar_unknownModeFallsBackToCommand() throws IOException {
		Path file = writeConfig(SCHEDULE + """
				  battle_loot:
				    mode: nonsense
				    item_amount: 0
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(BattleLootMode.COMMAND, Cache.battleLootMode);
		assertEquals(1, Cache.battleLootItemAmount);
	}

	@Test
	void loadWar_battleLootTableMode() throws IOException {
		Path file = writeConfig(SCHEDULE + """
				  battle_loot:
				    mode: table
				    minutes_per_roll: 10
				    max_rolls: 12
				    active_table: act_1
				    coins:
				      - {item: m.currency.gold_coin, value: 1}
				    tables:
				      act_1:
				        - {item: m.materials.ignitium, amount: 3}
				        - {item: m.materials.raw_tin, percent: 8}
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(BattleLootMode.TABLE, Cache.battleLootMode);
		assertEquals("act_1", Cache.battleLootTable.getActiveTable());
		assertEquals(2, Cache.battleLootTable.getEntries().size());
		assertEquals(1, Cache.battleLootTable.getCoins().size());
		assertEquals(6, Cache.battleLootTable.rollsFor(3600L));
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
