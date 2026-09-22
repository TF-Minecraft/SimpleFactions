package me.Plugins.SimpleFactions.Loaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.Plugins.SimpleFactions.Cache;

class ConfigLoaderBattleScheduleTest {
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
	void loadConfig_validBattleScheduleHours() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(21, Cache.warBattleWindowStartHour);
		assertEquals(24, Cache.warBattleWindowEndHour);
		assertEquals(19, Cache.warRaidWindowStartHour);
		assertEquals(20, Cache.warRaidWindowEndHour);
		assertEquals(16, Cache.warVoteCloseHour);
		assertEquals(12, Cache.warDefenderChoiceDeadlineHour);
		assertEquals(4, Cache.warBattleVotingMinPlayers);
		assertEquals(1, Cache.warBattleVotingDevMinPlayers);
		assertTrue(Cache.warBattleVotingDevMinPlayersEnabled);
	}

	@Test
	void loadConfig_raidWindowDefaultsWhenOmitted() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(19, Cache.warRaidWindowStartHour);
		assertEquals(20, Cache.warRaidWindowEndHour);
	}

	@Test
	void loadConfig_devMinPlayersAbsentUsesMinPlayersThreshold() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				""");

		new ConfigLoader().loadWar(file.toFile());

		assertEquals(4, Cache.warBattleVotingMinPlayers);
		assertEquals(4, Cache.warBattleVotingDevMinPlayers);
		assertFalse(Cache.warBattleVotingDevMinPlayersEnabled);
	}

	@Test
	void loadConfig_invalidVoteCloseAfterRaidStartThrows() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 19
				    raid_window_start_hour: 19
				    raid_window_end_hour: 20
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(file.toFile()));
	}

	@Test
	void loadConfig_invalidRaidOverlapsBattleWindowThrows() throws IOException {
		Path file = writeConfig("""
				war:
				  battle_schedule:
				    vote_close_hour: 16
				    raid_window_start_hour: 20
				    raid_window_end_hour: 21
				    window_start_hour: 21
				    window_end_hour: 24
				    defender_choice_deadline_hour: 12
				  battle_voting:
				    min_players: 4
				    dev_min_players: 1
				""");

		assertThrows(IllegalStateException.class, () -> new ConfigLoader().loadWar(file.toFile()));
	}

	private Path writeConfig(String yaml) throws IOException {
		Path file = tempDir.resolve("config.yml");
		Files.writeString(file, yaml);
		return file;
	}
}
