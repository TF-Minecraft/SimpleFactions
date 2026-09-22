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
import me.Plugins.SimpleFactions.War.battle.enums.BattleLootMode;

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

		assertEquals(BattleLootMode.COMMAND, Cache.battleLootMode);
		assertTrue(Cache.battleLootCommands.isEmpty());
		assertEquals(1, Cache.battleLootItemAmount);
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

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
