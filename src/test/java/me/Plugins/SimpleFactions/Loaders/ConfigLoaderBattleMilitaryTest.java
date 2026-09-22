package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class ConfigLoaderBattleMilitaryTest {
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
	void loadConfig_battleMilitaryDefaults() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(5, Cache.warBattleLivesPerRegiment);
		assertEquals(1, Cache.warBattleMinSideLives);
	}

	@Test
	void loadConfig_battleMilitaryCustomValues() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_military:
				    lives_per_regiment: 7
				    min_side_lives: 3
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(7, Cache.warBattleLivesPerRegiment);
		assertEquals(3, Cache.warBattleMinSideLives);
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
